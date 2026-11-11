# pre-smio

**mio 的社区尝鲜版客户端，不是官方客户端 smio。**

pre-smio 用于在手机上连接已部署 mio 的 VPS，支持 Android 系统分身，无需 Termux 或 root。它也是 smio 专属客户端开发期间的社区过渡项目，不代表上游官方客户端。

[下载 APK / Releases](https://github.com/Kaffeetomas/pre-smio/releases/latest) · [mio 协议项目](https://github.com/orgmio/mio) · [构建说明](docs/BUILDING.md) · [更新记录](CHANGELOG.md)

## 功能

- 连接、节点、设置三个页面；支持浅色、深色与跟随系统。
- 多 VPS 节点添加、编辑、复制、删除和选择，支持切换并重新连接。
- 节点配置使用 Android Keystore / AES-GCM 加密保存，禁用系统备份。
- 显示连接状态、出口 IP 和运行时长；日志按需查看。
- 支持 Android 多用户 / 系统分身（要求该用户支持 VPN）。
- 保留启动兼容处理、崩溃报告恢复页，以及显式断开隧道的修复。

## 安装和使用

从 [Releases](https://github.com/Kaffeetomas/pre-smio/releases/latest) 下载维护者提供的 APK，适用于 **ARM64、Android 8.0+**。

当前手动发布的安装包是 `mio-1.0.2-arm64.apk`；`main` 分支是更名整理后的 pre-smio 1.0.3 源码。现有 APK 没有因源码更名而重新上传，二者版本应分别看待。

1. 在你需要使用 VPN 的安卓用户或系统分身中安装。
2. 在「节点 → 添加」填写名称、服务器 IP/域名、连接端口、密码、伪装域名和伪装站端口。
3. 选中节点，返回「连接」并允许系统 VPN 请求。
4. 等待显示已连接，并检查出口 IP。

**VPS 必须事先部署 mio 服务端。** 添加一台普通 VPS 的 IP 不会自动安装服务。客户端的密码、伪装域名和端口需要与服务端一致，服务器需放行对应的 TCP、UDP 端口；若启用了 UFW，也需添加放行规则。

### 从此前社区测试版升级

本次发布沿用此前 1.0.2 社区测试包的签名与包名，可直接覆盖安装。旧配置会迁移并保留。**不要先卸载或清除数据**，否则节点和 Android Keystore 密钥会被系统删除。

历史包名仍为 `net.miotest.client`，用于保持升级与数据兼容，不影响显示名称。

## 协议边界

- 代理当前安卓用户内应用的 **TCP** 流量；DNS UDP 请求通过 mio 转为 TCP 查询。
- 不提供一般应用 UDP 隧道。mio 客户端到服务端使用的 QUIC/UDP 传输是另一层，不受此限制。
- 出口 IP 检测通过 mio 请求 `https://api.ipify.org`，是连接时的一次检查，不是持续健康检测。
- 断开 VPN 后恢复普通网络；目前未实现断网保护、订阅、自动故障切换或全平台支持。
- 应用自身 UID / 核心进程排除在 VPN 外，避免底层连接递归。服务器域名在建立 VPN 前通过系统 DNS 解析。

日志仅保存在本地，可能包含访问域名；复制或提交问题前请检查内容。不要提交密码、完整节点配置、签名密钥或服务器私钥。

## 构建、源码和贡献

仓库包含 Android 包装层、Go VPN 桥接层及对应版本的上游源码，不包含私钥或个人节点。具体命令、签名连续性和自动构建说明见 [docs/BUILDING.md](docs/BUILDING.md)。

问题反馈请说明系统版本、手机型号、是否使用系统分身，以及触发步骤。欢迎提交可复现的问题和修复。

## 来源与许可

- [mio](https://github.com/orgmio/mio)：MoAEIOU / orgmio，基于 commit `04c3481dd46091b6f8ef47f7d3024aa0cec33357`，遵循 [MoPL](LICENSE)。`vendor/mio` 保留上游署名，并包含空连接崩溃修复。
- [tun2socks](https://github.com/xjasonlyu/tun2socks)：Jason Lyu，基于 commit `9c66760ef5887e7c2613983885cb41a07bc2e85a`，遵循 [MIT](vendor/tun2socks/LICENSE)。
- gVisor、Go 和其他依赖的许可见 [LICENSES](LICENSES) 及各模块上游文件。

pre-smio 按 MoPL 发布；其他组件保留各自许可证。维护者已确认获得上游作者同意使用本项目名称；这不改变上游许可证对其他再分发者的要求。本项目明确为社区客户端，不冒充 mio 或 smio 的官方发布。
