#!/usr/bin/env python3
"""Build a source-only submission using the slim Maven compiler's allowlist."""
from pathlib import Path
import hashlib, json, zipfile
from xml.etree import ElementTree as ET

root=Path(__file__).resolve().parents[1]
ns={'m':'http://maven.apache.org/POM/4.0.0'}
pom=ET.parse(root/'assignment5/pom.xml')
files=set()
for rule in pom.findall('.//m:plugin[m:artifactId="maven-compiler-plugin"]/m:configuration/m:includes/m:include',ns):
    files.update((root/'backend/src/main/java').glob(rule.text))
for rule in pom.findall('.//m:testIncludes/m:testInclude',ns):
    files.update((root/'backend/src/test/java').glob(rule.text))
for resource in pom.findall('.//m:resources/m:resource',ns)+pom.findall('.//m:testResources/m:testResource',ns):
    directory=(root/'assignment5'/resource.find('m:directory',ns).text).resolve()
    includes=resource.findall('m:includes/m:include',ns)
    if includes:
        for rule in includes: files.update(p for p in directory.glob(rule.text) if p.is_file())
    else: files.update(p for p in directory.rglob('*') if p.is_file())
# pathlib's ** by itself matches directories; keep the Mockito test configuration explicitly.
files.add(root/'backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker')
for name in ('ASSIGNMENT5.md','compose.assignment5.yml','start.sh','assignment5/pom.xml',
             'scripts/java21-env.sh','scripts/run-assignment5.sh','scripts/verify_assignment5.py',
             'scripts/capture_assignment5.cjs','scripts/capture_bookstore_assignment5.cjs',
             'scripts/package_assignment5.py','frontend/src/context/CartContext.jsx',
             'frontend/src/components/CartModal.jsx','frontend/src/services/orderService.js',
             'frontend/src/services/orderStream.js','frontend/src/test/order-stream.test.js',
             'frontend/src/test/checkout-and-messages.test.jsx'):
    files.add(root/name)
files.update(p for p in (root/'evidence-kafka').iterdir() if p.is_file() and p.suffix in {'.png','.json','.txt','.log'})
for p in files:
    if not p.is_file(): raise RuntimeError('Missing submission file: '+str(p))
    if p.suffix in {'.jar','.class','.pyc'} or p.name.startswith('.env'):
        raise RuntimeError('Forbidden artifact: '+str(p))
entries={'README.md':(root.parent/'README.md').read_bytes()}
for p in sorted(files): entries['BookStore_s4/'+p.relative_to(root).as_posix()]=p.read_bytes()
manifest=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(entries.items()))
entries['MANIFEST.sha256']=manifest.encode()
output=root.parent/'524031910668-作业5.zip'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for name,data in sorted(entries.items()): archive.writestr(name,data)
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    for line in archive.read('MANIFEST.sha256').decode().splitlines():
        digest,name=line.split('  ',1)
        assert hashlib.sha256(archive.read(name)).hexdigest()==digest
print(json.dumps({'archive':str(output),'files':len(entries),'bytes':output.stat().st_size},ensure_ascii=False))
