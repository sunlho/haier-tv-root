# 海尔TV Root

海尔电视 root 工具，支持检测厂商接口、执行 root 命令，并为匹配的固件提供标准 `adb root` 和开机恢复功能。

**推荐直接下载 Releases 正式版安装，无需配置开发环境。** 已在海尔 M638 验证；其他海尔型号可安装后检测，功能是否可用以本机结果为准。

## 下载与安装（推荐）

1. 打开本仓库的 [Releases 页面](../../releases)，选择最新正式版本。
2. 在 **Assets** 中下载 `.apk` 安装包，例如 `haier-root-release.apk`。`Source code` 是源码压缩包，不能直接安装。
3. 将 APK 安装到电视，在应用列表中打开「海尔TV Root」。可使用电视支持的 U 盘、文件管理器安装方式，或使用下面的 ADB 命令。

使用正式 APK 不需要 Python、JDK、Gradle 或 Android Studio。

### 使用 ADB 安装

电视先启用 ADB 调试并授权电脑，电脑安装 Android Platform-Tools。将 `TV_IP:5555` 替换为自己的电视地址；APK 文件名替换为实际下载的文件名：

```sh
adb connect TV_IP:5555
adb -s TV_IP:5555 install -r haier-root-release.apk
adb -s TV_IP:5555 shell am start -n io.github.sunlho.haierroot/.MainActivity
```

## 适用范围

| 项目 | 说明 |
| --- | --- |
| 已验证型号 | 海尔 `M638_HAIER` |
| 已验证系统 | YunOS / Android `4.4.4-R-20200716.1124`，API 19 |
| 其他海尔型号 | 允许安装，启动后检测厂商接口及实际 root 能力 |
| 最低 Android 版本 | Android 4.4，API 19 |
| 安装后的名称 | **海尔TV Root** |
| 应用包名 | `io.github.sunlho.haierroot` |

检测通过后可使用通用 root 命令。标准 `adb root` 补丁还需匹配已验证的 adbd 文件哈希和补丁字节；其他固件未经实机验证。

## 使用方法

### 检测环境与执行 root 命令

打开「海尔TV Root」后，应用自动检测电视型号、Android 版本、厂商接口和实际命令执行身份，也可选择「重新检测环境」。

- 接口不存在或无法执行 root：显示原因，并禁用相关操作。
- 厂商服务确认以 root 执行：启用「执行 root 命令」，可输入 `id`、`getprop` 等短命令并查看输出。
- adbd 文件与已验证版本匹配：额外开放「开启开机恢复 adb root 功能」。

命令须在 10 秒内完成，超时不代表远程任务已取消。应用自身保持普通 UID，由厂商服务代为执行 root 命令。

### 开启标准 adb root

环境匹配时，在电视上选择「开启开机恢复 adb root 功能」。adbd 重启会断开现有连接，重新连接后默认仍是普通 shell；需要 root 时执行：

```sh
adb connect TV_IP:5555
adb -s TV_IP:5555 root
adb connect TV_IP:5555
adb -s TV_IP:5555 shell id
```

成功时返回 `restarting adbd as root`，重连后为 `uid=0(root)`。请只在可信网络中使用开启了 root 的 ADB。

### 恢复与关闭

| 应用按钮 | 效果 |
| --- | --- |
| 开启开机恢复 adb root 功能 | 安装运行时补丁并保存开机恢复设置 |
| 恢复普通 ADB（保留 adb root 功能） | 恢复普通 shell，之后仍可执行 `adb root` |
| 关闭开机恢复并还原原版 adbd | 关闭开机恢复、撤销挂载、恢复原版 adbd |
| 重新检测环境 | 检查厂商接口、root 执行身份和 adbd 补丁兼容性 |
| 执行 root 命令 | 通过厂商 root 服务执行输入的短命令 |

重启会清除运行时补丁；开启开机恢复后，应用会在收到开机广播时重新安装补丁。需要保留并启用本应用，强行停止、冻结、禁用或卸载会影响后续恢复。**卸载前请先关闭开机恢复并还原原版 adbd。**

## 更新与签名

后续更新优先使用同一来源的正式 Release APK，可直接覆盖安装。若此前装过自编译版或 CI debug 版，签名可能不同，无法通过 `adb install -r` 覆盖；请先在旧版应用中关闭开机恢复、还原 adbd，再卸载旧版并安装正式版。

若装有旧探测版，也应先关闭旧版的开机恢复并还原 adbd，避免两份应用同时恢复。原始实测报告中的旧包名仅作为历史记录。

## 自行构建（可选）

需要修改源码或自行生成 APK 时，可选择以下方式。普通使用者直接安装 Releases 正式包即可。

### 独立脚本构建（不使用 Gradle）

电脑已有 **Python 3.8+、JDK 17、Android SDK Platform（API 19 或以上）和 Build Tools 35+** 时，可直接构建，无需下载 AGP、Maven 依赖或任何 Python 包：

```powershell
python build.py
```

脚本读取 `JAVA_HOME`、`ANDROID_HOME` / `ANDROID_SDK_ROOT`，也支持显式指定：

```powershell
python build.py --jdk "D:/Java/jdk-17" --sdk "D:/Android/Sdk"
```

可用 `--build-tools` 指定 Build Tools 目录，`--android-jar` 指定编译平台。默认复用本机已安装的最早可用平台（最低 API 19）及最新稳定 Build Tools。构建参数、包名和版本从 `app/build.gradle` 读取，无需运行 Gradle。

输出 **`build/haier-root.apk`** 和 `build/SHA256SUMS`，自动校验 Android 4.4 所需的 v1 签名及厂商类隔离。安装命令：

```sh
adb -s TV_IP:5555 install -r build/haier-root.apk
```

签名优先复用已有 `~/.android/debug.keystore`；不存在时创建并保留 `.signing/debug.keystore`。更新时保留相同密钥。自定义发布签名使用下文的四个 `ANDROID_KEYSTORE_FILE` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD` 环境变量。密钥不会提交到 Git。

### Gradle 命令行构建

安装 JDK 17 和 Android SDK，设置 `JAVA_HOME`、`ANDROID_HOME`，或在不提交的 `local.properties` 中设置 `sdk.dir`。有 Android SDK 命令行工具时可安装：

```sh
sdkmanager "platforms;android-34" "build-tools;35.0.0" "platform-tools"
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Linux / macOS：

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

安装包位于 `app/build/outputs/apk/debug/app-debug.apk`，包含 Android 4.4 所需的 v1 签名。默认使用本机 Android debug key，无需配置发布密钥就能构建和安装。可通过 `python tools/verify-apk.py <APK路径>` 检查 APK 未携带厂商类实现。

### Android Studio 导入

1. 克隆仓库，在 Android Studio 选择 **Open**，打开含 `settings.gradle` 的仓库根目录。
2. 使用支持 AGP 8.13 的 Android Studio，Gradle JDK 设置为 **JDK 17**，Gradle distribution 使用 **Wrapper**。
3. 在 SDK Manager 安装 **Android SDK Platform 34**、**Build-Tools 35.0.0** 和可选的 **Platform-Tools**。
4. 完成 Gradle Sync，选择 `app`，通过 Build APK(s) 构建，或连接电视后 Run。

首次同步需要联网下载 Gradle 和 Google Maven 依赖。工具版本固定为 AGP 8.13.2 / Gradle 8.13 / JDK 17，兼容关系见 [Android 官方说明](https://developer.android.com/build/releases/agp-8-13-0-release-notes)。`compileSdk 34` 是编译环境，最低支持 API 19；`targetSdk 25` 保留旧电视的后台服务行为，同时满足较新系统的侧载最低要求，参见 [Android 安装限制说明](https://developer.android.com/about/versions/15/behavior-changes-all#minimum-target-api-level)。

## GitHub Actions：仅手动构建

工作流仅有 `workflow_dispatch`，push、PR、tag 均不会自动构建。

在 GitHub **Actions → Build APK → Run workflow**：

- `release_tag` 留空：构建 debug APK、运行 Android Lint 和接口兼容性单元测试、检查 v1 签名和厂商类隔离，然后上传 APK、SHA-256 和 Lint 报告。无需 Secrets。
- 填写 `v1.2.0` 这类版本号：使用固定密钥构建签名 release APK，完成相同检查后上传产物，并为本次选择的提交创建 GitHub Release 和 tag。

Artifact 中 APK 命名为 `haier-root-debug.apk` 或 `haier-root-release.apk`。从当前仓库的 Actions 页面下载构建产物需要登录 GitHub；公开仓库 Release 附件可直接下载。

CI debug key 在不同运行间可能变化，适合测试；正式发布请使用固定 release key，确保后续版本能够覆盖安装。

### 维护者：配置 Release 签名

只需配置一次。请保管好密钥和密码，后续更新始终使用同一份：

```sh
keytool -genkeypair -keystore release.jks -alias haier-m638 -keyalg RSA -keysize 2048 -validity 10000
```

在仓库 **Settings → Secrets and variables → Actions** 添加：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 完整 JKS 文件的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | `haier-m638` 或实际 alias |
| `ANDROID_KEY_PASSWORD` | 私钥密码 |

生成 Base64（自行复制输出至 Secret，不提交到仓库）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\release.jks)))
```

```sh
base64 < release.jks | tr -d '\n'
```

本地签名可复制 `keystore.properties.example` 为 `keystore.properties`，填写自己的密钥路径和密码，再执行 `./gradlew :app:assembleRelease`。也可用 `ANDROID_KEYSTORE_FILE`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 环境变量。未配置密钥的本地 release 构建为 unsigned，需另行签名才可安装；工作流发布时缺少密钥会立即失败。

## 实现与验证记录

应用通过电视自带的 `TvManager.setTvosInterfaceCommand()` 调用厂商服务，无 shared UID，不依赖 freezeapp、JDWP 或 su。厂商库为可选依赖，通过反射检测，无需电视 SDK 或厂商编译桩即可构建。

ADB root 补丁通过 bind mount 覆盖运行时 `/sbin/adbd`，不刷启动镜像、不写 `/system`。

| 文件 | SHA-256 |
| --- | --- |
| 已验证的原版 adbd | `8380fb3109225cae45335ede3653686a38a2ea8ad0b7c77699876b975e42ef0f` |
| 修正版 adbd | `d72e74a9a1694f84b51e78f5cc155a2281b85db8f17e0fd8e52e4cf32d2ab9f1` |

```text
build.py             不依赖 Gradle 的独立构建脚本
app/                 Android 应用、Manifest、补丁 JSON
gradle/wrapper/      固定版本的 Gradle Wrapper
tools/               APK 厂商类隔离检查、可选补丁生成器
docs/                原工程实测记录
.github/workflows/   手动构建及可选 Release 发布
```

原工程已在上述电视上验证普通 ADB → root、恢复普通 ADB、还原原版 adbd、再次启用；开机恢复由用户反馈实测正常，原工程没有完整的跨重启自动验证日志。初始普通 UID 权限对照报告保留在 [docs/haier-m638-result.txt](docs/haier-m638-result.txt)。独立工程的构建验证不代表在其他型号或固件上完成实机测试。`VendorInterfaceTest` 覆盖接口可用、缺少方法、签名不符、服务缺失、调用被拒绝和 root 身份识别。

`app/src/main/assets/adbd-root-patch.json` 包含精确偏移和前后字节；修正版 SHA-256 为 `d72e74a9a1694f84b51e78f5cc155a2281b85db8f17e0fd8e52e4cf32d2ab9f1`。维护补丁时可运行 `tools/generate-adbd-patch.py <原版adbd路径>`，另需安装 `capstone`、`keystone-engine`；正常 Gradle 构建不需要 Python 或这些库。

日志：`adb -s TV_IP:5555 logcat -d -v brief -s AdbRootSupport:I HaierRoot:I`。开机恢复成功标记为 `BOOT_RESTORE_OK`，失败为 `BOOT_RESTORE_FAILED`。

[MIT License](LICENSE)。Gradle Wrapper 适用其自带的 Apache-2.0 声明。
