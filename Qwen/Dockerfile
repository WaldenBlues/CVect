FROM python:3.10-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY embedding_service.py .

ENV EMBEDDING_MODEL_ID="Qwen/Qwen3-Embedding-0.6B"
ENV GENERATION_MODEL_ID="Qwen/Qwen3-0.6B"
ENV DEVICE="cpu"
ENV TORCH_DTYPE="auto"
ENV MAX_BATCH_SIZE="64"
ENV MAX_INPUT_LENGTH="8192"
ENV MAX_NEW_TOKENS_DEFAULT="256"
ENV PRELOAD_MODELS="false"
ENV HOST="0.0.0.0"
ENV PORT="8001"

EXPOSE 8001

CMD ["python", "embedding_service.py"]
