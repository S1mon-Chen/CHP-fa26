#!/usr/bin/env python3
"""Package only assignment-authored/modified files; never archive the whole project."""
from hashlib import sha256
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

PROJECT = Path(__file__).resolve().parents[1]
ASSIGNMENT = PROJECT.parent
FILES = [
    ".env.example",
    "ASSIGNMENT2.md",
    "backend/pom.xml",
    "backend/src/main/java/com/bookstore/assistant/AssistantSettings.java",
    "backend/src/main/java/com/bookstore/assistant/AssistantFailure.java",
    "backend/src/main/java/com/bookstore/assistant/ModelClient.java",
    "backend/src/main/java/com/bookstore/assistant/BookTools.java",
    "backend/src/main/java/com/bookstore/assistant/ToolChatService.java",
    "backend/src/main/java/com/bookstore/assistant/ToolChatController.java",
    "backend/src/main/java/com/bookstore/config/SecurityConfig.java",
    "backend/src/main/java/com/bookstore/repository/BookRepository.java",
    "backend/src/main/resources/assistant/tools.json",
    "backend/src/main/resources/application-assistant.properties",
    "backend/src/main/resources/application-demo.properties",
    "backend/src/main/resources/data-demo.sql",
    "backend/src/test/java/com/bookstore/assistant/ToolCallingTest.java",
    "backend/src/test/java/com/bookstore/assistant/ToolChatIntegrationTest.java",
    "frontend/src/components/AIAssistant.jsx",
    "frontend/src/components/AIAssistant.css",
    "frontend/src/services/aiService.js",
    "frontend/src/test/assistant-tools.test.jsx",
    "frontend/src/test/api-and-session.test.jsx",
    "scripts/mock_competitor.py",
    "scripts/run-assignment2.sh",
    "scripts/smoke_chat.py",
    "scripts/package_submission.py",
]
EVIDENCE = [
    "evidence/live-success.json",
    "evidence/live-timeout-recovery.json",
    "evidence/live-isbn-correction.json",
    "evidence/test-results.txt",
    "evidence/browser-chat.png",
]


def main():
    paths = [(ASSIGNMENT / "README.md", "README.md")]
    paths += [(PROJECT / name, "BookStore_s4/" + name) for name in FILES]
    paths += [(PROJECT / name, "BookStore_s4/" + name) for name in EVIDENCE if (PROJECT / name).is_file()]
    for path, name in paths:
        if not path.is_file():
            raise SystemExit(f"Required file missing: {path}")
        if any(part in {"node_modules", "target", "dist", ".git", "logs", ".env"} for part in Path(name).parts):
            raise SystemExit(f"Forbidden file: {name}")
        if path.suffix in {".jar", ".class", ".pyc"}:
            raise SystemExit(f"Binary dependency forbidden: {name}")
    output = ASSIGNMENT / "524031910668-作业2.zip"
    manifest = []
    with ZipFile(output, "w", compression=ZIP_DEFLATED) as archive:
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
