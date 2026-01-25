# ML Embedding Service

**Generated:** 2026-01-25
**Commit:** 69da5d4
**Branch:** main

## OVERVIEW
FastAPI service for Qwen text embeddings (768-dim). Integrated with CVect backend via HTTP.

## STRUCTURE
```
ml/
├── embedding_service.py  # FastAPI service (Qwen 0.6B)
├── Dockerfile           # Container build
└── requirements.txt     # Python dependencies
```

## API
### Endpoints
- `POST /embed` - Text to embeddings
  ```json
  {"texts": ["text1", "text2"]} → {"embeddings": [[...], [...]]}
  ```
- `GET /health` - Service status

## DEPLOYMENT
### Docker (Recommended)
```bash
# Build and run
docker build -t qwen-embedding:0.6b ./ml
docker run -p 8001:8001 qwen-embedding:0.6b

# With Docker Compose (from project root)
docker compose up embedding
```

### Local
```bash
cd ml
pip install -r requirements.txt
python embedding_service.py
```

## CONFIGURATION
| Variable | Default | Purpose |
|----------|---------|---------|
| `MODEL_NAME` | `Qwen/Qwen2.5-Embedding-0.6B-Instruct` | HuggingFace model |
| `DEVICE` | `cpu` (auto-detects CUDA) | `cpu` or `cuda` |
| `MAX_BATCH_SIZE` | `32` | Batch size limit |
| `PORT` | `8001` | Service port |

### Model Specs
- **Dimensions**: 768
- **Max input**: 8192 tokens
- **Precision**: FP16 (CUDA) / FP32 (CPU)

## INTEGRATION
### Backend Configuration
```yaml
# application.yml
app:
  embedding:
    model-name: Qwen/Qwen2.5-Embedding-0.6B-Instruct
    device: cpu
    batch-size: 32
```

### HTTP Client
- Backend uses `WebFlux` client to `http://localhost:8001/embed`
- Timeout: 30 seconds, retry logic

## PERFORMANCE
| Scenario | RAM | Speed |
|----------|-----|-------|
| **CPU** | 8GB+ | ~100ms/text |
| **CUDA** | 4GB VRAM | ~20ms/text |

## TROUBLESHOOTING
- **CUDA unavailable**: Set `DEVICE=cpu`
- **Out of memory**: Reduce `MAX_BATCH_SIZE` to 8 or 16
- **Test**: `curl -X POST http://localhost:8001/embed -d '{"texts":["test"]}'`