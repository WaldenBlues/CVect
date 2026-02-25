import torch

# 1. 检查显卡是否支持 BF16 (4060 是支持的)
support_bf16 = torch.cuda.is_bf16_supported()




print(f"--- 严谨性核验报告 ---")
print(f"1. PyTorch 显卡关联: {'成功' if torch.cuda.is_available() else '失败'}")
print(f"2. 4060 BF16 支持: {'支持' if support_bf16 else '不支持'}")
print(f"显存总量: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.2f} GB")
print(f"当前占用: {torch.cuda.memory_allocated()/1024**3:.2f} GB")
props = torch.cuda.get_device_properties(0)
support_fp16 = props.major >= 7  # 7.x 及以上架构支持 FP16
print(f"FP16 支持: {'支持' if support_fp16 else '不支持'}")
print(f"FP16 支持: {'支持' if support_fp16 else '不支持'}")
print(f"PyTorch 版本: {torch.__version__}")
print(f"CUDA 版本: {torch.version.cuda}")
import multiprocessing
print(f"CPU 核心数: {multiprocessing.cpu_count()}")

  