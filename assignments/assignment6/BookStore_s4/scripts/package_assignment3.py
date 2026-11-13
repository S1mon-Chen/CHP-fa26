#!/usr/bin/env python3
"""Package an explicit allowlist, including a standalone build, without the full app."""
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

PROJECT = Path(__file__).resolve().parents[1]
ASSIGNMENT = PROJECT.parent
FILES = [
    ".gitignore", ".env.example", "ASSIGNMENT3.md", "requirements-rag.txt",
    "backend/src/main/java/com/bookstore/assistant/ModelClient.java",
    "backend/src/main/java/com/bookstore/assistant/AssistantSettings.java",
    "backend/src/main/java/com/bookstore/assistant/AssistantFailure.java",
    "backend/src/main/java/com/bookstore/config/SecurityConfig.java",
    "backend/src/main/resources/application-assistant.properties",
    "backend/src/main/resources/application-shopping.properties",
    "backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker",
    "frontend/src/components/AIAssistant.jsx", "frontend/src/components/AIAssistant.css",
    "frontend/src/services/aiService.js", "frontend/src/test/assistant-tools.test.jsx",
    "frontend/src/test/api-and-session.test.jsx", "frontend/package.json", "frontend/package-lock.json",
    "standalone/pom.xml", "standalone/src/main/java/com/bookstore/shoppingdemo/ShoppingDemoApplication.java",
    "scripts/embedding_server.py", "scripts/run-assignment3.sh", "scripts/run-standalone.sh",
    "scripts/demo_assignment3.py", "scripts/package_assignment3.py",
]
for folder, pattern in [
    ("backend/src/main/java/com/bookstore/shopping", "*.java"),
    ("backend/src/test/java/com/bookstore/shopping", "*.java"),
    ("backend/src/main/resources/shopping", "*"),
]:
    FILES.extend(str(p.relative_to(PROJECT)) for p in sorted((PROJECT / folder).glob(pattern)) if p.is_file())
FILES.extend("evidence-rag/" + name for name in [
    "live-complex.json", "live-complex.txt", "live-membership.json", "live-membership.txt",
    "live-books-standalone.json", "live-books-standalone.txt", "index-manifest.json",
    "retrieval-evaluation.json", "test-results.txt", "browser-answer.png", "browser-trace.png",
])


def main():
    paths = [(ASSIGNMENT / "README.md", "README.md")]
    paths += [(PROJECT / p, "BookStore_s4/" + p) for p in FILES]
    for path, name in paths:
        if not path.is_file():
            raise SystemExit(f"Required file missing: {name}")
        if set(Path(name).parts) & {".env", "node_modules", "target", ".venv-rag", ".model-cache", "logs", ".git"}:
            raise SystemExit(f"Forbidden file: {name}")
        if path.suffix in {".jar", ".class", ".onnx", ".pyc"}:
            raise SystemExit(f"Binary dependency forbidden: {name}")
    output = ASSIGNMENT / "524031910668-作业3.zip"
    manifest = []
    with ZipFile(output, "w", ZIP_DEFLATED) as archive:
        for path, name in paths:
            content = path.read_bytes()
            archive.writestr(name, content)
            manifest.append(f"{sha256(content).hexdigest()}  {name}")
        archive.writestr("MANIFEST.sha256", "\n".join(manifest) + "\n")
    with ZipFile(output) as archive:
        assert archive.testzip() is None
    print(f"Created {output.name}: {len(paths) + 1} files, {output.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
