import subprocess
import sys

# 尝试运行Chroma服务器
try:
    # 尝试使用python -m chromadb来运行
    result = subprocess.run(
        [sys.executable, "-m", "chromadb", "run", "--host", "0.0.0.0", "--port", "8000"],
        capture_output=True,
        text=True
    )
    print("Chroma server output:")
    print(result.stdout)
    if result.stderr:
        print("Error:")
        print(result.stderr)
    print(f"Return code: {result.returncode}")
except Exception as e:
    print(f"Error running Chroma: {e}")

# 检查是否可以导入chromadb
try:
    import chromadb
    print("\nChroma is installed. Version:", chromadb.__version__)
except ImportError:
    print("\nChroma is not installed.")