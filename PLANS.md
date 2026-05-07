## Active Plan

### Configurable File Storage

1. Inspect the current upload, queue worker, and resume parsing flow to locate direct filesystem assumptions.
2. Introduce a storage abstraction with conditional local and S3-compatible implementations.
3. Keep `upload_items.storage_path` unchanged at the schema level, but treat it as an opaque storage key instead of an absolute local path.
4. Rewire upload and queue processing to persist and load files through the storage abstraction while preserving current APIs and retry behavior.
5. Add focused regression tests for local storage operations and storage-backed upload processing, then document local and MinIO/S3 usage.
