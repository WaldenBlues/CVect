#!/usr/bin/env python3
"""
Resume dataset generator for CVect integration tests.

Key goals:
- Reuse real resume layout from /static/My.pdf as the structural template
- Generate diverse tech directions (frontend/backend/devops/ai-infra/...)
- Control similarity to avoid overly repetitive resumes
"""

from __future__ import annotations

import argparse
import copy
import csv
import hashlib
import json
import os
import random
import re
import shutil
import textwrap
import time
from concurrent.futures import ThreadPoolExecutor
from itertools import repeat
from pathlib import Path
from typing import Any

try:
    from docx import Document
except ImportError:  # pragma: no cover - runtime dependency
    Document = None

try:
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfgen import canvas
except ImportError:  # pragma: no cover - runtime dependency
    A4 = None
    canvas = None

try:
    from pypdf import PdfReader
except ImportError:  # pragma: no cover - runtime dependency
    PdfReader = None


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_TEMPLATE_PDF = REPO_ROOT / "backend/cvect/src/main/resources/static/My.pdf"

SUPPORTED_FORMATS = ("pdf", "docx", "txt", "md")
DEFAULT_FORMAT_MIX = {"pdf": 45, "docx": 30, "txt": 15, "md": 10}
JD_LEVELS = ("L0", "L1", "L2", "L3", "L4")
DEFAULT_LEVEL_MIX = {"L0": 0.35, "L1": 0.25, "L2": 0.20, "L3": 0.15, "L4": 0.05}
LEVEL_YEAR_RANGES = {
    "L0": (1, 1),   # internship
    "L1": (1, 3),   # junior
    "L2": (3, 6),   # mid
    "L3": (6, 10),  # senior
    "L4": (9, 14),  # tech lead/staff
}
DOMAIN_ROLE_PREFIX = {
    "frontend": "前端",
    "backend": "后端",
    "devops": "DevOps",
    "ai_infra": "AI Infra",
    "data_platform": "数据平台",
    "mobile": "移动端",
    "security": "安全",
}
DOMAIN_LABELS_ZH = {
    "frontend": "前端",
    "backend": "后端",
    "devops": "DevOps",
    "ai_infra": "AI Infra",
    "data_platform": "数据平台",
    "mobile": "移动端",
    "security": "安全",
}
DEFAULT_SECTION_ORDER = ("profile", "skills", "experience", "projects", "education", "certifications")

SECTION_KEYWORDS = {
    "profile": ["个人信息", "基本信息", "profile", "summary", "简介"],
    "skills": ["技能", "技术栈", "tech stack", "skills"],
    "experience": ["工作经历", "经历", "experience", "employment"],
    "projects": ["项目", "project", "项目经历"],
    "education": ["教育", "学历", "education"],
    "certifications": ["证书", "认证", "certification", "awards", "荣誉"],
}

DEFAULT_SECTION_LABELS = {
    "profile": "个人信息",
    "skills": "技术栈",
    "experience": "工作经历",
    "projects": "项目经历",
    "education": "教育背景",
    "certifications": "证书/荣誉",
}

DOMAIN_PROFILES: dict[str, dict[str, list[str]]] = {
    "frontend": {
        "roles": ["前端工程师", "Web 工程师", "高级前端工程师"],
        "skills": ["TypeScript", "Vue", "React", "Next.js", "Vite", "Pinia", "Webpack", "Cypress", "Playwright"],
        "topics": ["BFF dashboard", "Candidate portal", "Real-time HR cockpit", "Resume review workspace"],
    },
    "backend": {
        "roles": ["后端工程师", "Java 工程师", "后端平台工程师"],
        "skills": ["Java", "Spring Boot", "MySQL", "PostgreSQL", "Redis", "Kafka", "Elasticsearch", "gRPC", "JUnit"],
        "topics": ["Resume parsing pipeline", "Candidate ranking service", "Upload workflow engine", "Search API"],
    },
    "devops": {
        "roles": ["DevOps 工程师", "SRE", "平台可靠性工程师"],
        "skills": ["Kubernetes", "Helm", "Terraform", "Ansible", "Prometheus", "Grafana", "ArgoCD", "Istio", "Nginx"],
        "topics": ["Progressive delivery platform", "Observability stack", "Cluster autoscaling", "Disaster recovery drills"],
    },
    "ai_infra": {
        "roles": ["AI Infra 工程师", "LLM 平台工程师", "MLOps 工程师"],
        "skills": ["PyTorch", "Transformers", "Ray", "vLLM", "FastAPI", "Triton", "MLflow", "ONNX Runtime", "Faiss"],
        "topics": ["Embedding service", "Model serving gateway", "Feature store integration", "Prompt evaluation pipeline"],
    },
    "data_platform": {
        "roles": ["数据工程师", "数据平台工程师", "流式计算工程师"],
        "skills": ["Spark", "Flink", "Airflow", "ClickHouse", "Hive", "Kafka", "dbt", "Trino", "Iceberg"],
        "topics": ["Candidate analytics lakehouse", "Real-time ETL", "Metrics warehouse", "Batch scheduling platform"],
    },
    "mobile": {
        "roles": ["移动端工程师", "Android 工程师", "iOS 工程师"],
        "skills": ["Kotlin", "Swift", "Flutter", "React Native", "Jetpack Compose", "SwiftUI", "Firebase", "Realm", "GraphQL"],
        "topics": ["Interview scheduling app", "Recruiter mobile console", "Push notification center", "Offline profile reader"],
    },
    "security": {
        "roles": ["安全工程师", "应用安全工程师", "云安全工程师"],
        "skills": ["OWASP", "Burp Suite", "SAST", "DAST", "SIEM", "WAF", "Vault", "IAM", "Falco"],
        "topics": ["Secure upload gateway", "Identity and access governance", "Threat detection rules", "Security baseline automation"],
    },
}

GENERIC_SKILLS = [
    "Docker",
    "Linux",
    "GitHub Actions",
    "SQL",
    "OpenTelemetry",
    "REST API",
    "Microservices",
    "Unit Testing",
    "CI/CD",
    "Message Queue",
]

FIRST_NAMES = [
    "Alex", "Taylor", "Jordan", "Morgan", "Casey", "Riley", "Avery", "Eden", "Kai", "Noah",
    "Yuki", "Leo", "Mila", "Iris", "Luna", "Mason", "Ethan", "Sofia", "Nora", "Aaron",
]
LAST_NAMES = [
    "Chen", "Wang", "Li", "Zhao", "Sun", "Liu", "Gao", "Xu", "Yang", "Hu",
    "Lin", "Tang", "Shen", "Zheng", "Deng", "Qian", "Gu", "He", "Lu", "Fan",
]
SCHOOL_POOL = [
    "浙江大学",
    "复旦大学",
    "上海交通大学",
    "南京大学",
    "哈尔滨工业大学",
    "中国科学技术大学",
    "同济大学",
    "北京航空航天大学",
]
CERT_POOL = [
    "AWS SAA",
    "CKA",
    "PMP",
    "Oracle OCP",
    "Alibaba ACA",
    "Google Professional Cloud Architect",
    "RHCE",
    "Azure Administrator Associate",
]
COMPANY_PREFIX = ["Byte", "Cloud", "Data", "Neo", "Meta", "Intra", "Nova", "Zen", "Hyper", "Graph"]
COMPANY_SUFFIX = ["Labs", "Stack", "Works", "Tech", "Systems", "Dynamics", "Bridge", "Pulse", "Forge", "Matrix"]
CHINESE_CHAR_RE = re.compile(r"[\u4e00-\u9fff]")
BOILERPLATE_PHRASES = [
    "围绕稳定性目标持续优化",
    "推进跨团队交付协同",
    "从方案设计到上线监控全流程负责",
    "负责服务设计、可观测性建设与生产可用性优化",
    "主导核心业务链路的架构设计与 API 落地",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate mixed-format resume dataset.")
    parser.add_argument(
        "--provider",
        choices=["dashscope", "deepseek", "custom"],
        default="dashscope",
        help="LLM provider preset. custom means pass your own --base-url/--model/--api-key.",
    )
    parser.add_argument(
        "--safe-test-one-pdf",
        action="store_true",
        help="Safe smoke mode: force generation to exactly 1 PDF with no duplicate/noise injection.",
    )
    parser.add_argument("--count", type=int, default=1000, help="Total resume files to generate.")
    parser.add_argument("--out-dir", default="generated_resumes", help="Output directory.")
    parser.add_argument(
        "--format-mix",
        default="pdf=45,docx=30,txt=15,md=10",
        help="Format distribution, e.g. 'pdf=45,docx=30,txt=15,md=10'.",
    )
    parser.add_argument(
        "--level-mix",
        default="L0=0.35,L1=0.25,L2=0.20,L3=0.15,L4=0.05",
        help="JD level distribution, e.g. 'L0=0.35,L1=0.25,L2=0.20,L3=0.15,L4=0.05'.",
    )
    parser.add_argument("--seed", type=int, default=42, help="Random seed.")
    parser.add_argument(
        "--template-pdf",
        default=str(DEFAULT_TEMPLATE_PDF),
        help="Template PDF path. The script extracts section style/order from this file.",
    )
    parser.add_argument("--duplicate-ratio", type=float, default=0.0, help="Exact duplicate ratio.")
    parser.add_argument("--near-duplicate-ratio", type=float, default=0.0, help="Near duplicate ratio.")
    parser.add_argument("--anomaly-ratio", type=float, default=0.07, help="Anomaly ratio.")
    parser.add_argument("--max-similarity", type=float, default=0.72, help="Maximum allowed Jaccard similarity for unique resumes.")
    parser.add_argument("--max-sim-retries", type=int, default=30, help="Retry times when generated resume is too similar.")
    parser.add_argument("--use-llm", action="store_true", help="Use Qwen API for canonical record generation.")
    parser.add_argument("--api-key", default=None, help="Provider API key.")
    parser.add_argument(
        "--base-url",
        default=None,
        help="OpenAI-compatible endpoint.",
    )
    parser.add_argument("--model", default=None, help="Model name for generation.")
    parser.add_argument("--sleep-seconds", type=float, default=0.15, help="Delay between API calls.")
    parser.add_argument("--max-retries", type=int, default=5, help="Retry count for LLM/synthetic generation.")
    parser.add_argument("--llm-think-steps", type=int, default=12, help="Requested internal reasoning steps for LLM prompt.")
    parser.add_argument(
        "--reasoning-effort",
        choices=["minimal", "low", "medium", "high"],
        default="high",
        help="Reasoning effort hint for supported models.",
    )
    parser.add_argument("--llm-temperature", type=float, default=0.9, help="Sampling temperature for LLM generation.")
    parser.add_argument("--llm-top-p", type=float, default=0.95, help="Top-p for LLM generation.")
    parser.add_argument("--llm-presence-penalty", type=float, default=0.6, help="Presence penalty for LLM generation.")
    parser.add_argument("--llm-frequency-penalty", type=float, default=0.5, help="Frequency penalty for LLM generation.")
    parser.add_argument("--llm-max-completion-tokens", type=int, default=3200, help="Max completion tokens for LLM generation.")
    parser.add_argument("--quality-threshold", type=float, default=0.74, help="Minimum narrative uniqueness ratio (0~1).")
    parser.add_argument("--progress-every", type=int, default=10, help="Print progress every N records.")
    parser.add_argument(
        "--resource-profile",
        choices=["interactive", "balanced", "throughput"],
        default="interactive",
        help="Resource profile. interactive minimizes impact on active development sessions.",
    )
    parser.add_argument(
        "--export-workers",
        type=int,
        default=None,
        help="Parallel workers for exporting files (I/O + PDF/DOCX rendering).",
    )
    parser.add_argument(
        "--yield-seconds",
        type=float,
        default=None,
        help="Optional tiny sleep between records to reduce CPU/IO bursts.",
    )
    parser.add_argument(
        "--cpu-nice",
        type=int,
        default=None,
        help="Optional os.nice increment (Linux). Positive values reduce process priority.",
    )
    parser.add_argument(
        "--llm-stream",
        action=argparse.BooleanOptionalAction,
        default=False,
        help="Enable streaming read from LLM endpoint when available (default off for output stability).",
    )
    parser.add_argument("--stream-chunk-interval", type=int, default=20, help="Print every N streamed chunks.")
    parser.add_argument(
        "--llm-fallback-synthetic",
        action=argparse.BooleanOptionalAction,
        default=False,
        help="When --use-llm, allow fallback to synthetic records after repeated LLM failures.",
    )
    return parser.parse_args()


def apply_runtime_defaults(args: argparse.Namespace) -> None:
    if args.safe_test_one_pdf:
        args.count = 1
        args.format_mix = "pdf=1"
        args.duplicate_ratio = 0.0
        args.near_duplicate_ratio = 0.0
        args.anomaly_ratio = 0.0

    if args.provider == "dashscope":
        if not args.base_url:
            args.base_url = os.getenv("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
        if not args.model:
            args.model = "qwen-plus"
        if not args.api_key:
            args.api_key = os.getenv("DASHSCOPE_API_KEY")
    elif args.provider == "deepseek":
        if not args.base_url:
            args.base_url = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
        if not args.model:
            args.model = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
        if not args.api_key:
            args.api_key = os.getenv("DEEPSEEK_API_KEY")
    else:
        if not args.api_key:
            args.api_key = os.getenv("OPENAI_API_KEY")

    cpu_count = max(1, os.cpu_count() or 1)
    if args.resource_profile == "interactive":
        if args.export_workers is None:
            args.export_workers = 1
        if args.yield_seconds is None:
            args.yield_seconds = 0.02
        if args.cpu_nice is None:
            args.cpu_nice = 10
    elif args.resource_profile == "balanced":
        if args.export_workers is None:
            args.export_workers = max(1, min(2, cpu_count // 2))
        if args.yield_seconds is None:
            args.yield_seconds = 0.01
        if args.cpu_nice is None:
            args.cpu_nice = 5
    else:
        if args.export_workers is None:
            args.export_workers = max(1, cpu_count // 2)
        if args.yield_seconds is None:
            args.yield_seconds = 0.0
        if args.cpu_nice is None:
            args.cpu_nice = 0


def apply_resource_controls(args: argparse.Namespace) -> None:
    nice_increment = int(args.cpu_nice or 0)
    if nice_increment <= 0:
        return
    try:
        os.nice(nice_increment)
        progress_log(f"[resource] applied os.nice +{nice_increment}")
    except Exception as exc:
        progress_log(f"[resource] os.nice skipped: {type(exc).__name__}")


def resolve_path(path_str: str) -> Path:
    raw = Path(path_str)
    candidates = [
        raw,
        SCRIPT_DIR / raw,
        REPO_ROOT / raw,
    ]
    for candidate in candidates:
        if candidate.exists():
            return candidate.resolve()
    return raw.resolve()


def parse_format_mix(raw: str) -> dict[str, int]:
    mix: dict[str, int] = {}
    for token in raw.split(","):
        token = token.strip()
        if not token:
            continue
        key, _, value = token.partition("=")
        fmt = key.strip().lower()
        if fmt not in SUPPORTED_FORMATS:
            raise ValueError(f"Unsupported format in mix: {fmt}")
        mix[fmt] = int(value.strip())
    if not mix:
        mix = dict(DEFAULT_FORMAT_MIX)
    return mix


def allocate_counts(
    total: int,
    weights: dict[str, float],
    ordered_keys: tuple[str, ...] | None = None,
) -> dict[str, int]:
    weight_sum = sum(weights.values())
    if weight_sum <= 0:
        raise ValueError("Format mix weights must be positive.")

    keys = [k for k in (ordered_keys or tuple(weights.keys())) if k in weights]
    if not keys:
        raise ValueError("No valid keys found for allocation.")

    allocation: dict[str, int] = {}
    remainders: list[tuple[float, str]] = []
    assigned = 0
    for key in keys:
        exact = total * (weights[key] / weight_sum)
        count = int(exact)
        allocation[key] = count
        assigned += count
        remainders.append((exact - count, key))

    while assigned < total:
        remainders.sort(reverse=True)
        _, fmt = remainders[0]
        allocation[fmt] += 1
        assigned += 1
    return allocation


def parse_level_mix(raw: str) -> dict[str, float]:
    mix: dict[str, float] = {}
    for token in raw.split(","):
        token = token.strip()
        if not token:
            continue
        key, _, value = token.partition("=")
        level = key.strip().upper()
        if level not in JD_LEVELS:
            raise ValueError(f"Unsupported level in mix: {level}")
        weight = float(value.strip())
        if weight <= 0:
            raise ValueError(f"Level weight must be positive: {level}={weight}")
        mix[level] = weight
    if not mix:
        mix = dict(DEFAULT_LEVEL_MIX)
    return mix


def build_level_plan(total: int, level_mix: dict[str, float], rng: random.Random) -> list[str]:
    allocation = allocate_counts(total, level_mix, JD_LEVELS)
    levels: list[str] = []
    for level in JD_LEVELS:
        count = allocation.get(level, 0)
        if count > 0:
            levels.extend([level] * count)
    rng.shuffle(levels)
    return levels


def validate_ratios(args: argparse.Namespace) -> None:
    ratios = [args.duplicate_ratio, args.near_duplicate_ratio, args.anomaly_ratio]
    if any(r < 0 or r >= 1 for r in ratios):
        raise ValueError("duplicate/near-duplicate/anomaly ratio must be in [0, 1).")
    if args.duplicate_ratio + args.near_duplicate_ratio + args.anomaly_ratio >= 1:
        raise ValueError("Sum of ratios must be less than 1.")
    if not (0.0 <= args.max_similarity <= 1.0):
        raise ValueError("--max-similarity must be in [0, 1].")


def scenario_counts(total: int, duplicate_ratio: float, near_ratio: float, anomaly_ratio: float) -> dict[str, int]:
    duplicate = int(total * duplicate_ratio)
    near_dup = int(total * near_ratio)
    anomaly = int(total * anomaly_ratio)
    unique = total - duplicate - near_dup
    if unique <= 0:
        raise ValueError("Ratios are too high. Need at least one unique record.")
    if anomaly > unique:
        anomaly = unique
    normal = unique - anomaly
    return {
        "normal": normal,
        "anomaly": anomaly,
        "near_duplicate": near_dup,
        "duplicate": duplicate,
    }


def build_client(args: argparse.Namespace) -> Any | None:
    if not args.use_llm:
        return None
    if not args.api_key:
        if args.provider == "deepseek":
            raise ValueError("Missing DeepSeek API key. Set DEEPSEEK_API_KEY or pass --api-key.")
        if args.provider == "dashscope":
            raise ValueError("Missing DashScope API key. Set DASHSCOPE_API_KEY or pass --api-key.")
        raise ValueError("Missing API key. Pass --api-key or set OPENAI_API_KEY.")
    if not args.base_url:
        raise ValueError("Missing --base-url for LLM client.")
    if not args.model:
        raise ValueError("Missing --model for LLM generation.")
    try:
        from openai import OpenAI
    except ImportError as exc:  # pragma: no cover - runtime dependency
        raise RuntimeError("openai package is required for --use-llm. Install with: pip install openai") from exc
    return OpenAI(api_key=args.api_key, base_url=args.base_url)


def normalize_line(line: str) -> str:
    line = re.sub(r"\s+", " ", line).strip()
    return line


def extract_template_context(template_pdf: Path) -> dict[str, Any]:
    if not template_pdf.exists():
        raise FileNotFoundError(f"Template PDF not found: {template_pdf}")
    if PdfReader is None:
        print("[warn] pypdf not installed, fallback to default template sections.", flush=True)
        return {
            "template_pdf": str(template_pdf),
            "section_order": list(DEFAULT_SECTION_ORDER),
            "section_labels": dict(DEFAULT_SECTION_LABELS),
            "preview_lines": [],
        }

    reader = PdfReader(str(template_pdf))
    all_lines: list[str] = []
    for page in reader.pages:
        text = page.extract_text() or ""
        lines = [normalize_line(x) for x in text.splitlines()]
        all_lines.extend([x for x in lines if x])

    detected_order: list[str] = []
    detected_labels: dict[str, str] = {}
    for line in all_lines:
        lower = line.lower()
        for section in DEFAULT_SECTION_ORDER:
            if section in detected_order:
                continue
            keywords = SECTION_KEYWORDS.get(section, [])
            if any(k in lower or k in line for k in keywords):
                detected_order.append(section)
                detected_labels[section] = line

    final_order = list(detected_order)
    for section in DEFAULT_SECTION_ORDER:
        if section not in final_order:
            final_order.append(section)

    labels = {}
    for section in DEFAULT_SECTION_ORDER:
        labels[section] = detected_labels.get(section, DEFAULT_SECTION_LABELS[section])

    return {
        "template_pdf": str(template_pdf),
        "section_order": final_order,
        "section_labels": labels,
        "preview_lines": all_lines[:30],
    }


def extract_json_object(text: str) -> dict[str, Any]:
    fenced = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, flags=re.S)
    if fenced:
        return json.loads(fenced.group(1))

    first = text.find("{")
    last = text.rfind("}")
    if first == -1 or last == -1 or first >= last:
        raise ValueError("Model response does not contain a JSON object.")
    return json.loads(text[first : last + 1])


def validate_record(data: dict[str, Any]) -> None:
    required = [
        "name",
        "target_role",
        "years_experience",
        "email",
        "phone",
        "summary",
        "skills",
        "projects",
        "work_experience",
        "education",
    ]
    for key in required:
        if key not in data:
            raise ValueError(f"Missing field: {key}")
    if not isinstance(data["skills"], list) or not data["skills"]:
        raise ValueError("skills must be a non-empty list")
    if not isinstance(data["projects"], list) or not data["projects"]:
        raise ValueError("projects must be a non-empty list")

    narrative_fields: list[str] = [str(data.get("summary", ""))]
    for project in data.get("projects", []):
        narrative_fields.append(str(project.get("responsibility", "")))
        narrative_fields.append(str(project.get("achievement", "")))
    for exp in data.get("work_experience", []):
        narrative_fields.extend([str(x) for x in exp.get("highlights", [])])

    if any(field.strip() and not CHINESE_CHAR_RE.search(field) for field in narrative_fields):
        raise ValueError(
            "Narrative fields must be Chinese text (technical terms can stay English): "
            "summary/projects.responsibility/projects.achievement/work_experience.highlights."
        )


def narrative_fragments(record: dict[str, Any]) -> list[str]:
    fragments: list[str] = [str(record.get("summary", "")).strip()]
    for project in record.get("projects", []):
        fragments.append(str(project.get("responsibility", "")).strip())
        fragments.append(str(project.get("achievement", "")).strip())
    for exp in record.get("work_experience", []):
        fragments.extend([str(x).strip() for x in exp.get("highlights", []) if str(x).strip()])
    return [x for x in fragments if x]


def normalized_narrative(text: str) -> str:
    lowered = text.lower()
    lowered = re.sub(r"\d+(?:\.\d+)?", "#", lowered)
    lowered = re.sub(r"[^\u4e00-\u9fff#a-z]+", "", lowered)
    return lowered


def quality_issues(record: dict[str, Any], quality_threshold: float) -> list[str]:
    issues: list[str] = []
    projects = record.get("projects", []) or []
    experiences = record.get("work_experience", []) or []
    level = str(record.get("_jd_level", ""))
    min_experience = 1 if level == "L0" else 2
    min_highlights_total = 2 if level == "L0" else 4

    if len(projects) < 2:
        issues.append("projects_too_few")
    if len(experiences) < min_experience:
        issues.append("experience_too_few")

    highlight_total = 0
    for project in projects:
        stack = project.get("stack", []) or []
        if len(stack) < 3:
            issues.append("project_stack_too_short")
        if len(str(project.get("responsibility", "")).strip()) < 20:
            issues.append("project_responsibility_too_short")
        achievement = str(project.get("achievement", "")).strip()
        if len(achievement) < 20:
            issues.append("project_achievement_too_short")
        if not re.search(r"\d", achievement):
            issues.append("project_missing_metric")

    for exp in experiences:
        highlights = [str(x).strip() for x in exp.get("highlights", []) if str(x).strip()]
        highlight_total += len(highlights)
        if len(highlights) < 2:
            issues.append("experience_highlights_too_few")

    if highlight_total < min_highlights_total:
        issues.append("highlights_total_too_few")

    summary = str(record.get("summary", "")).strip()
    if len(summary) < 28:
        issues.append("summary_too_short")

    narrative = narrative_fragments(record)
    normalized = [normalized_narrative(x) for x in narrative if normalized_narrative(x)]
    if normalized:
        uniq_ratio = len(set(normalized)) / len(normalized)
        if uniq_ratio < quality_threshold:
            issues.append("narrative_repetition_high")

    all_text = " ".join(narrative)
    boilerplate_hits = sum(all_text.count(p) for p in BOILERPLATE_PHRASES)
    if boilerplate_hits > 1:
        issues.append("boilerplate_phrase_repeated")

    return sorted(set(issues))


def progress_log(message: str) -> None:
    print(message, flush=True)


def should_emit_progress(index: int, total: int, every: int) -> bool:
    step = max(1, every)
    return index == 0 or index + 1 == total or ((index + 1) % step == 0)


def extract_stream_chunk_text(chunk: Any) -> str:
    def extract_text_content(content: Any) -> str:
        if isinstance(content, str):
            return content
        if isinstance(content, dict):
            text_obj = content.get("text")
            if isinstance(text_obj, str):
                return text_obj
            if isinstance(text_obj, dict):
                value = text_obj.get("value")
                return value if isinstance(value, str) else ""
            value = content.get("value")
            return value if isinstance(value, str) else ""
        if isinstance(content, list):
            pieces: list[str] = []
            for part in content:
                extracted = extract_text_content(part)
                if extracted:
                    pieces.append(extracted)
            return "".join(pieces)
        text_attr = getattr(content, "text", None)
        if isinstance(text_attr, str):
            return text_attr
        if text_attr is not None:
            value_attr = getattr(text_attr, "value", None)
            if isinstance(value_attr, str):
                return value_attr
        value_attr = getattr(content, "value", None)
        if isinstance(value_attr, str):
            return value_attr
        return ""

    try:
        choices = getattr(chunk, "choices", None)
        if choices is None and isinstance(chunk, dict):
            choices = chunk.get("choices")
        if not choices:
            return ""
        first = choices[0]
        delta = getattr(first, "delta", None)
        if delta is None and isinstance(first, dict):
            delta = first.get("delta")
        if delta is not None:
            content = getattr(delta, "content", None)
            if content is None and isinstance(delta, dict):
                content = delta.get("content")
            return extract_text_content(content)
    except Exception:
        return ""
    return ""


def request_llm_content(
    client: Any,
    kwargs: dict[str, Any],
    args: argparse.Namespace,
    seed: int,
    force_non_stream: bool = False,
) -> str:
    use_stream = args.llm_stream and not force_non_stream
    if use_stream:
        stream_kwargs = dict(kwargs)
        stream_kwargs["stream"] = True
        parts: list[str] = []
        chunks = 0
        stream = client.chat.completions.create(**stream_kwargs)
        for chunk in stream:
            piece = extract_stream_chunk_text(chunk)
            if not piece:
                continue
            parts.append(piece)
            chunks += 1
            if chunks % max(1, args.stream_chunk_interval) == 0:
                progress_log(f"[llm] seed={seed} streamed_chunks={chunks}")
        content = "".join(parts).strip()
        if content:
            progress_log(f"[llm] seed={seed} stream_done chunks={chunks}")
            return content
        raise ValueError("Empty content from streaming response.")

    response = client.chat.completions.create(**kwargs)
    return (response.choices[0].message.content or "").strip()


def choose_domains(rng: random.Random) -> tuple[str, str]:
    primary = rng.choice(list(DOMAIN_PROFILES.keys()))
    secondary_pool = [x for x in DOMAIN_PROFILES.keys() if x != primary]
    secondary = rng.choice(secondary_pool) if rng.random() < 0.45 else ""
    return primary, secondary


def level_role_candidates(primary: str, level: str) -> list[str]:
    prefix = DOMAIN_ROLE_PREFIX.get(primary, "Software")
    if level == "L0":
        return [f"{prefix}实习工程师", "软件工程实习生"]
    if level == "L1":
        return [f"初级{prefix}工程师", f"{prefix}工程师"]
    if level == "L2":
        return [f"{prefix}工程师", "软件工程师"]
    if level == "L3":
        return [f"高级{prefix}工程师", f"{prefix}资深工程师"]
    return [f"{prefix}技术负责人", f"{prefix}架构负责人", "资深工程师"]


def domain_label_zh(domain: str) -> str:
    return DOMAIN_LABELS_ZH.get(domain, domain.replace("_", "-"))


def sample_years_for_level(level: str, rng: random.Random) -> int:
    low, high = LEVEL_YEAR_RANGES.get(level, (2, 12))
    return rng.randint(low, high)


def apply_level_constraints(record: dict[str, Any], level: str, primary: str, rng: random.Random) -> dict[str, Any]:
    data = copy.deepcopy(record)
    low, high = LEVEL_YEAR_RANGES.get(level, (2, 12))
    years = int(data.get("years_experience", low))
    data["years_experience"] = max(low, min(high, years))
    data["target_role"] = rng.choice(level_role_candidates(primary, level))
    data["_jd_level"] = level
    return data


def llm_generate_record(
    client: Any,
    args: argparse.Namespace,
    model: str,
    seed: int,
    primary: str,
    secondary: str,
    level: str,
) -> dict[str, Any]:
    secondary_hint = secondary if secondary else "none"
    year_low, year_high = LEVEL_YEAR_RANGES.get(level, (2, 12))
    role_hint = " / ".join(level_role_candidates(primary, level))
    prompt = f"""
请生成一份真实、细节充足的软件工程简历 JSON。
只返回 JSON，不要输出任何额外文本。
seed={seed}
primary_domain={primary}
secondary_domain={secondary_hint}
jd_level={level}

硬性约束：
- 方向必须匹配 primary_domain，可选融合 secondary_domain。
- 年限应落在 {year_low}-{year_high} 年。
- target_role 与 {level} 匹配，参考：{role_hint}。
- 在输出前请先进行不少于 {args.llm_think_steps} 步的内部推理与自检（不要输出推理过程）。
- 技术栈不能是固定模板，需要体现 frontend/backend/devops/ai-infra/data/mobile/security 的差异。
- projects 至少 2 条、work_experience 至少 2 条，每段经历至少 2 条 highlights。
- 每条项目成果必须有至少 2 个数字指标（如延迟、吞吐、成本、成功率、故障恢复时长、交付周期）。
- 尽量写出真实工程约束：容量、稳定性、灰度、回滚、成本、跨团队协作、oncall、事故复盘等。
- 项目成果必须包含可量化指标（数字）。
- 叙述字段必须为中文句子，允许夹带英文技术词汇：
  summary、projects[].responsibility、projects[].achievement、work_experience[].highlights[]。
- 不要输出整段英文叙述。
- 禁止重复套话，尤其避免反复出现以下表达：
  「围绕稳定性目标持续优化」「推进跨团队交付协同」「全流程负责」。

返回 JSON Schema：
{{
  "name": "string",
  "email": "string",
  "phone": "string",
  "target_role": "string",
  "years_experience": 5,
  "summary": "string",
  "skills": ["string", "..."],
  "projects": [
    {{
      "name": "string",
      "stack": ["string", "..."],
      "responsibility": "string",
      "achievement": "string"
    }}
  ],
  "work_experience": [
    {{
      "company": "string",
      "title": "string",
      "period": "YYYY-MM 至 YYYY-MM|至今",
      "highlights": ["string", "..."]
    }}
  ],
  "education": [
    {{
      "school": "string",
      "degree": "string",
      "major": "string",
      "period": "YYYY 至 YYYY"
    }}
  ],
  "certifications": ["string", "..."],
  "domain": "{primary}",
  "secondary_domain": "{secondary_hint}"
}}
"""
    base_kwargs: dict[str, Any] = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": args.llm_temperature,
        "top_p": args.llm_top_p,
        "presence_penalty": args.llm_presence_penalty,
        "frequency_penalty": args.llm_frequency_penalty,
        "reasoning_effort": args.reasoning_effort,
        "max_completion_tokens": args.llm_max_completion_tokens,
        "response_format": {"type": "json_object"},
    }

    last_exc: Exception | None = None
    variants: list[dict[str, Any]] = []

    # Provider-first compatibility chain to avoid extra failed round-trips.
    if args.provider == "deepseek":
        # DeepSeek chat is most stable with this minimal compatible schema.
        v1 = dict(base_kwargs)
        v1["max_tokens"] = v1.pop("max_completion_tokens")
        v1.pop("reasoning_effort", None)
        v1.pop("response_format", None)
        v2 = dict(v1)
        for k in ("presence_penalty", "frequency_penalty"):
            v2.pop(k, None)
        variants.append(v2)

        # Fallback: keep penalties if sampling gets too conservative.
        variants.append(v1)
    else:
        v1 = dict(base_kwargs)
        variants.append(v1)

        v2 = dict(base_kwargs)
        v2["max_tokens"] = v2.pop("max_completion_tokens")
        variants.append(v2)

        v3 = dict(v2)
        v3.pop("reasoning_effort", None)
        variants.append(v3)

        v4 = dict(v3)
        for k in ("presence_penalty", "frequency_penalty"):
            v4.pop(k, None)
        variants.append(v4)

        v5 = dict(v4)
        v5.pop("response_format", None)
        variants.append(v5)

    for kwargs in variants:
        try:
            progress_log(f"[llm] seed={seed} request start")
            content = request_llm_content(client, kwargs, args, seed)
            try:
                record = extract_json_object(content)
                validate_record(record)
                if "domain" not in record:
                    record["domain"] = primary
                if "secondary_domain" not in record:
                    record["secondary_domain"] = secondary
                return record
            except Exception as parse_exc:
                last_exc = parse_exc
                if args.llm_stream:
                    progress_log(f"[llm] seed={seed} parse_failed retry_non_stream")
                    content = request_llm_content(client, kwargs, args, seed, force_non_stream=True)
                    record = extract_json_object(content)
                    validate_record(record)
                    if "domain" not in record:
                        record["domain"] = primary
                    if "secondary_domain" not in record:
                        record["secondary_domain"] = secondary
                    return record
                continue
        except Exception as exc:  # pragma: no cover - runtime/network/provider compatibility
            last_exc = exc
            progress_log(f"[llm] seed={seed} fallback due to {type(exc).__name__}")
            continue

    raise RuntimeError(f"LLM request failed after compatibility fallbacks: {last_exc}") from last_exc


def random_company(rng: random.Random) -> str:
    return f"{rng.choice(COMPANY_PREFIX)}{rng.choice(COMPANY_SUFFIX)}"


def rich_responsibility(topic: str, rng: random.Random) -> str:
    prefixes = [
        f"负责 {topic} 的核心链路改造",
        f"主导 {topic} 在生产环境的架构升级",
        f"承担 {topic} 的性能与稳定性专项",
        f"围绕 {topic} 推进跨团队技术方案落地",
    ]
    methods = [
        "引入灰度发布和可回滚机制",
        "补齐链路追踪、日志与告警闭环",
        "重构热点模块并拆分服务边界",
        "建立容量评估与压测基线",
        "推动接口契约治理与自动化回归",
    ]
    endings = [
        "保障高峰期可用性。",
        "显著降低线上变更风险。",
        "缩短故障定位与恢复时长。",
        "提升版本交付确定性。",
    ]
    return f"{rng.choice(prefixes)}，{rng.choice(methods)}，{rng.choice(endings)}"


def rich_achievement(rng: random.Random) -> str:
    latency = rng.randint(12, 68)
    throughput = rng.randint(20, 180)
    failure = rng.randint(18, 65)
    cost = rng.randint(10, 40)
    mttr = rng.randint(15, 55)
    return (
        f"核心链路吞吐提升 {throughput}%，p95 延迟下降 {latency}%，"
        f"故障率下降 {failure}%，资源成本下降 {cost}%，MTTR 缩短 {mttr}%。"
    )


def rich_highlights(topic: str, rng: random.Random) -> list[str]:
    line1 = (
        f"推动 {topic} 建立分层告警与值班手册，"
        f"将夜间告警噪音降低 {rng.randint(25, 70)}%。"
    )
    line2 = (
        f"主导一次高峰期容量演练，峰值 QPS 提升 {rng.randint(20, 120)}%，"
        f"关键接口超时率下降 {rng.randint(15, 60)}%。"
    )
    line3 = (
        f"协同产品与测试完成故障复盘 {rng.randint(2, 8)} 次，"
        f"上线回滚率下降 {rng.randint(15, 50)}%。"
    )
    return [line1, line2, line3]


def pick_skills(primary: str, secondary: str, rng: random.Random) -> list[str]:
    pool = set(DOMAIN_PROFILES[primary]["skills"])
    if secondary:
        pool.update(DOMAIN_PROFILES[secondary]["skills"][: rng.randint(3, 6)])
    pool.update(rng.sample(GENERIC_SKILLS, k=rng.randint(3, 6)))
    chosen = list(pool)
    rng.shuffle(chosen)
    return chosen[: rng.randint(8, min(14, len(chosen)))]


def create_periods(years_experience: int, count: int, rng: random.Random) -> list[str]:
    end_year = time.localtime().tm_year
    start_year = end_year - years_experience
    periods: list[str] = []
    cursor = start_year
    for i in range(count):
        if i == count - 1:
            periods.append(f"{cursor}-{rng.randint(1, 12):02d} 至今")
            continue
        span = max(1, years_experience // count + rng.randint(0, 1))
        next_year = min(end_year - 1, cursor + span)
        start_month = rng.randint(1, 12)
        if next_year == cursor:
            end_month = rng.randint(start_month, 12)
        else:
            end_month = rng.randint(1, 12)
        periods.append(f"{cursor}-{start_month:02d} 至 {next_year}-{end_month:02d}")
        cursor = next_year
    return periods


def synthetic_record(
    rng: random.Random,
    seed: int,
    primary: str,
    secondary: str,
    level: str,
) -> dict[str, Any]:
    profile = DOMAIN_PROFILES[primary]
    years = sample_years_for_level(level, rng)

    name = f"{rng.choice(FIRST_NAMES)} {rng.choice(LAST_NAMES)}"
    email_user = re.sub(r"[^a-z0-9]", "", name.lower())
    email = f"{email_user}{seed % 10000}@example.com"
    phone = f"+86-1{rng.randint(3000000000, 9999999999)}"
    role = rng.choice(profile["roles"])
    skills = pick_skills(primary, secondary, rng)
    if level == "L0":
        company_count = rng.randint(1, 2)
    elif level == "L1":
        company_count = 2
    else:
        company_count = rng.randint(2, 4)
    companies = [random_company(rng) for _ in range(company_count)]
    periods = create_periods(years, len(companies), rng)

    project_count = rng.randint(2, 4)
    projects = []
    for _ in range(project_count):
        topic = rng.choice(profile["topics"])
        project_stack = rng.sample(skills, k=min(rng.randint(4, 7), len(skills)))
        projects.append(
            {
                "name": f"{random_company(rng)} {topic}",
                "stack": project_stack,
                "responsibility": rich_responsibility(topic, rng),
                "achievement": rich_achievement(rng),
            }
        )

    work_experience = []
    for company, period in zip(companies, periods):
        topic = rng.choice(profile["topics"])
        highlights = rich_highlights(topic, rng)
        rng.shuffle(highlights)
        work_experience.append(
            {
                "company": company,
                "title": role if rng.random() < 0.65 else rng.choice(profile["roles"]),
                "period": period,
                "highlights": highlights[: rng.randint(2, 3)],
            }
        )

    degree = rng.choice(["本科", "硕士"])
    major = rng.choice(
        [
            "计算机科学与技术",
            "软件工程",
            "信息安全",
            "数据科学",
            "自动化",
            "电子工程",
        ]
    )
    edu_start = rng.randint(2010, 2018)
    edu_end = edu_start + (4 if degree == "本科" else 3)

    summary_templates = [
        "拥有 {years} 年经验的 {role}，聚焦 {primary}{secondary_part} 方向，擅长高可用系统与工程质量建设。",
        "{role}，在 {primary}{secondary_part} 方向有 {years} 年一线经验，持续产出可量化的性能与稳定性改进。",
        "{role}（{years} 年），覆盖 {primary}{secondary_part} 场景，能够从方案设计到运维治理全链路负责。",
        "{role}，累计 {years} 年工程实践，主攻 {primary}{secondary_part}，在容量治理、可观测性与交付效率上有稳定产出。",
        "{role}（{years} 年经验），擅长处理 {primary}{secondary_part} 场景下的性能瓶颈、故障恢复与跨团队协作。",
    ]
    secondary_part = f" + {domain_label_zh(secondary)}" if secondary else ""
    summary = rng.choice(summary_templates).format(
        role=role,
        years=years,
        primary=domain_label_zh(primary),
        secondary_part=secondary_part,
    )

    certifications = rng.sample(CERT_POOL, k=rng.randint(1, 3))

    return {
        "name": name,
        "email": email,
        "phone": phone,
        "target_role": role,
        "years_experience": years,
        "summary": summary,
        "skills": skills,
        "projects": projects,
        "work_experience": work_experience,
        "education": [
            {
                "school": rng.choice(SCHOOL_POOL),
                "degree": degree,
                "major": major,
                "period": f"{edu_start} 至 {edu_end}",
            }
        ],
        "certifications": certifications,
        "domain": primary,
        "secondary_domain": secondary,
        "_jd_level": level,
    }


def normalize_record(
    record: dict[str, Any],
    primary: str,
    secondary: str,
    level: str,
    rng: random.Random,
) -> dict[str, Any]:
    data = copy.deepcopy(record)
    data["domain"] = data.get("domain") or primary
    data["secondary_domain"] = data.get("secondary_domain") or secondary
    data["years_experience"] = max(1, int(data.get("years_experience", 1)))
    data["skills"] = list(dict.fromkeys([str(x).strip() for x in data.get("skills", []) if str(x).strip()]))
    if not data["skills"]:
        data["skills"] = pick_skills(primary, secondary, random.Random(data["years_experience"]))
    data["projects"] = data.get("projects") or []
    data["work_experience"] = data.get("work_experience") or []
    data["education"] = data.get("education") or []
    if not data["projects"]:
        data["projects"] = [
            {
                "name": f"{data['domain']} 平台项目",
                "stack": data["skills"][:5],
                "responsibility": "负责关键模块设计与交付流水线建设，推动研发流程标准化。",
                "achievement": "系统稳定性与交付效率均得到明显提升。",
            }
        ]
    if not data["work_experience"]:
        data["work_experience"] = [
            {
                "company": random_company(random.Random(data["years_experience"])),
                "title": data.get("target_role", "Engineer"),
                "period": "2021-01 至今",
                "highlights": ["承担核心功能交付并持续推进稳定性优化。"],
            }
        ]
    if not data["education"]:
        data["education"] = [
            {
                "school": "Unknown University",
                "degree": "本科",
                "major": "计算机科学与技术",
                "period": "2014 至 2018",
            }
        ]
    return apply_level_constraints(data, level, str(data["domain"]), rng)


def signature_text(record: dict[str, Any]) -> str:
    parts: list[str] = []
    parts.append(str(record.get("target_role", "")))
    parts.append(str(record.get("summary", "")))
    parts.extend(record.get("skills", []))
    for project in record.get("projects", []):
        parts.append(str(project.get("name", "")))
        parts.extend(project.get("stack", []))
        parts.append(str(project.get("responsibility", "")))
        parts.append(str(project.get("achievement", "")))
    for exp in record.get("work_experience", []):
        parts.append(str(exp.get("company", "")))
        parts.append(str(exp.get("title", "")))
        parts.extend(exp.get("highlights", []))
    return " ".join(parts)


def tokenize_signature(text: str) -> set[str]:
    tokens = re.findall(r"[a-z0-9_+#.-]+|[\u4e00-\u9fff]{2,}", text.lower())
    return set(tokens)


def jaccard_similarity(a: set[str], b: set[str]) -> float:
    if not a and not b:
        return 1.0
    union = a | b
    if not union:
        return 0.0
    return len(a & b) / len(union)


def max_similarity(tokens: set[str], previous: list[set[str]]) -> float:
    if not previous:
        return 0.0
    best = 0.0
    for old in previous:
        score = jaccard_similarity(tokens, old)
        if score > best:
            best = score
    return best


def generate_candidate_record(
    args: argparse.Namespace,
    rng: random.Random,
    client: Any | None,
    seed: int,
    primary: str,
    secondary: str,
    level: str,
) -> dict[str, Any]:
    if client is None:
        fallback: dict[str, Any] | None = None
        for _ in range(args.max_retries):
            candidate = synthetic_record(rng, seed, primary, secondary, level)
            normalized = normalize_record(candidate, primary, secondary, level, rng)
            fallback = normalized
            if not quality_issues(normalized, args.quality_threshold):
                return normalized
        return fallback if fallback is not None else synthetic_record(rng, seed, primary, secondary, level)

    last_exc: Exception | None = None
    for attempt in range(args.max_retries):
        try:
            progress_log(f"[gen][llm] seed={seed} attempt={attempt + 1}/{args.max_retries}")
            record = llm_generate_record(client, args, args.model, seed, primary, secondary, level)
            normalized = normalize_record(record, primary, secondary, level, rng)
            issues = quality_issues(normalized, args.quality_threshold)
            if issues:
                raise ValueError(f"Record quality is insufficient: {','.join(issues)}")
            return normalized
        except Exception as exc:  # pragma: no cover - network/runtime branch
            last_exc = exc
            brief = " ".join(str(exc).split())[:220]
            progress_log(f"[gen][llm] seed={seed} attempt={attempt + 1} failed: {brief}")
            time.sleep(args.sleep_seconds)
    if last_exc is not None:
        if args.llm_fallback_synthetic:
            progress_log(f"[gen][llm] seed={seed} fallback=synthetic")
            return synthetic_record(rng, seed, primary, secondary, level)
        raise RuntimeError(
            f"LLM generation failed after {args.max_retries} retries for seed={seed}: {last_exc}"
        ) from last_exc
    if args.llm_fallback_synthetic:
        progress_log(f"[gen][llm] seed={seed} fallback=synthetic")
        return synthetic_record(rng, seed, primary, secondary, level)
    raise RuntimeError(f"LLM generation failed unexpectedly for seed={seed} with no captured exception.")


def generate_unique_records(
    args: argparse.Namespace,
    total: int,
    rng: random.Random,
    level_plan: list[str],
) -> list[dict[str, Any]]:
    if len(level_plan) != total:
        raise ValueError("level_plan length must equal unique record total.")
    records: list[dict[str, Any]] = []
    token_bank: list[set[str]] = []
    client = build_client(args)

    for idx in range(total):
        target_level = level_plan[idx]
        if should_emit_progress(idx, total, args.progress_every):
            progress_log(f"[build] unique_record={idx + 1}/{total} target_level={target_level}")
        accepted = False
        for attempt in range(args.max_sim_retries):
            primary, secondary = choose_domains(rng)
            seed = args.seed * 100_000 + idx * 97 + attempt
            record = generate_candidate_record(args, rng, client, seed, primary, secondary, target_level)
            tokens = tokenize_signature(signature_text(record))
            score = max_similarity(tokens, token_bank)
            if score <= args.max_similarity:
                record["_max_similarity"] = round(score, 4)
                records.append(record)
                token_bank.append(tokens)
                accepted = True
                if should_emit_progress(idx, total, args.progress_every):
                    progress_log(
                        f"[build] accepted={idx + 1}/{total} "
                        f"domain={record.get('domain')} score={record['_max_similarity']} attempts={attempt + 1}"
                    )
                break
            if args.use_llm:
                time.sleep(args.sleep_seconds)

        if not accepted:
            raise RuntimeError(
                f"Failed to generate diverse resume at index {idx}. "
                f"Try increasing --max-similarity (current {args.max_similarity}) "
                f"or raising --max-sim-retries."
            )
    return records


def make_near_duplicate(base: dict[str, Any], rng: random.Random) -> dict[str, Any]:
    clone = copy.deepcopy(base)
    clone["years_experience"] = max(1, int(clone["years_experience"]) + rng.choice([-1, 1]))
    clone["summary"] = clone["summary"] + " 进一步负责跨地域稳定性治理与容量规划。"
    clone["skills"] = clone["skills"] + [rng.choice(["ClickHouse", "ArgoCD", "Istio", "Sentry", "LangChain"])]
    if clone["projects"]:
        clone["projects"][0]["achievement"] = (
            clone["projects"][0]["achievement"] + f" 并辅导 {rng.randint(1, 6)} 名工程师完成关键模块交付。"
        )
    level = clone.get("_jd_level")
    if level:
        clone = apply_level_constraints(clone, str(level), str(clone.get("domain", "backend")), rng)
    clone["_generated_from"] = "near_duplicate"
    return clone


def make_anomaly(base: dict[str, Any], rng: random.Random) -> dict[str, Any]:
    clone = copy.deepcopy(base)
    anomaly_type = rng.choice(["missing_contact", "noisy_text", "long_paragraph", "special_chars"])
    if anomaly_type == "missing_contact":
        clone["email"] = ""
        clone["phone"] = ""
    elif anomaly_type == "noisy_text":
        clone["summary"] = "### ??? " + clone["summary"] + " //// ####"
    elif anomaly_type == "long_paragraph":
        clone["summary"] = " ".join([clone["summary"]] * 15)
    else:
        clone["name"] = clone["name"] + " @@@"
    clone["_anomaly_type"] = anomaly_type
    return clone


def render_resume_text(record: dict[str, Any], template_context: dict[str, Any], rng: random.Random) -> str:
    sep = "："
    bullet = "- "
    labels = DEFAULT_SECTION_LABELS
    section_order = template_context["section_order"]

    sections: dict[str, list[str]] = {}
    sections["profile"] = [
        f"姓名{sep}{record['name']}",
        f"目标岗位{sep}{record['target_role']}",
        f"邮箱{sep}{record['email']}",
        f"电话{sep}{record['phone']}",
        f"经验年限{sep}{record['years_experience']} 年",
        f"方向{sep}{domain_label_zh(str(record.get('domain', '')))}"
        + (f" + {domain_label_zh(str(record.get('secondary_domain')))}" if record.get("secondary_domain") else ""),
        f"简介{sep}{record['summary']}",
    ]

    sections["skills"] = [f"{bullet}{skill}" for skill in record["skills"]]

    exp_lines: list[str] = []
    for exp in record["work_experience"]:
        exp_lines.append(f"{bullet}{exp['company']} | {exp['title']} | {exp['period']}")
        for hl in exp.get("highlights", []):
            exp_lines.append(f"  {bullet}{hl}")
    sections["experience"] = exp_lines

    project_lines: list[str] = []
    for p in record["projects"]:
        project_lines.append(f"{bullet}{p['name']}")
        project_lines.append(f"  技术栈{sep}{', '.join(p['stack'])}")
        project_lines.append(f"  职责{sep}{p['responsibility']}")
        project_lines.append(f"  成果{sep}{p['achievement']}")
    sections["projects"] = project_lines

    edu_lines: list[str] = []
    for edu in record["education"]:
        edu_lines.append(f"{bullet}{edu['school']} | {edu['degree']} {edu['major']} | {edu['period']}")
    sections["education"] = edu_lines

    certs = record.get("certifications", [])
    sections["certifications"] = [f"{bullet}{c}" for c in certs] if certs else [f"{bullet}暂无"]

    output_lines: list[str] = []
    for section in section_order:
        content = sections.get(section, [])
        if not content:
            continue
        output_lines.append(labels.get(section, DEFAULT_SECTION_LABELS[section]))
        output_lines.extend(content)
        output_lines.append("")
    return "\n".join(output_lines).strip() + "\n"


def export_single_record(
    item: dict[str, Any],
    fmt: str,
    files_dir: Path,
    template_context: dict[str, Any],
    rng_seed: int,
) -> dict[str, Any]:
    record = item["record"]
    record_id = item["record_id"]
    rendered = render_resume_text(record, template_context, random.Random(rng_seed))
    file_name = f"{record_id}.{fmt}"
    path = files_dir / file_name

    if fmt == "txt":
        file_bytes = write_txt(path, rendered)
    elif fmt == "md":
        file_bytes = write_md(path, rendered)
    elif fmt == "docx":
        file_bytes = write_docx(path, rendered)
    elif fmt == "pdf":
        file_bytes = write_pdf(path, rendered)
    else:
        raise ValueError(f"Unsupported format: {fmt}")

    file_hash = sha256_hex(file_bytes)
    level = str(record.get("_jd_level", ""))
    canonical_item = {
        "record_id": record_id,
        "scenario": item["scenario"],
        "source_record_id": item["source_record_id"],
        "format": fmt,
        "record": record,
    }
    return {
        "record": record,
        "level": level,
        "canonical_json": json.dumps(canonical_item, ensure_ascii=False),
        "manifest_row": {
            "record_id": record_id,
            "file_path": str(path),
            "format": fmt,
            "scenario": item["scenario"],
            "source_record_id": item["source_record_id"],
            "file_hash": file_hash,
            "name": record.get("name", ""),
            "target_role": record.get("target_role", ""),
            "domain": record.get("domain", ""),
            "secondary_domain": record.get("secondary_domain", ""),
            "years_experience": record.get("years_experience", ""),
            "max_similarity": record.get("_max_similarity", ""),
            "anomaly_type": record.get("_anomaly_type", ""),
            "jd_level": level,
        },
    }


def run_export_job(job: tuple[int, dict[str, Any], str, int], files_dir: Path, template_context: dict[str, Any]) -> dict[str, Any]:
    _, item, fmt, seed = job
    return export_single_record(item, fmt, files_dir, template_context, seed)


def ensure_writers_available(requested_formats: set[str]) -> None:
    if "docx" in requested_formats and Document is None:
        raise RuntimeError("python-docx is required for DOCX export. Install with: pip install python-docx")
    if "pdf" in requested_formats and (canvas is None or A4 is None):
        raise RuntimeError("reportlab is required for PDF export. Install with: pip install reportlab")


def write_txt(path: Path, text: str) -> bytes:
    data = text.encode("utf-8")
    path.write_bytes(data)
    return data


def write_md(path: Path, text: str) -> bytes:
    data = text.encode("utf-8")
    path.write_bytes(data)
    return data


def write_docx(path: Path, text: str) -> bytes:
    assert Document is not None
    doc = Document()
    try:
        from docx.oxml.ns import qn

        normal_style = doc.styles["Normal"]
        normal_style.font.name = "宋体"
        normal_style._element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")
    except Exception:
        pass
    for line in text.splitlines():
        doc.add_paragraph(line)
    doc.save(str(path))
    return path.read_bytes()


def write_pdf(path: Path, text: str) -> bytes:
    assert canvas is not None and A4 is not None
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.cidfonts import UnicodeCIDFont

    font_name = "STSong-Light"
    try:
        pdfmetrics.getFont(font_name)
    except KeyError:
        pdfmetrics.registerFont(UnicodeCIDFont(font_name))

    c = canvas.Canvas(str(path), pagesize=A4)
    _, height = A4
    left = 40
    top = height - 40

    text_obj = c.beginText(left, top)
    text_obj.setFont(font_name, 10)
    text_obj.setLeading(14)

    for raw_line in text.splitlines():
        line = raw_line.rstrip()
        if not line.strip():
            text_obj.textLine("")
            continue
        wrapped_lines = textwrap.wrap(line, width=48, break_long_words=True, replace_whitespace=False) or [line]
        for wrapped in wrapped_lines:
            text_obj.textLine(wrapped)
            if text_obj.getY() <= 40:
                c.drawText(text_obj)
                c.showPage()
                text_obj = c.beginText(left, top)
                text_obj.setFont(font_name, 10)
                text_obj.setLeading(14)
    c.drawText(text_obj)
    c.save()
    return path.read_bytes()


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def pick_formats(allocation: dict[str, int], rng: random.Random) -> list[str]:
    formats: list[str] = []
    for fmt, count in allocation.items():
        formats.extend([fmt] * count)
    rng.shuffle(formats)
    return formats


def reset_output_dir(out_dir: Path) -> tuple[Path, Path]:
    raw_dir = out_dir / "raw"
    files_dir = out_dir / "files"
    manifest_path = out_dir / "manifest.csv"
    summary_path = out_dir / "summary.json"

    out_dir.mkdir(parents=True, exist_ok=True)
    if raw_dir.exists():
        shutil.rmtree(raw_dir)
    if files_dir.exists():
        shutil.rmtree(files_dir)
    if manifest_path.exists():
        manifest_path.unlink()
    if summary_path.exists():
        summary_path.unlink()

    raw_dir.mkdir(parents=True, exist_ok=True)
    files_dir.mkdir(parents=True, exist_ok=True)
    return raw_dir, files_dir


def build_dataset(args: argparse.Namespace) -> list[dict[str, Any]]:
    rng = random.Random(args.seed)
    counts = scenario_counts(
        total=args.count,
        duplicate_ratio=args.duplicate_ratio,
        near_ratio=args.near_duplicate_ratio,
        anomaly_ratio=args.anomaly_ratio,
    )

    unique_total = counts["normal"] + counts["anomaly"]
    level_mix = parse_level_mix(args.level_mix)
    full_level_plan = build_level_plan(args.count, level_mix, rng)
    unique_level_plan = full_level_plan[:unique_total]
    duplicate_level_plan = full_level_plan[unique_total:]
    unique_records = generate_unique_records(args, unique_total, rng, unique_level_plan)

    dataset: list[dict[str, Any]] = []
    for i in range(counts["normal"]):
        dataset.append({"scenario": "normal", "source_record_id": None, "record": unique_records[i]})

    for i in range(counts["anomaly"]):
        base = unique_records[counts["normal"] + i]
        dataset.append({"scenario": "anomaly", "source_record_id": None, "record": make_anomaly(base, rng)})

    base_pool = [item["record"] for item in dataset if item["scenario"] in ("normal", "anomaly")]
    near_levels = duplicate_level_plan[: counts["near_duplicate"]]
    dup_levels = duplicate_level_plan[counts["near_duplicate"] :]
    for target_level in near_levels:
        level_indices = [i for i, rec in enumerate(base_pool) if rec.get("_jd_level") == target_level]
        idx = rng.choice(level_indices) if level_indices else rng.randrange(len(base_pool))
        dataset.append({"scenario": "near_duplicate", "source_record_id": idx, "record": make_near_duplicate(base_pool[idx], rng)})

    for target_level in dup_levels:
        level_indices = [i for i, rec in enumerate(base_pool) if rec.get("_jd_level") == target_level]
        idx = rng.choice(level_indices) if level_indices else rng.randrange(len(base_pool))
        dup = copy.deepcopy(base_pool[idx])
        dup["_generated_from"] = "duplicate"
        dataset.append({"scenario": "duplicate", "source_record_id": idx, "record": dup})

    rng.shuffle(dataset)
    for i, item in enumerate(dataset):
        item["record_id"] = f"resume_{i:04d}"
    return dataset


def export_dataset(dataset: list[dict[str, Any]], args: argparse.Namespace, template_context: dict[str, Any]) -> dict[str, Any]:
    rng = random.Random(args.seed + 99)
    out_dir = Path(args.out_dir)
    raw_dir, files_dir = reset_output_dir(out_dir)

    mix = parse_format_mix(args.format_mix)
    allocation = allocate_counts(len(dataset), mix, SUPPORTED_FORMATS)
    formats = pick_formats(allocation, rng)
    ensure_writers_available(set(formats))

    canonical_path = raw_dir / "canonical.jsonl"
    manifest_path = out_dir / "manifest.csv"

    canonical_fp = canonical_path.open("w", encoding="utf-8")
    manifest_fp = manifest_path.open("w", encoding="utf-8", newline="")
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
    writer.writeheader()

    format_counter = {fmt: 0 for fmt in SUPPORTED_FORMATS}
    scenario_counter: dict[str, int] = {}
    level_counter = {level: 0 for level in JD_LEVELS}

    try:
        export_jobs = [
            (i, item, formats[i], args.seed + 99 + i)
            for i, item in enumerate(dataset)
        ]

        workers = max(1, int(args.export_workers))
        if workers == 1:
            export_results = [
                export_single_record(item, fmt, files_dir, template_context, seed)
                for _, item, fmt, seed in export_jobs
            ]
        else:
            with ThreadPoolExecutor(max_workers=workers) as executor:
                export_results = list(
                    executor.map(
                        run_export_job,
                        export_jobs,
                        repeat(files_dir),
                        repeat(template_context),
                    )
                )

        for i, (job, result) in enumerate(zip(export_jobs, export_results)):
            _, item, fmt, _ = job
            if should_emit_progress(i, len(dataset), args.progress_every):
                progress_log(f"[export] file={i + 1}/{len(dataset)}")
            format_counter[fmt] = format_counter.get(fmt, 0) + 1
            scenario_counter[item["scenario"]] = scenario_counter.get(item["scenario"], 0) + 1

            level = result["level"]
            if level in level_counter:
                level_counter[level] += 1
            canonical_fp.write(result["canonical_json"] + "\n")
            writer.writerow(result["manifest_row"])
            if args.yield_seconds and args.yield_seconds > 0:
                time.sleep(args.yield_seconds)
    finally:
        canonical_fp.close()
        manifest_fp.close()

    summary = {
        "total": len(dataset),
        "formats": format_counter,
        "scenarios": scenario_counter,
        "levels": level_counter,
        "provider": args.provider,
        "model": args.model,
        "template_pdf": template_context["template_pdf"],
        "template_sections": template_context["section_order"],
        "max_similarity": args.max_similarity,
        "manifest": str(manifest_path),
        "canonical_jsonl": str(canonical_path),
    }
    (out_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return summary


def main() -> None:
    args = parse_args()
    apply_runtime_defaults(args)
    apply_resource_controls(args)
    validate_ratios(args)
    template_pdf = resolve_path(args.template_pdf)
    template_context = extract_template_context(template_pdf)
    progress_log(
        "[start] "
        f"count={args.count} use_llm={args.use_llm} provider={args.provider} "
        f"model={args.model} out_dir={args.out_dir}"
    )
    progress_log("[phase] building dataset")
    dataset = build_dataset(args)
    progress_log("[phase] exporting files")
    summary = export_dataset(dataset, args, template_context)
    progress_log("[done] generation finished")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
