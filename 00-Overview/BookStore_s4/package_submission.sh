#!/bin/bash
# 只打包作业 2 自己编写的源码、脚本和文档（不含整个工程和第三方依赖）
# 用法：./package_submission.sh   → 生成 ../作业2-提交.zip
set -euo pipefail
cd "$(dirname "$0")"

OUT="../作业2-提交.zip"
FILES=(
  FUNCTION_CALLING.md
  package_submission.sh
  backend/src/main/java/com/bookstore/functioncalling
  backend/src/main/java/com/bookstore/controller/FunctionCallingController.java
  backend/src/main/java/com/bookstore/repository/BookRepository.java
  backend/src/main/resources/function-calling-seed.sql
  backend/src/test/java/com/bookstore/functioncalling
  frontend/src/pages/FunctionCallingPage.jsx
  frontend/src/pages/FunctionCallingPage.css
  frontend/src/services/aiService.js
  frontend/src/App.jsx
  frontend/src/components/Header.jsx
)
# 截图放在 screenshots/ 目录下会一并打包
[ -d screenshots ] && FILES+=(screenshots)

rm -f "$OUT"
zip -r -q "$OUT" "${FILES[@]}" -x '*.DS_Store'
echo "已生成 $OUT："
unzip -l "$OUT" | tail -n +4 | sed '$d' | sed '$d' | awk '{print "  " $4}'
