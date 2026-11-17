#!/usr/bin/env python3
"""Allowlist-only submission package. Never include third-party binaries."""
import hashlib
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT.parents[1]
TARGET = OUT / '524031910668-作业7.zip'

def main():
    files = [OUT/'README.md',OUT/'作业7实验报告.docx',ROOT.parent/'start.sh',
             ROOT.parent/'scripts/run-assignment7.sh', ROOT/'README.md',ROOT/'pom.xml',ROOT/'compose.yml',ROOT/'.gitignore']
    allowed = {'src':{'.java','.properties','.html'},'infra':{'.sql'},
               'scripts':{'.py','.sh','.cjs','.jmx'},'evidence':{'.json','.txt','.png','.log'}}
    for folder, extensions in allowed.items():
        files += [p for p in (ROOT/folder).rglob('*') if p.is_file() and p.suffix in extensions and '__pycache__' not in p.parts]
    files = sorted(set(files))
    missing = [str(p) for p in files if not p.is_file()]
    if missing: raise SystemExit(f'Missing required outputs: {missing}')
    manifest=[]
    with ZipFile(TARGET,'w',ZIP_DEFLATED) as archive:
        for file in files:
            name=file.relative_to(OUT).as_posix()
            content=file.read_bytes()
            archive.writestr(name,content)
            manifest.append(hashlib.sha256(content).hexdigest()+'  '+name)
        archive.writestr('SHA256SUMS.txt','\n'.join(manifest)+'\n')
    with ZipFile(TARGET) as archive:
        assert not any('/target/' in n or n.endswith(('.jar','.class','.env')) for n in archive.namelist())
        assert archive.testzip() is None
    print(f'{TARGET}\n{len(files)+1} files, {TARGET.stat().st_size} bytes')

if __name__=='__main__': main()
