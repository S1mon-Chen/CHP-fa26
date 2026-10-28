#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/java21-env.sh
mvn -B -f assignment4/pom.xml package
exec java -jar assignment4/target/assignment4-integrated-1.0.0.jar "$@"
