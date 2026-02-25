import json
import time
from openai import OpenAI

client = OpenAI(api_key="sk-51bfa9c2b7874b7fb4b783606334ed69", base_url="https://dashscope.aliyuncs.com/compatible-mode/v1")

def get_resume_pair(seed=None):
    prompt = f"""
    请伪造一段互联网行业的简历片段（包含项目经历），并按照以下格式提取信息：
    {{
      "instruction": "提取简历中的技术栈、项目名称及核心职责",
      "input": "简历文本内容",
      "output": "{{\\"姓名\\": \\"...\\", \\"技术栈\\": [...], \\"项目\\": \\"...\\", \\"职责\\": \\"...\\"}}"
    }}
    要求：
    - 技术栈要具体（如 SpringBoot, Redis）
    - 项目职责要包含量化结果
    - 职位类型随机化
    - seed={seed} 用于多样性
    """
    completion = client.chat.completions.create(
        model="qwen-plus",
        messages=[{"role": "user", "content": prompt}]
    )
    content = completion.choices[0].message.content
    try:
        json_obj = json.loads(content)  # 确保格式正确
        return json_obj
    except json.JSONDecodeError:
        print("JSON 格式错误，跳过该条")
        return None

# 批量生成
data = []
for i in range(500):
    item = get_resume_pair(seed=i)
    if item:
        data.append(item)
    time.sleep(0.5)  # 避免频率过快
