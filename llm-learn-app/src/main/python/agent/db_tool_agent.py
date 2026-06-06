import re
import json
import mysql.connector
from openai import OpenAI

# --- MySQL查询工具 ---
def query_database(sql: str) -> str:
    """执行SELECT查询语句，返回查询结果。只允许SELECT操作，禁止增删改。"""
    # 安全校验：只允许SELECT
    if not re.match(r'^\s*SELECT\s', sql, re.IGNORECASE):
        return "错误：只允许SELECT查询，禁止INSERT/UPDATE/DELETE/DROP"
    
    # 截断分号后的内容（防注入）
    sql = sql.split(';')[0].strip()
    try:
        conn = mysql.connector.connect(
            host="localhost",
            port=3306,
            user="root",
            password=os.environ.get("DB_PASSWORD", ""),
            database="llm_learn",
            charset="utf8mb4"
        )
        cursor = conn.cursor(dictionary=True)
        cursor.execute(sql)
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        
        if not rows:
            return "查询结果为空"
        
        return json.dumps(rows, ensure_ascii=False, default=str)
    
    except Exception as e:
        return f"查询出错：{e}"
    
# --- Tool定义（给LLM看的描述）---
tools = [
    {
        "type": "function",
        "function": {
            "name": "query_database",
            "description": "查询MySQL数据库llm_learn，执行SELECT语句获取数据。数据库有一个employees表，字段：id, name(姓名), department(部门), salary(薪资), hire_date(入职日期)",
            "parameters": {
                "type": "object",
                "properties": {
                    "sql": {
                        "type": "string",
                        "description": "要执行的SELECT SQL语句"
                    }
                },
                "required": ["sql"]
            }
        }
    }
]

tool_map = {
    "query_database": query_database,
}

SILICONFLOW_API_KEY = "os.environ.get("SILICONFLOW_API_KEY", "")"
SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1"

client = OpenAI(
    api_key=SILICONFLOW_API_KEY,
    base_url=SILICONFLOW_BASE_URL
)

MODEL = "Pro/zai-org/GLM-5.1"

# --- FC两轮闭环 ---
def chat_with_db(user_message: str) -> str:
    messages = [{"role": "user", "content": user_message}]
    response = client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=tools,
    )
    choice = response.choices[0]
    message = choice.message
    
    if not message.tool_calls:
        return message.content
    messages.append(message)
    for tool_call in message.tool_calls:
        func_name = tool_call.function.name
        func_args = json.loads(tool_call.function.arguments)
        
        print(f"[工具调用] {func_name}({func_args})")
        
        result = tool_map[func_name](**func_args)
        
        messages.append({
            "role": "tool",
            "tool_call_id": tool_call.id,
            "content": result,
        })
    
    response2 = client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=tools,
    )
    
    return response2.choices[0].message.content

if __name__ == "__main__":
    # 测试1：查数据
    print(chat_with_db("工程部有几个员工？平均薪资多少？"))