#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then set -a; source .env; set +a; fi
source scripts/java21-env.sh
# Start scripts/embedding_server.py in another terminal first, as described in README.
mvn -q -f standalone/pom.xml package
exec java -jar standalone/target/assignment3-demo-1.0.0.jar "$@"
