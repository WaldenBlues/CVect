import contextlib
import importlib.util
import os
import sys
import types
import unittest
from pathlib import Path


def _install_stubs() -> None:
    torch_module = types.ModuleType("torch")
    torch_module.float16 = object()
    torch_module.float32 = object()
    torch_module.bfloat16 = object()

    class _Cuda:
        @staticmethod
        def is_available() -> bool:
            return False

        @staticmethod
        def empty_cache() -> None:
            return None

    @contextlib.contextmanager
    def inference_mode():
        yield

    def _noop(*args, **kwargs):
        return None

    torch_module.cuda = _Cuda()
    torch_module.inference_mode = inference_mode
    torch_module.set_num_threads = _noop
    torch_module.set_num_interop_threads = _noop

    torch_nn_module = types.ModuleType("torch.nn")
    torch_nn_functional_module = types.ModuleType("torch.nn.functional")
    torch_nn_functional_module.normalize = lambda value, *args, **kwargs: value
    torch_nn_module.functional = torch_nn_functional_module
    torch_module.nn = torch_nn_module

    fastapi_module = types.ModuleType("fastapi")

    class HTTPException(Exception):
        def __init__(self, status_code: int, detail: str):
            super().__init__(detail)
            self.status_code = status_code
            self.detail = detail

    class FastAPI:
        def __init__(self, *args, **kwargs):
            self.args = args
            self.kwargs = kwargs
            self.middleware_calls = []

        def add_middleware(self, *args, **kwargs):
            self.middleware_calls.append((args, kwargs))
            return None

        def on_event(self, *args, **kwargs):
            def decorator(func):
                return func

            return decorator

        def get(self, *args, **kwargs):
            def decorator(func):
                return func

            return decorator

        def post(self, *args, **kwargs):
            def decorator(func):
                return func

            return decorator

    fastapi_module.FastAPI = FastAPI
    fastapi_module.HTTPException = HTTPException

    middleware_module = types.ModuleType("fastapi.middleware")
    cors_module = types.ModuleType("fastapi.middleware.cors")

    class CORSMiddleware:
        pass

    cors_module.CORSMiddleware = CORSMiddleware
    middleware_module.cors = cors_module

    pydantic_module = types.ModuleType("pydantic")

    class BaseModel:
        pass

    def Field(default=None, **kwargs):
        return default

    pydantic_module.BaseModel = BaseModel
    pydantic_module.Field = Field

    transformers_module = types.ModuleType("transformers")

    class AutoTokenizer:
        @staticmethod
        def from_pretrained(*args, **kwargs):
            return object()

    class AutoModel:
        @staticmethod
        def from_pretrained(*args, **kwargs):
            class Model:
                def eval(self):
                    return self

                def to(self, device):
                    return self

            return Model()

    transformers_module.AutoTokenizer = AutoTokenizer
    transformers_module.AutoModel = AutoModel

    sys.modules["torch"] = torch_module
    sys.modules["torch.nn"] = torch_nn_module
    sys.modules["torch.nn.functional"] = torch_nn_functional_module
    sys.modules["fastapi"] = fastapi_module
    sys.modules["fastapi.middleware"] = middleware_module
    sys.modules["fastapi.middleware.cors"] = cors_module
    sys.modules["pydantic"] = pydantic_module
    sys.modules["transformers"] = transformers_module


def _load_module():
    _install_stubs()
    module_path = Path(__file__).resolve().parents[1] / "embedding_service.py"
    spec = importlib.util.spec_from_file_location("qwen_embedding_service", module_path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def _load_module_with_env(**env_overrides):
    previous = {key: os.environ.get(key) for key in env_overrides}
    try:
        for key, value in env_overrides.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value
        return _load_module()
    finally:
        for key, value in previous.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


class FakeRegistry:
    def __init__(self, *, loaded=False, load_error=None):
        self.loaded = loaded
        self.load_error = load_error
        self.load_calls = 0

    def load_embedding(self):
        self.load_calls += 1
        if self.load_error is not None:
            raise self.load_error
        self.loaded = True
        return True

    def state_snapshot(self):
        return {
            "embedding_loaded": self.loaded,
            "active_requests": 0,
            "idle_unload_enabled": False,
            "idle_unload_seconds": 0,
            "idle_check_interval_seconds": 0,
            "last_access_age_seconds": 0.0,
            "last_request_at": None,
            "last_loaded_at": None,
            "last_unloaded_at": None,
            "last_unload_reason": None,
        }


class EmbeddingServiceReadinessTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = _load_module()

    def test_health_remains_liveness_only(self):
        registry = FakeRegistry(loaded=False)
        self.module.registry = registry

        response = self.module.health()

        self.assertEqual("healthy", response["status"])
        self.assertFalse(response["embedding_loaded"])
        self.assertEqual(0, registry.load_calls)

    def test_ready_loads_model_when_available(self):
        registry = FakeRegistry(loaded=False)
        self.module.registry = registry

        response = self.module.ready()

        self.assertEqual("ready", response["status"])
        self.assertTrue(response["embedding_loaded"])
        self.assertEqual(1, registry.load_calls)

    def test_ready_returns_503_when_model_cannot_load(self):
        registry = FakeRegistry(loaded=False, load_error=RuntimeError("boom"))
        self.module.registry = registry

        with self.assertRaises(self.module.HTTPException) as ctx:
            self.module.ready()

        self.assertEqual(503, ctx.exception.status_code)
        self.assertEqual("embedding model not ready", ctx.exception.detail)
        self.assertEqual(1, registry.load_calls)


class EmbeddingServiceCorsTest(unittest.TestCase):
    def test_cors_defaults_to_no_browser_origins(self):
        module = _load_module_with_env(CORS_ALLOW_ORIGINS="")

        middleware_args, middleware_kwargs = module.app.middleware_calls[0]

        self.assertEqual("CORSMiddleware", middleware_args[0].__name__)
        self.assertEqual([], middleware_kwargs["allow_origins"])
        self.assertFalse(middleware_kwargs["allow_credentials"])

    def test_cors_allows_explicit_origins(self):
        module = _load_module_with_env(
            CORS_ALLOW_ORIGINS="https://frontend.example, http://localhost:5173"
        )

        middleware_args, middleware_kwargs = module.app.middleware_calls[0]

        self.assertEqual("CORSMiddleware", middleware_args[0].__name__)
        self.assertEqual(
            ["https://frontend.example", "http://localhost:5173"],
            middleware_kwargs["allow_origins"],
        )
        self.assertTrue(middleware_kwargs["allow_credentials"])


class EmbeddingServiceEmbedErrorTest(unittest.TestCase):
    def test_embed_hides_internal_exception_details(self):
        module = _load_module()

        def boom(texts, normalize):
            raise RuntimeError("database password = secret")

        module._embedding_forward = boom

        with self.assertRaises(module.HTTPException) as ctx:
            module.embed(types.SimpleNamespace(texts=["hello"], normalize=True))

        self.assertEqual(500, ctx.exception.status_code)
        self.assertEqual("embedding request failed", ctx.exception.detail)


if __name__ == "__main__":
    unittest.main()
