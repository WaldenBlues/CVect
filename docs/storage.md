# File Storage Modes

CVect backend now supports two file storage modes behind the same `FileStorageService` abstraction:

- `local`: store uploaded resume payloads on the local filesystem
- `s3` or `minio`: store uploaded resume payloads in an S3-compatible object store

The upload API, queue worker, and resume parsing flow stay unchanged at the HTTP level. `upload_items.storage_path` is still used, but it now stores an opaque storage key instead of a local absolute path.

## Configuration

All storage options live under `app.storage`:

| Property | Default | Description |
| --- | --- | --- |
| `app.storage.type` | `local` | Storage backend type. Supported values: `local`, `s3`, `minio`. |
| `app.storage.local-root` | `/storage` | Root directory for local mode. |
| `app.storage.endpoint` | empty | S3 endpoint override. Set this for MinIO or any custom S3-compatible service. |
| `app.storage.region` | `us-east-1` | AWS region or placeholder region for S3-compatible services. |
| `app.storage.bucket` | empty | Target bucket name for object storage mode. |
| `app.storage.access-key` | empty | Object storage access key. |
| `app.storage.secret-key` | empty | Object storage secret key. |
| `app.storage.path-style-access` | `true` | Enables path-style S3 requests. Keep `true` for MinIO. |
| `app.storage.auto-create-bucket` | `true` | Auto-create the bucket on startup when it is missing. |

Environment variable mapping:

| Property | Environment variable |
| --- | --- |
| `app.storage.type` | `CVECT_STORAGE_TYPE` |
| `app.storage.local-root` | `CVECT_STORAGE_LOCAL_ROOT` |
| `app.storage.endpoint` | `CVECT_STORAGE_ENDPOINT` |
| `app.storage.region` | `CVECT_STORAGE_REGION` |
| `app.storage.bucket` | `CVECT_STORAGE_BUCKET` |
| `app.storage.access-key` | `CVECT_STORAGE_ACCESS_KEY` |
| `app.storage.secret-key` | `CVECT_STORAGE_SECRET_KEY` |
| `app.storage.path-style-access` | `CVECT_STORAGE_PATH_STYLE_ACCESS` |
| `app.storage.auto-create-bucket` | `CVECT_STORAGE_AUTO_CREATE_BUCKET` |

## Local Mode

Local mode is the default. No extra object storage service is required.

Example:

```bash
cd backend/cvect
CVECT_STORAGE_TYPE=local \
CVECT_STORAGE_LOCAL_ROOT=/tmp/cvect-storage \
./mvnw spring-boot:run
```

Expected behavior:

- uploaded resume payloads are saved under `CVECT_STORAGE_LOCAL_ROOT`
- queue worker reads the payload back through `FileStorageService`
- after processing, the stored payload is reconciled to a content-hash key to preserve dedupe behavior

## MinIO / S3-Compatible Mode

`docker-compose.yml` now includes a `minio` service and publishes:

- S3 API: `9000`
- Console: `9001`

Example with the local compose stack:

```bash
CVECT_STORAGE_TYPE=minio \
CVECT_STORAGE_ENDPOINT=http://minio:9000 \
CVECT_STORAGE_BUCKET=cvect-resumes \
CVECT_STORAGE_ACCESS_KEY=minioadmin \
CVECT_STORAGE_SECRET_KEY=minioadmin \
docker compose --env-file .env -f docker-compose.yml up -d minio postgres backend frontend
```

For AWS S3, switch the same backend to:

```bash
CVECT_STORAGE_TYPE=s3
CVECT_STORAGE_ENDPOINT=
CVECT_STORAGE_REGION=<aws-region>
CVECT_STORAGE_BUCKET=<bucket-name>
CVECT_STORAGE_ACCESS_KEY=<access-key>
CVECT_STORAGE_SECRET_KEY=<secret-key>
CVECT_STORAGE_PATH_STYLE_ACCESS=false
```

## Verify Uploaded Files Reach MinIO

### Option 1: MinIO Console

1. Start the stack with `CVECT_STORAGE_TYPE=minio`.
2. Open `http://127.0.0.1:9001`.
3. Log in with `CVECT_MINIO_ROOT_USER` / `CVECT_MINIO_ROOT_PASSWORD` or the default `minioadmin` / `minioadmin`.
4. Confirm the bucket from `CVECT_STORAGE_BUCKET` exists.
5. Upload a resume through `/api/uploads/resumes` or the frontend.
6. Refresh the bucket contents and confirm a new object appears.
7. After processing completes, confirm the temporary object is replaced or reconciled to a hash-like object key.

### Option 2: AWS CLI against MinIO

```bash
aws --endpoint-url http://127.0.0.1:9000 \
  --region us-east-1 \
  s3 ls s3://cvect-resumes
```

After uploading a resume, run:

```bash
aws --endpoint-url http://127.0.0.1:9000 \
  --region us-east-1 \
  s3 ls s3://cvect-resumes --recursive
```

You should see a new object key. Once the queue worker finishes, the key should settle to the content hash used by dedupe.

## Test Coverage

Automated coverage for the storage module currently includes:

- local `save`
- local `load`
- local `exists`
- local `delete`
- local path traversal rejection

S3-compatible mode is wired for real runtime use, but object-store integration tests are not automated in this repository yet. Use the manual verification steps above against MinIO.
