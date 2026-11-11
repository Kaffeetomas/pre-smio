# 发布验证

本次只发布源码，以下 APK 检查针对本地构建结果，并非声称替换现有 Release。

版本：pre-smio 1.0.3（versionCode 103，ARM64，Android 8.0+）。

- 节点增删改、选中状态持久化、输入校验与 IPv6 测试通过。
- Go TCP 转发、DNS 转 TCP、截断响应处理、文件描述符传递和空连接回归测试通过。
- 同一客户端代码在 Android 9 模拟器验证过旧偏好迁移、Android Keystore / AES-GCM 节点持久化、密文篡改拒绝，以及被系统绑定时连续断开。1.0.3 的更改为品牌与公开工程整理。
- APK v2/v3 签名和 16KB ZIP 对齐验证通过，签名与此前 1.0.2 社区包一致。
- Release 不启用 debuggable，不包含测试 Instrumentation、个人节点或签名私钥。
- 实机网络测试需在真实手机完成；此前用户已确认连接可用，后续修复版仍需按具体设备复测。

APK SHA-256：

```text
e8487bd612c2575e9b5869fce36c082903c0c21a9fe339d9cb275c80c4c0973d
```
