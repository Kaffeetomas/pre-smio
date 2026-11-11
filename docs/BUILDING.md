# 构建和签名

## 本地构建

Linux 需要 Java 21、zip、unzip、keytool。推荐使用 Go 1.27.4。

```bash
bash setup-tools.sh
bash build.sh
```

Android SDK 安装时需要阅读并接受许可。已有 SDK 可通过 `ANDROID_HOME` 指定。输出：

- `out/pre-smio-arm64.apk`
- `out/SHA256SUMS`

构建会运行 Java 节点管理测试、Go 转发和 DNS 测试、mio 空连接回归测试，并验证 APK 签名和 16KB ZIP 对齐。应用使用 Android SDK 35，最低 API 26。

## 签名连续性

没有指定签名密钥时，脚本在被 Git 忽略的 `tools/local-signing.p12` 生成本地开发密钥。本地开发包不能覆盖安装维护者签名的 Release；请勿把这个临时密钥误作正式发布密钥。

正式构建支持：

- `PRE_SMIO_RELEASE=1`：密钥缺失时拒绝构建，不自动换签名。
- `PRE_SMIO_KEYSTORE`：私有 PKCS#12 文件路径。
- `PRE_SMIO_STORE_PASSWORD`、`PRE_SMIO_KEY_PASSWORD`：通过环境变量传递密码。
- `PRE_SMIO_KEY_ALIAS`：密钥别名；历史升级签名沿用 `mio-test`。

签名密钥、密码和本地 GitHub 凭据都不能提交到仓库。

## GitHub Actions

当前工作流只执行构建和测试，不上传 APK、不创建 Release，也不使用维护者签名 Secrets。正式安装包由维护者自行签名、手动上传到 Release。本地构建的开发签名包不能覆盖维护者签名的安装包。

## 测试范围

`tests/NodeBookTest.java` 可在主机执行。`tests/AndroidSmoke.java` 和 `tests/VpnStopSmoke.java` 是额外的 Android 模拟器测试入口，不会编入 Release。

历史 Android 模拟器检查包括加密节点持久化、旧配置迁移、篡改密文后不覆盖文件，以及系统仍绑定服务时连续断开。ARM64 安装包的实机网络测试还需在真实手机进行；编译成功不等同于所有系统分身均兼容。

