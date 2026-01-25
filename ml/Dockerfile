FROM python:3.10-slim

WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# 复制依赖文件
COPY requirements.txt .

# 安装 Python 依赖
RUN pip install --no-cache-dir -r requirements.txt

# 复制服务代码
COPY embedding_service.py .

# 设置环境变量
ENV MODEL_NAME="Qwen/Qwen2.5-Embedding-0.6B-Instruct"
ENV HOST="0.0.0.0"
ENV PORT="8001"
ENV DEVICE="cpu"
ENV MAX_BATCH_SIZE="32"

# 暴露端口
EXPOSE 8001

# 启动服务
CMD ["python", "embedding_service.py"]
