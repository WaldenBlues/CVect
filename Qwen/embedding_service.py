#!/usr/bin/env python3
"""
Unified Qwen FastAPI service

Features:
- POST /embed: text embeddings
- POST /generate: text generation
- POST /models/preload: preload/download models
- GET /health, GET /ready, GET /info

Env vars:
- EMBEDDING_MODEL_ID: default "Qwen/Qwen3-Embedding-0.6B"
- GENERATION_MODEL_ID: default "Qwen/Qwen3-0.6B"
- DEVICE: cpu/cuda/auto (default auto)
- TORCH_DTYPE: auto/float16/bfloat16/float32 (default auto)
- MAX_BATCH_SIZE: default 16
- MAX_INPUT_LENGTH: default 8192
- MAX_NEW_TOKENS_DEFAULT: default 256
- HOST: default 0.0.0.0
- PORT: default 8001
- PRELOAD_MODELS: true/false (default false)
- READINESS_REQUIRE_GENERATION: true/false (default false)
"""

from __future__ import annotations

import logging
import os
import threading
import time
from typing import Dict, List, Optional

import torch
import torch.nn.functional as F
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from transformers import AutoModel, AutoModelForCausalLM, AutoTokenizer


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("qwen-fastapi")


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


EMBEDDING_MODEL_ID = os.getenv("EMBEDDING_MODEL_ID", "Qwen/Qwen3-Embedding-0.6B")
GENERATION_MODEL_ID = os.getenv("GENERATION_MODEL_ID", "Qwen/Qwen3-0.6B")
DEVICE = _resolve_device()
TORCH_DTYPE = _resolve_dtype(DEVICE)
MAX_BATCH_SIZE = int(os.getenv("MAX_BATCH_SIZE", "16"))
MAX_INPUT_LENGTH = int(os.getenv("MAX_INPUT_LENGTH", "8192"))
MAX_NEW_TOKENS_DEFAULT = int(os.getenv("MAX_NEW_TOKENS_DEFAULT", "256"))
LOCAL_FILES_ONLY = os.getenv("HF_LOCAL_FILES_ONLY", "false").strip().lower() == "true"
READINESS_REQUIRE_GENERATION = (
    os.getenv("READINESS_REQUIRE_GENERATION", "false").strip().lower() == "true"
)


class EmbeddingRequest(BaseModel):
    texts: List[str] = Field(..., min_length=1)
    normalize: bool = True


class EmbeddingResponse(BaseModel):
    embeddings: List[List[float]]
    model: str
    dimension: int
    batch_size: int
    processing_time_ms: float


class GenerateRequest(BaseModel):
    prompt: str = Field(..., min_length=1)
    max_new_tokens: int = Field(default=MAX_NEW_TOKENS_DEFAULT, ge=1, le=2048)
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    top_p: float = Field(default=0.9, ge=0.0, le=1.0)
    do_sample: bool = True


class GenerateResponse(BaseModel):
    text: str
    model: str
    prompt_tokens: int
    completion_tokens: int
    processing_time_ms: float


class ModelRegistry:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.embedding_tokenizer = None
        self.embedding_model = None
        self.generation_tokenizer = None
        self.generation_model = None

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
            )
            if DEVICE == "cuda":
                model = model.to(DEVICE)
            model.eval()
            self.embedding_tokenizer = tokenizer
            self.embedding_model = model
            logger.info("Embedding model ready on %s", DEVICE)

    def load_generation(self) -> None:
        if self.generation_model is not None and self.generation_tokenizer is not None:
            return
        with self._lock:
            if self.generation_model is not None and self.generation_tokenizer is not None:
                return
            logger.info("Loading generation model: %s", GENERATION_MODEL_ID)
            tokenizer = AutoTokenizer.from_pretrained(
                GENERATION_MODEL_ID,
                trust_remote_code=True,
                local_files_only=LOCAL_FILES_ONLY,
            )
            model = AutoModelForCausalLM.from_pretrained(
                GENERATION_MODEL_ID,
                torch_dtype=TORCH_DTYPE,
                trust_remote_code=True,
                local_files_only=LOCAL_FILES_ONLY,
            )
            if DEVICE == "cuda":
                model = model.to(DEVICE)
            model.eval()
            self.generation_tokenizer = tokenizer
            self.generation_model = model
            logger.info("Generation model ready on %s", DEVICE)


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

    inputs = tokenizer(
        texts,
        padding=True,
        truncation=True,
        max_length=MAX_INPUT_LENGTH,
        return_tensors="pt",
    )
    inputs = {k: v.to(DEVICE) for k, v in inputs.items()}

    with torch.no_grad():
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


def _generation_forward(request: GenerateRequest) -> Dict[str, object]:
    registry.load_generation()
    tokenizer = registry.generation_tokenizer
    model = registry.generation_model

    encoded = tokenizer(
        request.prompt,
        truncation=True,
        max_length=MAX_INPUT_LENGTH,
        return_tensors="pt",
    )
    encoded = {k: v.to(DEVICE) for k, v in encoded.items()}
    prompt_len = encoded["input_ids"].shape[1]

    with torch.no_grad():
        generated = model.generate(
            **encoded,
            max_new_tokens=request.max_new_tokens,
            temperature=request.temperature,
            top_p=request.top_p,
            do_sample=request.do_sample,
            pad_token_id=tokenizer.eos_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )

    completion_ids = generated[0][prompt_len:]
    text = tokenizer.decode(completion_ids, skip_special_tokens=True)
    if not text.strip():
        text = tokenizer.decode(generated[0], skip_special_tokens=True)
    completion_tokens = max(0, generated[0].shape[0] - prompt_len)
    return {
        "text": text,
        "prompt_tokens": int(prompt_len),
        "completion_tokens": int(completion_tokens),
    }


app = FastAPI(
    title="Qwen Unified Service",
    description="Embedding + Generation FastAPI service",
    version="2.0.0",
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
        "Service startup. device=%s dtype=%s preload_models=%s", DEVICE, TORCH_DTYPE, preload
    )
    if preload:
        registry.load_embedding()
        registry.load_generation()


@app.get("/health")
def health() -> Dict[str, object]:
    return {
        "status": "healthy",
        "device": DEVICE,
        "dtype": str(TORCH_DTYPE).replace("torch.", ""),
        "embedding_loaded": registry.embedding_model is not None,
        "generation_loaded": registry.generation_model is not None,
    }


@app.get("/ready")
def ready() -> Dict[str, object]:
    start = time.time()
    try:
        registry.load_embedding()
        generation_ready = registry.generation_model is not None
        if READINESS_REQUIRE_GENERATION:
            registry.load_generation()
            generation_ready = True
    except Exception as exc:  # noqa: BLE001
        logger.exception("Readiness probe failed")
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    elapsed = (time.time() - start) * 1000
    return {
        "status": "ready",
        "device": DEVICE,
        "dtype": str(TORCH_DTYPE).replace("torch.", ""),
        "embedding_loaded": registry.embedding_model is not None,
        "generation_loaded": generation_ready,
        "generation_required": READINESS_REQUIRE_GENERATION,
        "processing_time_ms": round(elapsed, 2),
    }


@app.get("/info")
def info() -> Dict[str, object]:
    return {
        "embedding_model": EMBEDDING_MODEL_ID,
        "generation_model": GENERATION_MODEL_ID,
        "device": DEVICE,
        "max_batch_size": MAX_BATCH_SIZE,
        "max_input_length": MAX_INPUT_LENGTH,
        "max_new_tokens_default": MAX_NEW_TOKENS_DEFAULT,
    }


@app.post("/models/preload")
def preload_models() -> Dict[str, object]:
    start = time.time()
    registry.load_embedding()
    registry.load_generation()
    elapsed = (time.time() - start) * 1000
    return {
        "status": "ok",
        "message": "models loaded",
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


@app.post("/generate", response_model=GenerateResponse)
def generate(request: GenerateRequest) -> GenerateResponse:
    start = time.time()
    try:
        out = _generation_forward(request)
    except Exception as exc:  # noqa: BLE001
        logger.exception("Generation failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    elapsed = (time.time() - start) * 1000
    return GenerateResponse(
        text=out["text"],
        model=GENERATION_MODEL_ID,
        prompt_tokens=out["prompt_tokens"],
        completion_tokens=out["completion_tokens"],
        processing_time_ms=elapsed,
    )


if __name__ == "__main__":
    import uvicorn

    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8001"))
    uvicorn.run(app, host=host, port=port)
