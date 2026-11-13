#!/usr/bin/env python3
"""Package only the assignment's authored sources, configuration, docs and evidence."""
from pathlib import Path
import hashlib
import json
import zipfile

project = Path(__file__).resolve().parents[1]
bookstore = project.parent
assignment = bookstore.parent
files = set()
for module in ('common', 'checkout', 'inventory', 'orders'):
    files.add(project / module / 'pom.xml')
    files.update(p for p in (project / module / 'src').rglob('*') if p.is_file())
for folder in ('infra', 'scripts', 'evidence'):
    files.update(p for p in (project / folder).rglob('*')
                 if p.is_file() and '__pycache__' not in p.parts and p.suffix != '.pyc')
for name in ('pom.xml', 'compose.yml', 'README.md', '.gitignore'):
    files.add(project / name)
files.add(bookstore / 'start.sh')
files.add(bookstore / 'scripts/run-assignment6.sh')
files.add(assignment / 'README.md')
files.add(assignment / '作业6实验报告.docx')
allowed = {'.java', '.xml', '.properties', '.html', '.sql', '.yml', '.sh', '.py',
           '.cjs', '.md', '.json', '.txt', '.log', '.png', '.docx'}
entries = {}
for file in sorted(files):
    if not file.is_file():
        raise RuntimeError(f'Missing required deliverable: {file}')
    if file.name != '.gitignore' and file.suffix not in allowed:
        raise RuntimeError(f'Unexpected artifact in submission: {file}')
    if {'target','node_modules','.git','.env','logs'}.intersection(file.relative_to(assignment).parts):
        raise RuntimeError(f'Excluded path: {file}')
    entries[file.relative_to(assignment).as_posix()] = file.read_bytes()
entries['MANIFEST.sha256'] = ''.join(
    hashlib.sha256(data).hexdigest() + '  ' + name + '\n'
    for name, data in sorted(entries.items())).encode()
output = assignment / '524031910668-作业6.zip'
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name, data in sorted(entries.items()):
        archive.writestr(name, data)
with zipfile.ZipFile(output) as archive:
    if archive.testzip() is not None:
        raise RuntimeError('Corrupt ZIP')
    for line in archive.read('MANIFEST.sha256').decode().splitlines():
        digest, name = line.split('  ', 1)
        if hashlib.sha256(archive.read(name)).hexdigest() != digest:
            raise RuntimeError('Manifest mismatch: ' + name)
print(json.dumps({'archive': str(output), 'files': len(entries), 'bytes': output.stat().st_size}, ensure_ascii=False))
