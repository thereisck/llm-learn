"""最简MCP Server：提供文件列表和文件读取两个工具"""
import os
from mcp.server.fastmcp import FastMCP

# 创建MCP Server实例
mcp = FastMCP("filesystem-server")
# 定义工具1：列出目录下的文件
@mcp.tool()
def list_files(directory: str) -> str:
    """列出指定目录下的所有文件和子目录名称"""
    if not os.path.isdir(directory):
        return f"错误：目录 {directory} 不存在"
    items = os.listdir(directory)
    files = [i for i in items if os.path.isfile(os.path.join(directory, i))]
    dirs = [i for i in items if os.path.isdir(os.path.join(directory, i))]
    result = f"目录: {directory}\n文件({len(files)}个): {', '.join(files)}"
    if dirs:
        result += f"\n子目录({len(dirs)}个): {', '.join(dirs)}"
    return result

# 定义工具2：读取文件内容
@mcp.tool()
def read_file(filepath: str) -> str:
    """读取指定文件的文本内容，限制返回前500行"""
    if not os.path.isfile(filepath):
        return f"错误：文件 {filepath} 不存在"
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        lines = f.readlines()[:500]
    return ''.join(lines)

# 启动Server（stdio模式，Agent通过标准输入输出通信）
if __name__ == "__main__":
    mcp.run(transport="stdio")