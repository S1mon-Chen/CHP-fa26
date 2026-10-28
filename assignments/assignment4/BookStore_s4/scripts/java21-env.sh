#!/usr/bin/env bash
# Source after loading .env. Used by the complete bookstore and the submission launcher.
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
command -v java >/dev/null || { echo '需要 JDK 21。' >&2; exit 1; }
java_version=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
if [[ "${java_version:-0}" -lt 21 ]]; then
  echo "当前 Java 为 ${java_version:-未知}；请设置 JAVA_HOME 指向 JDK 21。" >&2
  exit 1
fi
if [[ -z "${JWT_SECRET:-}" ]]; then
  export JWT_SECRET=$(openssl rand -base64 32)
  echo '已为本次运行生成临时 JWT 密钥；重启后旧 Token 失效。'
fi
