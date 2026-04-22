#!/usr/bin/env python3
"""
Qwen embedding FastAPI service.

Features:
- POST /embed: text embeddings
- POST /models/preload: preload/download embedding model
- POST /models/unload: unload embedding model manually
- GET /health, GET /ready, GET /info

Env vars:
- EMBEDDING_MODEL_ID: default "Qwen/Qwen3-Embedding-0.6B"
- DEVICE: cpu/cuda/auto (default auto)
- TORCH_DTYPE: auto/float16/bfloat16/float32 (default auto)
- TORCH_NUM_THREADS: CPU worker threads (default min(2, cpu_count))
- TORCH_NUM_INTEROP_THREADS: CPU inter-op threads (default 1)
- MAX_BATCH_SIZE: default 1
- MAX_INPUT_LENGTH: default 1024
- MAX_CONCURRENT_REQUESTS: default 1
- IDLE_UNLOAD_SECONDS: unload model after idle seconds; 0 disables idle unload
- IDLE_CHECK_INTERVAL_SECONDS: idle unload sweep interval
- HOST: default 0.0.0.0
- PORT: default 8001
- PRELOAD_MODELS: true/false (default false)
"""

from __future__ import annotations

import gc
import logging
import os
import threading
import time
from datetime import datetime, timezone
from typing import Dict, List

import torch
import torch.nn.functional as F
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from transformers import AutoModel, AutoTokenizer


os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("qwen-embedding-fastapi")


def _resolve_device() -> str:
    configured = os.getenv("DEVICE", "auto").strip().lower()
    if configured in {"cpu", "cuda"}:
        if configured == "cuda" and not torch.cuda.is_available():
            logger.warning("DEVICE=cuda but CUDA not available, falling back to cpu")
            return "cpu"
        return configured
    return "cuda" if torch.cuda.is_available() else "cpu"


def _resolve_dtype(device: str) -> torch.dtype:
    configured = os.getenv("TORCH_DTYPE", "auto").strip().lower()
    if configured == "float32":
        return torch.float32
    if configured == "float16":
        return torch.float16
    if configured == "bfloat16":
        return torch.bfloat16

    if device == "cuda":
        return torch.float16
    return torch.float32


def _resolve_thread_count(env_name: str, default_value: int) -> int:
    configured = os.getenv(env_name, "").strip()
    if configured:
        try:
            return max(1, int(configured))
        except ValueError:
            logger.warning("Invalid %s=%s, using default %s", env_name, configured, default_value)
    return default_value


def _read_proc_status_kib(field_name: str) -> int | None:
    try:
        with open("/proc/self/status", "r", encoding="utf-8") as status_file:
            for line in status_file:
                if not line.startswith(f"{field_name}:"):
                    continue
                parts = line.split()
                if len(parts) < 2:
                    return None
                return int(parts[1])
    except (OSError, ValueError):
        return None
    return None


def _process_memory_snapshot() -> Dict[str, float]:
    snapshot: Dict[str, float] = {}
    rss_kib = _read_proc_status_kib("VmRSS")
    hwm_kib = _read_proc_status_kib("VmHWM")
    if rss_kib is not None:
        snapshot["rss_mb"] = round(rss_kib / 1024, 2)
    if hwm_kib is not None:
        snapshot["rss_high_water_mark_mb"] = round(hwm_kib / 1024, 2)
    return snapshot


def _format_timestamp(epoch_seconds: float | None) -> str | None:
    if epoch_seconds is None:
        return None
    return datetime.fromtimestamp(epoch_seconds, tz=timezone.utc).isoformat()


EMBEDDING_MODEL_ID = os.getenv("EMBEDDING_MODEL_ID", "Qwen/Qwen3-Embedding-0.6B")
DEVICE = _resolve_device()
TORCH_DTYPE = _resolve_dtype(DEVICE)
MAX_BATCH_SIZE = int(os.getenv("MAX_BATCH_SIZE", "1"))
MAX_INPUT_LENGTH = int(os.getenv("MAX_INPUT_LENGTH", "1024"))
MAX_CONCURRENT_REQUESTS = max(1, int(os.getenv("MAX_CONCURRENT_REQUESTS", "1")))
IDLE_UNLOAD_SECONDS = max(0, int(os.getenv("IDLE_UNLOAD_SECONDS", "900")))
IDLE_CHECK_INTERVAL_SECONDS = max(1, int(os.getenv("IDLE_CHECK_INTERVAL_SECONDS", "30")))
LOCAL_FILES_ONLY = os.getenv("HF_LOCAL_FILES_ONLY", "false").strip().lower() == "true"
CPU_COUNT = os.cpu_count() or 1
TORCH_NUM_THREADS = _resolve_thread_count("TORCH_NUM_THREADS", min(2, CPU_COUNT))
TORCH_NUM_INTEROP_THREADS = _resolve_thread_count("TORCH_NUM_INTEROP_THREADS", 1)

REQUEST_SEMAPHORE = threading.Semaphore(MAX_CONCURRENT_REQUESTS)
IDLE_UNLOAD_STOP = threading.Event()
IDLE_UNLOAD_THREAD: threading.Thread | None = None


def _configure_torch_runtime() -> None:
    if DEVICE != "cpu":
        return
    try:
        torch.set_num_threads(TORCH_NUM_THREADS)
    except RuntimeError as exc:
        logger.warning("Unable to set torch num threads: %s", exc)
    try:
        torch.set_num_interop_threads(TORCH_NUM_INTEROP_THREADS)
    except RuntimeError as exc:
        logger.warning("Unable to set torch interop threads: %s", exc)


_configure_torch_runtime()


class EmbeddingRequest(BaseModel):
    texts: List[str] = Field(..., min_length=1)
    normalize: bool = True


class EmbeddingResponse(BaseModel):
    embeddings: List[List[float]]
    model: str
    dimension: int
    batch_size: int
    processing_time_ms: float


class ModelRegistry:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.embedding_tokenizer = None
        self.embedding_model = None
        self.active_requests = 0
        self.last_access_monotonic = time.monotonic()
        self.last_request_at = None
        self.last_loaded_at = None
        self.last_unloaded_at = None
        self.last_unload_reason = None

    def _load_embedding_locked(self) -> bool:
        if self.embedding_model is not None and self.embedding_tokenizer is not None:
            return False
        logger.info("Loading embedding model: %s", EMBEDDING_MODEL_ID)
        tokenizer = AutoTokenizer.from_pretrained(
            EMBEDDING_MODEL_ID,
            trust_remote_code=True,
            local_files_only=LOCAL_FILES_ONLY,
        )
        model = AutoModel.from_pretrained(
            EMBEDDING_MODEL_ID,
            torch_dtype=TORCH_DTYPE,
            trust_remote_code=True,
            local_files_only=LOCAL_FILES_ONLY,
            low_cpu_mem_usage=(DEVICE == "cpu"),
        )
        if DEVICE == "cuda":
            model = model.to(DEVICE)
        model.eval()
        self.embedding_tokenizer = tokenizer
        self.embedding_model = model
        now = time.time()
        self.last_loaded_at = now
        self.last_request_at = now
        self.last_access_monotonic = time.monotonic()
        self.last_unloaded_at = None
        self.last_unload_reason = None
        logger.info("Embedding model ready on %s with memory=%s", DEVICE, _process_memory_snapshot())
        return True

    def load_embedding(self) -> bool:
        with self._lock:
            return self._load_embedding_locked()

    def acquire_for_request(self):
        with self._lock:
            self._load_embedding_locked()
            self.active_requests += 1
            now = time.time()
            self.last_request_at = now
            self.last_access_monotonic = time.monotonic()
            return self.embedding_tokenizer, self.embedding_model

    def release_after_request(self) -> None:
        with self._lock:
            self.active_requests = max(0, self.active_requests - 1)
            now = time.time()
            self.last_request_at = now
            self.last_access_monotonic = time.monotonic()

    def unload_embedding(self, reason: str) -> bool:
        with self._lock:
            if self.active_requests > 0:
                return False
            if self.embedding_model is None and self.embedding_tokenizer is None:
                return False
            self.embedding_model = None
            self.embedding_tokenizer = None
            self.last_unloaded_at = time.time()
            self.last_unload_reason = reason
        gc.collect()
        if DEVICE == "cuda":
            torch.cuda.empty_cache()
        logger.info("Embedding model unloaded. reason=%s memory=%s", reason, _process_memory_snapshot())
        return True

    def unload_if_idle(self, idle_seconds: int) -> bool:
        if idle_seconds <= 0:
            return False
        with self._lock:
            if self.active_requests > 0:
                return False
            if self.embedding_model is None or self.embedding_tokenizer is None:
                return False
            idle_for = time.monotonic() - self.last_access_monotonic
            if idle_for < idle_seconds:
                return False
        return self.unload_embedding(f"idle timeout ({idle_seconds}s)")

    def state_snapshot(self) -> Dict[str, object]:
        with self._lock:
            loaded = self.embedding_model is not None and self.embedding_tokenizer is not None
            return {
                "embedding_loaded": loaded,
                "active_requests": self.active_requests,
                "idle_unload_enabled": IDLE_UNLOAD_SECONDS > 0,
                "idle_unload_seconds": IDLE_UNLOAD_SECONDS,
                "idle_check_interval_seconds": IDLE_CHECK_INTERVAL_SECONDS,
                "last_access_age_seconds": round(max(0.0, time.monotonic() - self.last_access_monotonic), 2),
                "last_request_at": _format_timestamp(self.last_request_at),
                "last_loaded_at": _format_timestamp(self.last_loaded_at),
                "last_unloaded_at": _format_timestamp(self.last_unloaded_at),
                "last_unload_reason": self.last_unload_reason,
            }


registry = ModelRegistry()


def _mean_pool(last_hidden_state: torch.Tensor, attention_mask: torch.Tensor) -> torch.Tensor:
    mask = attention_mask.unsqueeze(-1).expand(last_hidden_state.size()).float()
    summed = torch.sum(last_hidden_state * mask, dim=1)
    count = torch.clamp(mask.sum(dim=1), min=1e-9)
    return summed / count


def _embedding_forward(texts: List[str], normalize: bool) -> List[List[float]]:
    with REQUEST_SEMAPHORE:
        tokenizer, model = registry.acquire_for_request()
        try:
            inputs = tokenizer(
                texts,
                padding=True,
                truncation=True,
                max_length=MAX_INPUT_LENGTH,
                return_tensors="pt",
            )
            inputs = {k: v.to(DEVICE) for k, v in inputs.items()}

            with torch.inference_mode():
                outputs = model(**inputs, return_dict=True)
                token_embeddings = getattr(outputs, "last_hidden_state", None)
                if token_embeddings is None:
                    hidden_states = getattr(outputs, "hidden_states", None)
                    if not hidden_states:
                        raise RuntimeError("Embedding model output missing last_hidden_state")
                    token_embeddings = hidden_states[-1]

                pooled = _mean_pool(token_embeddings, inputs["attention_mask"])
                if normalize:
                    pooled = F.normalize(pooled, p=2, dim=1)

            return pooled.cpu().tolist()
        finally:
            registry.release_after_request()


def _idle_unload_worker() -> None:
    while not IDLE_UNLOAD_STOP.wait(IDLE_CHECK_INTERVAL_SECONDS):
        try:
            registry.unload_if_idle(IDLE_UNLOAD_SECONDS)
        except Exception:  # noqa: BLE001
            logger.exception("Idle unload sweep failed")


app = FastAPI(
    title="Qwen Embedding Service",
    description="Embedding-only FastAPI service for local CPU deployment",
    version="3.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def _startup() -> None:
    global IDLE_UNLOAD_THREAD
    preload = os.getenv("PRELOAD_MODELS", "false").strip().lower() == "true"
    logger.info(
        "Service startup. device=%s dtype=%s preload_models=%s idle_unload_seconds=%s idle_check_interval_seconds=%s num_threads=%s interop_threads=%s max_batch_size=%s max_concurrent_requests=%s",
        DEVICE,
        TORCH_DTYPE,
        preload,
        IDLE_UNLOAD_SECONDS,
        IDLE_CHECK_INTERVAL_SECONDS,
        TORCH_NUM_THREADS,
        TORCH_NUM_INTEROP_THREADS,
        MAX_BATCH_SIZE,
        MAX_CONCURRENT_REQUESTS,
    )
    if DEVICE == "cpu" and TORCH_DTYPE == torch.float32:
        logger.warning(
            "CPU float32 embedding mode can keep more than 2GB RSS resident for %s; this is expected model footprint, not necessarily a leak",
            EMBEDDING_MODEL_ID,
        )
    if IDLE_UNLOAD_SECONDS > 0 and (IDLE_UNLOAD_THREAD is None or not IDLE_UNLOAD_THREAD.is_alive()):
        IDLE_UNLOAD_STOP.clear()
        IDLE_UNLOAD_THREAD = threading.Thread(
            target=_idle_unload_worker,
            name="idle-unload-worker",
            daemon=True,
        )
        IDLE_UNLOAD_THREAD.start()
    if preload:
        registry.load_embedding()


@app.on_event("shutdown")
def _shutdown() -> None:
    IDLE_UNLOAD_STOP.set()


@app.get("/health")
def health() -> Dict[str, object]:
    return {
        "status": "healthy",
        "device": DEVICE,
        "dtype": str(TORCH_DTYPE).replace("torch.", ""),
        "torch_num_threads": TORCH_NUM_THREADS,
        "torch_num_interop_threads": TORCH_NUM_INTEROP_THREADS,
        "max_concurrent_requests": MAX_CONCURRENT_REQUESTS,
        **registry.state_snapshot(),
        **_process_memory_snapshot(),
    }


@app.get("/ready")
def ready() -> Dict[str, object]:
    try:
        registry.load_embedding()
    except Exception as exc:  # noqa: BLE001
        logger.warning("Embedding readiness check failed", exc_info=exc)
        raise HTTPException(status_code=503, detail="embedding model not ready") from exc
    return {
        "status": "ready",
        "device": DEVICE,
        "dtype": str(TORCH_DTYPE).replace("torch.", ""),
        "torch_num_threads": TORCH_NUM_THREADS,
        "torch_num_interop_threads": TORCH_NUM_INTEROP_THREADS,
        "max_concurrent_requests": MAX_CONCURRENT_REQUESTS,
        **registry.state_snapshot(),
        **_process_memory_snapshot(),
    }


@app.get("/info")
def info() -> Dict[str, object]:
    return {
        "embedding_model": EMBEDDING_MODEL_ID,
        "device": DEVICE,
        "max_batch_size": MAX_BATCH_SIZE,
        "max_input_length": MAX_INPUT_LENGTH,
        "torch_num_threads": TORCH_NUM_THREADS,
        "torch_num_interop_threads": TORCH_NUM_INTEROP_THREADS,
        "max_concurrent_requests": MAX_CONCURRENT_REQUESTS,
        **registry.state_snapshot(),
        **_process_memory_snapshot(),
    }


@app.post("/models/preload")
def preload_models() -> Dict[str, object]:
    start = time.time()
    loaded = registry.load_embedding()
    elapsed = (time.time() - start) * 1000
    return {
        "status": "ok",
        "message": "embedding model loaded" if loaded else "embedding model already loaded",
        **registry.state_snapshot(),
        **_process_memory_snapshot(),
        "processing_time_ms": round(elapsed, 2),
    }


@app.post("/models/unload")
def unload_models() -> Dict[str, object]:
    unloaded = registry.unload_embedding("manual unload")
    return {
        "status": "ok",
        "unloaded": unloaded,
        **registry.state_snapshot(),
        **_process_memory_snapshot(),
    }


@app.post("/embed", response_model=EmbeddingResponse)
def embed(request: EmbeddingRequest) -> EmbeddingResponse:
    if len(request.texts) > MAX_BATCH_SIZE:
        raise HTTPException(
            status_code=400,
            detail=f"Batch too large. max={MAX_BATCH_SIZE}, got={len(request.texts)}",
        )

    start = time.time()
    try:
        vectors = _embedding_forward(request.texts, request.normalize)
    except Exception as exc:  # noqa: BLE001
        logger.exception("Embedding failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    elapsed = (time.time() - start) * 1000
    dimension = len(vectors[0]) if vectors else 0
    return EmbeddingResponse(
        embeddings=vectors,
        model=EMBEDDING_MODEL_ID,
        dimension=dimension,
        batch_size=len(request.texts),
        processing_time_ms=elapsed,
    )


if __name__ == "__main__":
    import uvicorn

    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8001"))
    uvicorn.run(app, host=host, port=port)
