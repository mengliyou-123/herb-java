# 自建问答 Agent 接入说明

视频问诊和普通文字问诊的问答流现在由 Vue 直接请求工作区内的
`herb-agent/tcm_merge` 服务。开发环境通过 Vite 的 `/agent-api` 代理，生产环境应由
Nginx 等反向代理把 `/agent-api` 转发到 Agent；Spring Boot 只负责登录鉴权和问诊历史保存。

## 启动 Agent

在工作区根目录执行：

```powershell
python -m pip install -r herb-agent/tcm_merge/requirements.txt
python -m uvicorn tcm_merge.app:app --app-dir herb-agent --host 0.0.0.0 --port 7862
```

Agent 的模型、RAG 和 Neo4j 配置仍放在 `herb-agent/tcm_merge/.env`，不要把密钥提交到仓库。

## 启动 Java 后端

开发环境的前端代理默认请求 `http://localhost:7862`。如果 Agent 部署在其他地址，修改
`前端程序/vite.config.js` 中 `/agent-api` 的 `target`，或在生产反向代理中调整目标地址。

```powershell
$env:VITE_AGENT_BASE_URL = "http://127.0.0.1:7862/api"
```

问诊流使用 Agent 的 `/api/query/stream`，前端会把 Agent 的累计 SSE 文本转换成原页面使用的
增量文本。问答完成后，前端通过 Spring Boot 的 `/ai/history` 保存当前登录用户的历史记录。
