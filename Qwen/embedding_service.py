#!/usr/bin/env python3
"""
Qwen embedding FastAPI service.

Features:
- POST /embed: text embeddings
- POST /models/preload: preload/download embedding model
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
- HOST: default 0.0.0.0
- PORT: default 8001
- PRELOAD_MODELS: true/false (default false)
"""

from __future__ import annotations

import logging
import os
import threading
import time
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


EMBEDDING_MODEL_ID = os.getenv("EMBEDDING_MODEL_ID", "Qwen/Qwen3-Embedding-0.6B")
DEVICE = _resolve_device()
TORCH_DTYPE = _resolve_dtype(DEVICE)
MAX_BATCH_SIZE = int(os.getenv("MAX_BATCH_SIZE", "1"))
MAX_INPUT_LENGTH = int(os.getenv("MAX_INPUT_LENGTH", "1024"))
MAX_CONCURRENT_REQUESTS = max(1, int(os.getenv("MAX_CONCURRENT_REQUESTS", "1")))
LOCAL_FILES_ONLY = os.getenv("HF_LOCAL_FILES_ONLY", "false").strip().lower() == "true"
CPU_COUNT = os.cpu_count() or 1
TORCH_NUM_THREADS = _resolve_thread_count("TORCH_NUM_THREADS", min(2, CPU_COUNT))
TORCH_NUM_INTEROP_THREADS = _resolve_thread_count("TORCH_NUM_INTEROP_THREADS", 1)

REQUEST_SEMAPHORE = threading.Semaphore(MAX_CONCURRENT_REQUESTS)


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

    def load_embedding(self) -> None:
        if self.embedding_model is not None and self.embedding_tokenizer is not None:
            return
        with self._lock:
            if self.embedding_model is not None and self.embedding_tokenizer is not None:
                return
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
            logger.info("Embedding model ready on %s", DEVICE)


registry = ModelRegistry()


def _mean_pool(last_hidden_state: torch.Tensor, attention_mask: torch.Tensor) -> torch.Tensor:
    mask = attention_mask.unsqueeze(-1).expand(last_hidden_state.size()).float()
    summed = torch.sum(last_hidden_state * mask, dim=1)
    count = torch.clamp(mask.sum(dim=1), min=1e-9)
    return summed / count


def _embedding_forward(texts: List[str], normalize: bool) -> List[List[float]]:
    registry.load_embedding()
    tokenizer = registry.embedding_tokenizer
    model = registry.embedding_model

    with REQUEST_SEMAPHORE:
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
    preload = os.getenv("PRELOAD_MODELS", "false").strip().lower() == "true"
    logger.info(
        "Service startup. device=%s dtype=%s preload_models=%s num_threads=%s interop_threads=%s max_batch_size=%s max_concurrent_requests=%s",
        DEVICE,
        TORCH_DTYPE,
        preload,
        TORCH_NUM_THREADS,
        TORCH_NUM_INTEROP_THREADS,
        MAX_BATCH_SIZE,
        MAX_CONCURRENT_REQUESTS,
    )
    if preload:
        registry.load_embedding()


@app.get("/health")
def health() -> Dict[str, object]:
    return {
        "status": "healthy",
        "device": DEVICE,
        "dtype": str(TORCH_DTYPE).replace("torch.", ""),
        "embedding_loaded": registry.embedding_model is not None,
        "torch_num_threads": TORCH_NUM_THREADS,
        "torch_num_interop_threads": TORCH_NUM_INTEROP_THREADS,
        "max_concurrent_requests": MAX_CONCURRENT_REQUESTS,
    }


@app.get("/ready")
def ready() -> Dict[str, object]:
    start = time.time()
    try:
        registry.load_embedding()
    except Exception as exc:  # noqa: BLE001
        logger.exception("Readiness probe failed")
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    elapsed = (time.time() - start) * 1000
    return {
        "status": "ready",
        "device": DEVICE,
        "dtype": str(TORCH_DTYPE).replace("torch.", ""),
        "embedding_loaded": registry.embedding_model is not None,
        "torch_num_threads": TORCH_NUM_THREADS,
        "torch_num_interop_threads": TORCH_NUM_INTEROP_THREADS,
        "max_concurrent_requests": MAX_CONCURRENT_REQUESTS,
        "processing_time_ms": round(elapsed, 2),
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
    }


@app.post("/models/preload")
def preload_models() -> Dict[str, object]:
    start = time.time()
    registry.load_embedding()
    elapsed = (time.time() - start) * 1000
    return {
        "status": "ok",
        "message": "embedding model loaded",
        "processing_time_ms": round(elapsed, 2),
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
