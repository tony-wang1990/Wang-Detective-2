# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| 4.1.6   | :white_check_mark: |
| < 4.1.6 | :x: |

只有当前版本（`4.1.6`，与 `pom.xml` 一致）会收到安全更新。旧版本请先升级到
`4.1.6` 再报告问题。

## Reporting a Vulnerability

请不要在公开 Issue 中直接贴出漏洞利用细节。请通过以下任一私密渠道报告：

- 在 GitHub 上提交 Issue 并使用私密报告方式（GitHub 的 Private vulnerability reporting，如仓库已开启）；或
- 通过 GitHub 联系仓库所有者（`tony-wang1990`）私密报告。

报告时请说明：受影响的版本、复现步骤（或概念验证）、影响范围、以及你认为
可行的缓解方法。收到报告后我们会先私下确认与修复，修复发布后再公开披露。
请给我们合理的修复时间，不要在修复发布前公开漏洞细节。

## 已知安全要求（部署前必读）

- `ADMIN_PASSWORD` 与 `OPS_SSH_SECRET_KEY` 均为必填、无默认值：未设置时应用
  拒绝启动；`OPS_SSH_SECRET_KEY` 必须与管理员密码不同。
- `CORS_ALLOWED_ORIGINS` 默认留空（仅同源），不要设置为 `*`。
- 不要把 `.env`、数据库文件（`*.db`）、`keys/` 下的私钥提交进仓库。
