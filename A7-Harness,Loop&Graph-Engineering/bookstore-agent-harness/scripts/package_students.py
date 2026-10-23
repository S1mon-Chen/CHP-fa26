"""白名单打包学生版，不收集运行记录、数据库、虚拟环境或隐藏文件。"""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent
FILES = ['README.md', 'STUDENT_START.md', 'TASK.md', 'pyproject.toml', 'Dockerfile',
         '.dockerignore', '.gitignore', 'config.json', 'start.sh', 'stop.sh']
DIRS = ['harness', 'starter', 'tests_business', 'tests', 'scenarios', 'web', 'docs', 'scripts']


def main():
    selected = [ROOT/name for name in FILES]
    for directory in DIRS:
        for path in (ROOT/directory).rglob('*'):
            if not path.is_file() or path.is_symlink():
                continue
            if any(part.startswith('.') or part == '__pycache__' for part in path.relative_to(ROOT).parts):
                continue
            if path.suffix in ('.pyc', '.log') or path.name == 'lecture-text.json':
                continue  # 原课件全文无需随练习项目分发；保留页码映射与教学对照文档。
            selected.append(path)
    selected = sorted(selected)
    manifest = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in selected}
    destination = ROOT/'dist/bookstore-agent-harness-student.zip'
    destination.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in selected:
            archive.write(path, 'bookstore-agent-harness/'+str(path.relative_to(ROOT)))
        archive.writestr('bookstore-agent-harness/PACKAGE_MANIFEST.json',
                         json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
    with zipfile.ZipFile(destination) as archive:
        assert archive.testzip() is None
    print(f'{destination}\n{len(selected)} files + manifest; {destination.stat().st_size} bytes')
    print('SHA-256:', hashlib.sha256(destination.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
