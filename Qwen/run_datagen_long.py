#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import os
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_DATAGEN_PATH = SCRIPT_DIR / "dataGen.py"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Long-run orchestrator for dataGen.py with batch retries and resume."
    )
    parser.add_argument("--total", type=int, default=1000, help="Target total records.")
    parser.add_argument("--batch-size", type=int, default=50, help="Records per batch run.")
    parser.add_argument("--out-dir", default="generated_resumes_1000", help="Final merged output directory.")
    parser.add_argument("--work-dir", default=".datagen_work", help="Temporary batch output directory.")
    parser.add_argument("--data-gen-path", default=str(DEFAULT_DATAGEN_PATH), help="Path to dataGen.py")
    parser.add_argument("--provider", choices=["dashscope", "deepseek", "custom"], default="deepseek")
    parser.add_argument("--model", default="deepseek-chat", help="LLM model for dataGen.py")
    parser.add_argument("--base-url", default=None, help="Override API base URL.")
    parser.add_argument("--api-key", default=None, help="Optional API key. Prefer environment variable.")
    parser.add_argument("--seed", type=int, default=42, help="Initial seed.")
    parser.add_argument("--max-batch-retries", type=int, default=5, help="Max retries for one batch.")
    parser.add_argument("--retry-wait-seconds", type=float, default=15.0, help="Wait before retrying failed batch.")
    parser.add_argument("--sleep-between-batches", type=float, default=2.0, help="Wait between successful batches.")
    parser.add_argument(
        "--use-llm",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Pass --use-llm to dataGen.py (default true).",
    )
    parser.add_argument("--progress-every", type=int, default=10, help="Progress interval for dataGen.py")
    parser.add_argument(
        "--passthrough",
        action="append",
        default=[],
        help="Extra argument for dataGen.py; can be repeated. Example: --passthrough=--llm-stream",
    )
    return parser.parse_args()


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def atomic_json_write(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


def load_state(state_path: Path) -> dict[str, Any]:
    if not state_path.exists():
        return {
            "version": 1,
            "created_at": now_iso(),
            "updated_at": now_iso(),
            "total": 0,
            "completed": 0,
            "next_seed": 42,
            "batches": [],
        }
    return json.loads(state_path.read_text(encoding="utf-8"))


def ensure_final_layout(out_dir: Path) -> tuple[Path, Path, Path]:
    raw_dir = out_dir / "raw"
    files_dir = out_dir / "files"
    out_dir.mkdir(parents=True, exist_ok=True)
    raw_dir.mkdir(parents=True, exist_ok=True)
    files_dir.mkdir(parents=True, exist_ok=True)
    return raw_dir, files_dir, out_dir / "manifest.csv"


def write_manifest_header_if_needed(path: Path) -> None:
    if path.exists() and path.stat().st_size > 0:
        return
    with path.open("w", encoding="utf-8", newline="") as fp:
        writer = csv.DictWriter(
            fp,
            fieldnames=[
                "record_id",
                "file_path",
                "format",
                "scenario",
                "source_record_id",
                "file_hash",
                "name",
                "target_role",
                "domain",
                "secondary_domain",
                "years_experience",
                "max_similarity",
                "anomaly_type",
                "jd_level",
            ],
        )
        writer.writeheader()


def run_batch(cmd: list[str], batch_log_path: Path) -> tuple[int, str]:
    batch_log_path.parent.mkdir(parents=True, exist_ok=True)
    with batch_log_path.open("a", encoding="utf-8") as log_fp:
        log_fp.write(f"\n[{now_iso()}] CMD: {' '.join(cmd)}\n")
        log_fp.flush()
        proc = subprocess.run(cmd, stdout=log_fp, stderr=subprocess.STDOUT, text=True)
    return proc.returncode, batch_log_path.read_text(encoding="utf-8")[-2000:]


def build_batch_command(
    args: argparse.Namespace,
    batch_count: int,
    batch_seed: int,
    batch_out_dir: Path,
) -> list[str]:
    cmd = [
        sys.executable,
        str(Path(args.data_gen_path).resolve()),
        "--count",
        str(batch_count),
        "--out-dir",
        str(batch_out_dir.resolve()),
        "--provider",
        args.provider,
        "--seed",
        str(batch_seed),
        "--progress-every",
        str(max(1, args.progress_every)),
    ]
    if args.use_llm:
        cmd.append("--use-llm")
    if args.model:
        cmd.extend(["--model", args.model])
    if args.base_url:
        cmd.extend(["--base-url", args.base_url])
    if args.api_key:
        cmd.extend(["--api-key", args.api_key])
    for token in args.passthrough:
        cmd.append(token)
    return cmd


def read_batch_manifest(batch_manifest: Path) -> list[dict[str, str]]:
    with batch_manifest.open("r", encoding="utf-8", newline="") as fp:
        return list(csv.DictReader(fp))


def read_batch_canonical(batch_canonical: Path) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    with batch_canonical.open("r", encoding="utf-8") as fp:
        for line in fp:
            line = line.strip()
            if not line:
                continue
            items.append(json.loads(line))
    return items


def merge_batch(
    batch_out_dir: Path,
    final_out_dir: Path,
    start_index: int,
) -> int:
    final_raw, final_files, final_manifest = ensure_final_layout(final_out_dir)
    write_manifest_header_if_needed(final_manifest)
    final_canonical = final_raw / "canonical.jsonl"

    batch_manifest = batch_out_dir / "manifest.csv"
    batch_canonical = batch_out_dir / "raw" / "canonical.jsonl"
    if not batch_manifest.exists() or not batch_canonical.exists():
        raise RuntimeError(f"Batch output is incomplete: {batch_out_dir}")

    manifest_rows = read_batch_manifest(batch_manifest)
    canonical_rows = read_batch_canonical(batch_canonical)
    if len(manifest_rows) != len(canonical_rows):
        raise RuntimeError(
            f"Batch manifest/canonical size mismatch: {len(manifest_rows)} vs {len(canonical_rows)}"
        )

    with final_manifest.open("a", encoding="utf-8", newline="") as manifest_fp, final_canonical.open(
        "a", encoding="utf-8"
    ) as canonical_fp:
        writer = csv.DictWriter(
            manifest_fp,
            fieldnames=[
                "record_id",
                "file_path",
                "format",
                "scenario",
                "source_record_id",
                "file_hash",
                "name",
                "target_role",
                "domain",
                "secondary_domain",
                "years_experience",
                "max_similarity",
                "anomaly_type",
                "jd_level",
            ],
        )

        for i, (manifest_row, canonical_row) in enumerate(zip(manifest_rows, canonical_rows)):
            idx = start_index + i
            new_record_id = f"resume_{idx:04d}"
            fmt = manifest_row["format"]
            old_path = Path(manifest_row["file_path"])
            new_path = final_files / f"{new_record_id}.{fmt}"
            new_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(old_path, new_path)

            manifest_row["record_id"] = new_record_id
            manifest_row["file_path"] = str(new_path.resolve())
            writer.writerow(manifest_row)

            canonical_row["record_id"] = new_record_id
            canonical_fp.write(json.dumps(canonical_row, ensure_ascii=False) + "\n")

    return len(manifest_rows)


def validate_llm_credentials(args: argparse.Namespace) -> None:
    if not args.use_llm:
        return
    if args.api_key:
        return
    if args.provider == "deepseek" and not os.getenv("DEEPSEEK_API_KEY"):
        raise ValueError("Missing DeepSeek API key. Set DEEPSEEK_API_KEY or pass --api-key.")
    if args.provider == "dashscope" and not os.getenv("DASHSCOPE_API_KEY"):
        raise ValueError("Missing DashScope API key. Set DASHSCOPE_API_KEY or pass --api-key.")
    if args.provider == "custom" and not os.getenv("OPENAI_API_KEY"):
        raise ValueError("Missing OPENAI_API_KEY or pass --api-key.")


def main() -> None:
    args = parse_args()
    if args.total <= 0:
        raise ValueError("--total must be > 0")
    if args.batch_size <= 0:
        raise ValueError("--batch-size must be > 0")

    validate_llm_credentials(args)

    final_out_dir = Path(args.out_dir).resolve()
    work_dir = Path(args.work_dir).resolve()
    state_path = final_out_dir / "run_state.json"
    state = load_state(state_path)

    old_total = int(state.get("total", 0) or 0)
    completed = int(state.get("completed", 0) or 0)
    if args.total < completed:
        raise RuntimeError(
            f"Requested total={args.total} is smaller than completed={completed}. "
            "Use a total >= completed."
        )
    if old_total and args.total < old_total and args.total > completed:
        raise RuntimeError(
            f"Requested total={args.total} is smaller than previous total={old_total}. "
            "Use a total >= previous total or equal to completed."
        )
    state["total"] = args.total
    state["next_seed"] = int(state.get("next_seed", args.seed))
    state["completed"] = int(state.get("completed", 0))
    state["updated_at"] = now_iso()
    atomic_json_write(state_path, state)

    print(f"[start] total={args.total} completed={state['completed']} batch_size={args.batch_size}", flush=True)
    print(f"[paths] final_out={final_out_dir} work_dir={work_dir}", flush=True)

    while state["completed"] < args.total:
        remain = args.total - state["completed"]
        batch_count = min(args.batch_size, remain)
        batch_no = len(state.get("batches", [])) + 1
        batch_seed = int(state["next_seed"])
        batch_out_dir = work_dir / f"batch_{batch_no:04d}"
        batch_log = work_dir / "logs" / f"batch_{batch_no:04d}.log"

        cmd = build_batch_command(args, batch_count, batch_seed, batch_out_dir)
        print(
            f"[batch-start] no={batch_no} count={batch_count} seed={batch_seed} target={state['completed'] + batch_count}/{args.total}",
            flush=True,
        )

        success = False
        last_tail = ""
        for attempt in range(1, args.max_batch_retries + 1):
            rc, tail = run_batch(cmd, batch_log)
            last_tail = tail
            if rc == 0:
                success = True
                break
            print(f"[batch-retry] no={batch_no} attempt={attempt}/{args.max_batch_retries} rc={rc}", flush=True)
            if attempt < args.max_batch_retries:
                time.sleep(max(0.0, args.retry_wait_seconds))

        if not success:
            raise RuntimeError(
                f"Batch {batch_no} failed after retries. Check log: {batch_log}\nLast output:\n{last_tail}"
            )

        merged = merge_batch(batch_out_dir, final_out_dir, state["completed"])
        state["completed"] += merged
        state["next_seed"] = batch_seed + 1
        state.setdefault("batches", []).append(
            {
                "batch_no": batch_no,
                "count": merged,
                "seed": batch_seed,
                "batch_out_dir": str(batch_out_dir),
                "log_path": str(batch_log),
                "finished_at": now_iso(),
            }
        )
        state["updated_at"] = now_iso()
        atomic_json_write(state_path, state)
        print(f"[batch-done] no={batch_no} merged={merged} completed={state['completed']}/{args.total}", flush=True)

        if state["completed"] < args.total:
            time.sleep(max(0.0, args.sleep_between_batches))

    summary = {
        "total": state["completed"],
        "provider": args.provider,
        "model": args.model,
        "final_out_dir": str(final_out_dir),
        "manifest": str((final_out_dir / "manifest.csv").resolve()),
        "canonical_jsonl": str((final_out_dir / "raw" / "canonical.jsonl").resolve()),
        "state": str(state_path.resolve()),
        "finished_at": now_iso(),
    }
    summary_path = final_out_dir / "run_summary.json"
    atomic_json_write(summary_path, summary)
    print("[done] long-run generation finished", flush=True)
    print(json.dumps(summary, ensure_ascii=False, indent=2), flush=True)


if __name__ == "__main__":
    main()
