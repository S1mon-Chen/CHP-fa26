---
name: "vibe-project-generator"
description: "Generates complete project structures from natural language descriptions. Invoke when user wants to create a new project, demo system, or application based on plain English requirements."
---

# Vibe Project Generator

This skill transforms natural language descriptions into complete, runnable project structures. It follows the Vibe Coding methodology: understand intent, generate code, test, and iterate based on feedback.

## When to Use

**INVOKE THIS SKILL WHEN:**
- User says "I want to build...", "create a...", "develop a..." or similar
- User provides a code snippet and asks to generate the complete program
- User wants a demo/prototype/sample implementation
- User asks to create a teaching case or example project
- User describes functionality without specifying technical details

**DO NOT INVOKE WHEN:**
- User asks to modify existing code (use direct editing instead)
- User asks a specific technical question without wanting a full project
- User asks to add features to an existing project (unless explicitly asked to generate a new one)

## Core Workflow

### Phase 1: Understanding & Planning

1. **Parse the Request**
   - Identify the core purpose: what should the system do?
   - List key features/requirements
   - Determine the tech stack preferences (explicit or implied)
   - Identify target users or audience

2. **Design Project Structure**
   - Determine appropriate architecture (CLI, Web, API, etc.)
   - Plan file organization
   - Identify required dependencies
   - Design data flow and storage

3. **Create Specification**
   - Write a clear project description
   - List all features with priority
   - Define success criteria

### Phase 2: Project Generation

#### Standard Project Structure

```
project-name/
├── README.md              # Project documentation
├── requirements.txt       # Python dependencies (if applicable)
├── package.json          # Node.js dependencies (if applicable)
├── src/                  # Source code
│   ├── main.py          # Entry point
│   ├── config.py        # Configuration
│   ├── models/          # Data models
│   ├── services/        # Business logic
│   └── utils/           # Utility functions
├── scripts/             # Shell scripts
│   ├── start.sh        # Start script
│   └── stop.sh         # Stop script
├── tests/              # Test files
└── docs/              # Additional documentation
```

#### For Web Applications

```
web-app/
├── backend/
│   ├── app.py          # Flask/FastAPI backend
│   └── requirements.txt
├── frontend/
│   ├── public/
│   │   ├── index.html
│   │   ├── style.css
│   │   └── app.js
│   ├── server.js      # Node.js proxy server
│   └── package.json
├── docker-compose.yml  # (optional) Container orchestration
└── docs/
```

#### For Teaching Demos

```
teaching-demo/
├── src/                # Core implementation
├── examples/          # Usage examples
├── exercises/         # Practice problems
├── solutions/        # Reference answers
└── README.md          # Tutorial documentation
```

### Phase 3: Code Generation Rules

#### Python Projects

1. **Dependencies Management**
   ```bash
   # Always create requirements.txt
   pip freeze > requirements.txt
   ```

2. **Entry Point Template**
   ```python
   """
   [Project Title]
   [Brief Description]

   Usage:
     python main.py [arguments]

   Dependencies:
     - pip install -r requirements.txt
   """

   import argparse
   import sys

   def main():
       parser = argparse.ArgumentParser(description='[Description]')
       # Add arguments
       args = parser.parse_args()

       # Main logic
       print("[Project Name] started...")

   if __name__ == "__main__":
       main()
   ```

3. **Configuration**
   ```python
   # config.py
   import os

   # Environment variables with defaults
   DEBUG = os.getenv('DEBUG', 'False').lower() == 'true'
   PORT = int(os.getenv('PORT', 5000))
   ```

#### Node.js Projects

1. **Package.json Template**
   ```json
   {
     "name": "project-name",
     "version": "1.0.0",
     "description": "Project description",
     "main": "server.js",
     "scripts": {
       "start": "node server.js",
       "dev": "nodemon server.js"
     },
     "dependencies": {
       "express": "^4.18.0"
     }
   }
   ```

2. **Entry Point Template**
   ```javascript
   /**
    * [Project Name]
    * [Brief Description]
    */

   const express = require('express');
   const app = express();
   const PORT = process.env.PORT || 3000;

   app.use(express.json());
   app.use(express.static('public'));

   // Routes
   app.get('/api/health', (req, res) => {
     res.json({ status: 'ok' });
   });

   app.listen(PORT, () => {
     console.log(`[Project] running on port ${PORT}`);
   });
   ```

### Phase 4: Script Generation

#### Start Script Template

```bash
#!/bin/bash

# ==========================================
# [Project Name] Start Script
# ==========================================

echo "=========================================="
echo "  [Project Name] Starting..."
echo "=========================================="

# Check dependencies
echo "[1/3] Checking dependencies..."

# Start services
echo "[2/3] Starting services..."

# Run application
echo "[3/3] Running application..."
python src/main.py

echo "=========================================="
echo "  [Project Name] Started!"
echo "=========================================="
```

#### Stop Script Template

```bash
#!/bin/bash

# ==========================================
# [Project Name] Stop Script
# ==========================================

echo "[Stopping] [Project Name]..."
# Add cleanup commands here
pkill -f "process-name"

echo "✅ [Project Name] stopped!"
```

### Phase 5: Documentation Generation

#### README.md Template

```markdown
# [Project Name]

[One-line description]

## Features

- Feature 1
- Feature 2
- Feature 3

## Requirements

- Python 3.8+ / Node.js 18+
- [Other dependencies]

## Installation

```bash
# Clone repository
git clone [repo-url]
cd [project-name]

# Install dependencies
pip install -r requirements.txt
# or
npm install
```

## Usage

```bash
# Run
python main.py

# Or use start script
./start.sh
```

## Project Structure

```
project-name/
├── main.py          # Entry point
├── config.py        # Configuration
├── src/             # Source code
└── scripts/        # Utility scripts
```

## API Reference (if applicable)

| Endpoint | Method | Description |
|----------|--------|-------------|
| /api/... | GET/POST | Description |

## License

MIT
```

## Quality Checklist

Before completing, verify:

- [ ] All code compiles/runs without syntax errors
- [ ] Dependencies are listed in requirements.txt or package.json
- [ ] Start/stop scripts are executable (chmod +x)
- [ ] README.md provides clear usage instructions
- [ ] Entry point has proper argument parsing
- [ ] Error handling is implemented
- [ ] Configuration is externalized (not hardcoded)

## Common Patterns

### Pattern 1: Teaching Demo Generator

When user provides a code snippet and says "create a teaching case":

1. Create a complete runnable version
2. Add inline comments explaining key concepts
3. Create a README with learning objectives
4. Add example inputs/outputs
5. Create test cases to verify correctness

### Pattern 2: Web Application Generator

When user wants a web app:

1. Determine frontend + backend structure
2. Create REST API backend
3. Create static frontend files
4. Add proxy server if needed (CORS)
5. Provide docker-compose for easy deployment

### Pattern 3: API Service Generator

When user wants an API service:

1. Choose framework (Flask/FastAPI for Python, Express for Node.js)
2. Create standard endpoints (health, status, resources)
3. Add request validation
4. Implement error handling
5. Create API documentation

## Iteration Based on Feedback

When user reports issues:

1. **Bug Reports**: Reproduce, fix, verify
2. **Feature Requests**: Assess scope, implement if minor, document if major
3. **UX Issues**: Understand intent, propose solution, implement
4. **Performance Issues**: Profile, optimize, test

Remember: Vibe Coding is iterative. Always ask for feedback after initial generation.

## Examples

### Example 1: Simple CLI Tool

**User Request**: "Create a CLI tool that counts lines of code in a directory"

**Generated Structure**:
```
line-counter/
├── line_counter.py
├── requirements.txt
├── start.sh
└── README.md
```

### Example 2: Web API

**User Request**: "Build a REST API for a todo list with Flask"

**Generated Structure**:
```
todo-api/
├── backend/
│   ├── app.py
│   └── requirements.txt
├── frontend/
│   └── public/
│       ├── index.html
│       └── app.js
├── start.sh
└── README.md
```

### Example 3: Teaching Case

**User Request**: "I have this code snippet, can you make it a teaching example?"

**Generated Structure**:
```
teaching-case/
├── src/
│   ├── main.py
│   └── concepts/
├── tests/
├── examples/
├── README.md (with tutorial)
└── EXERCISES.md
```

## Limits and Boundaries

**This skill can generate:**
- Complete project structures
- Working code with proper error handling
- Documentation and README files
- Start/stop scripts
- Basic test cases

**This skill cannot:**
- Execute code in environments without proper setup
- Guarantee 100% correctness of generated code (always verify)
- Handle extremely complex architectures without more guidance

**When uncertain:**
- Ask clarifying questions about tech stack preferences
- Request more details on specific requirements
- Propose a simplified version if the request is too vague
