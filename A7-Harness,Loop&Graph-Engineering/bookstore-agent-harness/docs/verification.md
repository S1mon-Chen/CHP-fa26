# 实际验收记录

执行日期：2026-10-12。宿主使用 Python 3.12 虚拟环境，Docker Server 29.6.1；容器基础镜像 Python 3.12-slim，pytest 8.3.5。原 Docker Hub 地址连接被重置，改用官方镜像公共仓库成功构建：

```sh
docker build --build-arg BASE_IMAGE=public.ecr.aws/docker/library/python:3.12-slim -t bookstore-harness:py312 .
```

| 验收项 | 实际结果 | 证据 |
|---|---|---|
| 初始业务边界失败 | 真实 Docker：3 failed, 26 passed；三个失败均由同一个免运费边界 Bug 导致 | `docker-verification.json` initial |
| 正确候选 | 真实 Docker：29 passed | 同文件 correct |
| 修改测试文件 | 3 次 VALIDATION_ERROR，0 次测试调用，最终 FAILED | run `9a6ce4bcb15b4ef6be89a4bca82d2b64` |
| 重复失败 | 相同候选、相同真实失败，第 2 轮 STALLED | run `9f84ac28e14d4736b11b92d10331bf6e` |
| 预算耗尽 | 阈值设 10，3 轮真实失败后 FAILED | run `954225c535694734ac02d17047bbedad`，配置 `docs/config-budget.json` |
| 执行超时 | Docker 中死循环触发 TEST_TIMEOUT；控制测试验证归类 EXECUTION_ERROR | `docker-verification.json` timeout；`tests/test_controls.py` |
| 输出上限 | Docker 中无限输出触发 OUTPUT_LIMIT，输出保留不超过 4096 字节 | 同文件 output_limit |
| 回放不调用模型/工具 | 已回放真实 live 轨迹；控制测试用禁止调用的回调验证 | run `6e5d6b6029c6431bb241745fd29c1d56`；控制测试 |
| 缺少 Docker 不执行宿主候选 | 自动化注入 Docker 不可用，CLI 返回 EXECUTION_ERROR；执行器被设置为一调用即失败 | `test_missing_docker_never_runs_host` |
| scripted 成功 | 第 1 轮真实测试失败，第 2 轮通过 | run `5812d7d1cde04f16994f97ee06f60c84` |
| live | Ollama `qwen2.5:7b` 真实调用，第 1 轮 29 项测试通过，SUCCESS | run `677a69650b804d209cbbaad1ec53ff70` |
| Harness 测试 | 20 passed | `python -m pytest -q` |

所有 run 均在 `runs/` 下，含事件、汇总、录制、候选和 diff（被拒绝的候选不落盘）。live 的 token 计数由 Ollama 返回：输入 711，输出 318。scripted 模拟执行和其回放也已运行，明确记录 simulated 来源。

## 验证范围限制

八项指定验收均已覆盖。Docker 缺失是依赖注入测试，未卸载或停止用户的 Docker 服务。模型超时/重试通过故障注入验证，未刻意中断用户的 Ollama。未验证其他操作系统、其他模型及对抗性容器逃逸；不以本次通过声明容器绝对安全。live 的成功只代表此次实际运行。
