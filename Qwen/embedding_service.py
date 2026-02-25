#!/usr/bin/env python3
"""
Qwen Embedding Service - FastAPI 服务

启动方式:
    pip install fastapi uvicorn transformers torch accelerate
    python embedding_service.py

或使用 Docker:
    docker run -p 8001:8001 qwen-embedding:0.6b

API:
    POST /embed
    Body: {"texts": ["text1", "text2", ...]}
    Response: {"embeddings": [[0.1, 0.2, ...], [0.3, 0.4, ...]]}
"""

import os
import torch
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from transformers import AutoTokenizer, AutoModelForCausalLM
import numpy as np
from typing import List, Optional
import time
import logging

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 环境变量配置
MODEL_NAME = os.getenv("MODEL_NAME", "Qwen/Qwen2.5-Embedding-0.6B-Instruct")
DEVICE = os.getenv("DEVICE", "cuda" if torch.cuda.is_available() else "cpu")
MAX_BATCH_SIZE = int(os.getenv("MAX_BATCH_SIZE", "32"))

print(f"Loading model: {MODEL_NAME}")
print(f"Device: {DEVICE}")

# 加载模型和分词器
tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME, trust_remote_code=True)
model = AutoModelForCausalLM.from_pretrained(
    MODEL_NAME,
    torch_dtype=torch.float16 if DEVICE == "cuda" else torch.float32,
    device_map=DEVICE if DEVICE == "cuda" else None,
    trust_remote_code=True
)
model.eval()

print(f"Model loaded successfully on {DEVICE}")

# 创建 FastAPI 应用
app = FastAPI(
    title="Qwen Embedding Service",
    description="基于 Qwen2.5-Embedding-0.6B-Instruct 的向量生成服务",
    version="1.0.0"
)

# CORS 配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# 请求/响应模型
class EmbeddingRequest(BaseModel):
    texts: List[str]
    normalize: bool = True


class EmbeddingResponse(BaseModel):
    embeddings: List[List[float]]
    model: str
    dimension: int
    batch_size: int
    processing_time_ms: float


def get_embedding(text: str) -> List[float]:
    """获取单个文本的 embedding"""
    # 构造 embedding 任务的 prompt
    prompt = f"Represent this text for retrieval: {text}"

    # 编码
    inputs = tokenizer(prompt, return_tensors="pt", truncation=True, max_length=8192)
    inputs = {k: v.to(DEVICE) for k, v in inputs.items()}

    # 生成 embedding (使用 last hidden state 的 mean pooling)
    with torch.no_grad():
        outputs = model(**inputs)
        hidden_states = outputs.last_hidden_state
        # Mean pooling
        attention_mask = inputs["attention_mask"]
        token_embeddings = hidden_states
        input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
        embedding = torch.sum(token_embeddings * input_mask_expanded, 1) / torch.clamp(
            input_mask_expanded.sum(1), min=1e-9
        )

        # L2 归一化
        if True:  # normalize
            embedding = torch.nn.functional.normalize(embedding, p=2, dim=1)

    return embedding[0].cpu().tolist()


@app.post("/embed", response_model=EmbeddingResponse)
async def embed_texts(request: EmbeddingRequest):
    """批量生成文本 embedding"""
    start_time = time.time()

    if not request.texts:
        raise HTTPException(status_code=400, detail="texts list cannot be empty")

    if len(request.texts) > MAX_BATCH_SIZE:
        raise HTTPException(
            status_code=400,
            detail=f"Batch size too large. Max: {MAX_BATCH_SIZE}, Got: {len(request.texts)}"
        )

    try:
        embeddings = []
        for text in request.texts:
            embedding = get_embedding(text)
            embeddings.append(embedding)

        processing_time = (time.time() - start_time) * 1000

        logger.info(f"Processed {len(request.texts)} texts in {processing_time:.2f}ms")

        return EmbeddingResponse(
            embeddings=embeddings,
            model=MODEL_NAME,
            dimension=len(embeddings[0]) if embeddings else 0,
            batch_size=len(request.texts),
            processing_time_ms=processing_time
        )

    except Exception as e:
        logger.error(f"Error generating embeddings: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
async def health_check():
    """健康检查"""
    return {
        "status": "healthy",
        "model": MODEL_NAME,
        "device": DEVICE,
        "batch_size": MAX_BATCH_SIZE
    }


@app.get("/info")
async def model_info():
    """模型信息"""
    return {
        "model_name": MODEL_NAME,
        "device": DEVICE,
        "max_batch_size": MAX_BATCH_SIZE,
        "max_input_length": 8192,
        "embedding_dimension": 768,
        "truncation": True
    }


if __name__ == "__main__":
    import uvicorn

    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8001"))

    print(f"\n{'='*60}")
    print(f"Qwen Embedding Service started")
    print(f"API: http://{host}:{port}/embed")
    print(f"Health: http://{host}:{port}/health")
    print(f"{'='*60}\n")

    uvicorn.run(app, host=host, port=port)
