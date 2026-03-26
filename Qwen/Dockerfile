ARG PYTHON_BASE_IMAGE=python:3.10-slim

FROM ${PYTHON_BASE_IMAGE}

ARG DEBIAN_APT_MIRROR
ARG PIP_INDEX_URL
ARG PIP_TRUSTED_HOST

WORKDIR /app

RUN if [ -n "${DEBIAN_APT_MIRROR}" ]; then \
      if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i "s|http://deb.debian.org/debian|${DEBIAN_APT_MIRROR}|g; s|https://deb.debian.org/debian|${DEBIAN_APT_MIRROR}|g; s|http://security.debian.org/debian-security|${DEBIAN_APT_MIRROR}-security|g; s|https://security.debian.org/debian-security|${DEBIAN_APT_MIRROR}-security|g" /etc/apt/sources.list.d/debian.sources; \
      elif [ -f /etc/apt/sources.list ]; then \
        sed -i "s|http://deb.debian.org/debian|${DEBIAN_APT_MIRROR}|g; s|https://deb.debian.org/debian|${DEBIAN_APT_MIRROR}|g; s|http://security.debian.org/debian-security|${DEBIAN_APT_MIRROR}-security|g; s|https://security.debian.org/debian-security|${DEBIAN_APT_MIRROR}-security|g" /etc/apt/sources.list; \
      fi; \
    fi \
    && apt-get update \
    && apt-get install -y --no-install-recommends gcc \
    && rm -rf /var/lib/apt/lists/*

COPY requirements-service.txt .
RUN if [ -n "${PIP_INDEX_URL}" ]; then \
      pip config set global.index-url "${PIP_INDEX_URL}"; \
    fi \
    && if [ -n "${PIP_TRUSTED_HOST}" ]; then \
      pip config set global.trusted-host "${PIP_TRUSTED_HOST}"; \
    fi \
    && pip install --no-cache-dir -r requirements-service.txt

COPY embedding_service.py .

ENV EMBEDDING_MODEL_ID="Qwen/Qwen3-Embedding-0.6B"
ENV DEVICE="cpu"
ENV TORCH_DTYPE="auto"
ENV TORCH_NUM_THREADS="1"
ENV TORCH_NUM_INTEROP_THREADS="1"
ENV OMP_NUM_THREADS="1"
ENV MKL_NUM_THREADS="1"
ENV MAX_CONCURRENT_REQUESTS="1"
ENV MALLOC_ARENA_MAX="2"
ENV TOKENIZERS_PARALLELISM="false"
ENV MAX_BATCH_SIZE="1"
ENV MAX_INPUT_LENGTH="1024"
ENV PRELOAD_MODELS="false"
ENV HOST="0.0.0.0"
ENV PORT="8001"

EXPOSE 8001

CMD ["python", "embedding_service.py"]
