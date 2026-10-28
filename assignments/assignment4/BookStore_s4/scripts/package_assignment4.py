#!/usr/bin/env python3
"""Package the integrated assignment sources and evidence, never the complete bookstore."""
from hashlib import sha256
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile, ZIP_DEFLATED


def main():
    project = Path(__file__).resolve().parents[1]
    assignment = project.parent
    namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
    pom = ET.parse(project / 'assignment4/pom.xml')
    # The minimal build's compiler includes are also the source packaging allowlist.
    sources = pom.findall('.//m:plugin[m:artifactId="maven-compiler-plugin"]/m:configuration/m:includes/m:include', namespace)
    files = ['backend/src/main/java/' + item.text for item in sources]
    files += [
        '.gitignore', '.env.example', 'ASSIGNMENT4.md', 'start.sh', 'backend/pom.xml',
        'backend/src/main/resources/application.properties',
        'backend/src/main/resources/application-jwt.properties',
        'backend/src/main/resources/application-demo.properties',
        'backend/src/main/resources/application-assignment4.properties',
        'backend/src/main/resources/data-demo.sql', 'backend/src/main/resources/static/verify.html',
        'backend/src/test/resources/application-test.properties',
        'backend/src/test/java/com/bookstore/security/BookstoreJwtHttpTest.java',
        'backend/src/test/java/com/bookstore/BookstoreRegressionTest.java',
        'backend/src/test/java/com/bookstore/assistant/ToolChatIntegrationTest.java',
        'backend/src/test/java/com/bookstore/shopping/ShoppingIntegrationTest.java',
        'assignment4/pom.xml', 'assignment4/src/main/resources/application.properties',
        'frontend/src/services/api.js', 'frontend/src/services/authToken.js',
        'frontend/src/services/userService.js', 'frontend/src/services/bookService.js',
        'frontend/src/context/AuthContext.jsx', 'frontend/src/context/BooksContext.jsx',
        'frontend/src/App.jsx', 'frontend/src/pages/HomePage.jsx',
        'frontend/src/test/api-and-session.test.jsx',
    ]
    files += ['scripts/' + name for name in [
        'java21-env.sh', 'run-assignment4.sh', 'run-assignment4-submission.sh',
        'run-assignment2.sh', 'run-assignment3.sh', 'demo_assignment3.py', 'smoke_chat.py',
        'verify_assignment4.py', 'benchmark_assignment4.py', 'capture_assignment4.cjs',
        'check_bookstore_ui.cjs', 'package_assignment4.py',
    ]]
    files += ['evidence-jwt/' + name for name in [
        'authentication-401-200.png', 'bookstore-jwt-login.png', 'bookstore-jwt-order.png',
        'browser-verification.json', 'bookstore-ui-verification.json', 'benchmark.json',
        'http-verification.json', 'http-verification.txt', 'test-summary.txt',
        'backend-tests.log', 'submission-tests.log', 'frontend-tests.log',
        'frontend-lint.log', 'frontend-build.log',
    ]]
    if len(files) != len(set(files)):
        raise RuntimeError('Duplicate allowlist entry')
    entries = [(assignment / 'README.md', 'README.md')]
    entries += [(project / name, 'BookStore_s4/' + name) for name in files]
    forbidden = {'.env', '.git', 'target', 'node_modules', '.venv-rag', '.model-cache', 'logs', '__pycache__'}
    contents = []
    for file, name in entries:
        if not file.is_file() or file.is_symlink():
            raise RuntimeError(f'Missing file or symlink: {name}')
        if forbidden.intersection(Path(name).parts) or file.suffix in {'.jar', '.class', '.pyc', '.onnx'}:
            raise RuntimeError(f'Forbidden dependency or credential path: {name}')
        contents.append((name, file.read_bytes()))
    output = assignment / '524031910668-作业4.zip'
    with ZipFile(output, 'w', ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in contents:
            archive.writestr(name, data)
        archive.writestr('MANIFEST.sha256', ''.join(f'{sha256(data).hexdigest()}  {name}\n' for name, data in contents))
    with ZipFile(output) as archive:
        assert archive.testzip() is None
        for line in archive.read('MANIFEST.sha256').decode().splitlines():
            expected, name = line.split('  ', 1)
            assert sha256(archive.read(name)).hexdigest() == expected, name
    print(f'Created {output.name}: {len(contents) + 1} files, {output.stat().st_size:,} bytes; hashes verified.')


if __name__ == '__main__':
    main()
