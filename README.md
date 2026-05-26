# SunnyNet Android

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

基于 [SunnyNet](https://github.com/qtgolang/SunnyNet) 引擎的 Android 抓包示例应用。通过系统 VPN（TUN）拦截设备流量，演示 HTTP/HTTPS、WebSocket、TCP、UDP 的抓包、规则处理与会话导入导出等能力。

## 重要说明

> **项目定位**  
> 本仓库为 **SunnyNet SDK 在 Android 端的参考示例**，并非功能完备的生产级产品。部分能力仍在完善中，界面与交互也可能随版本调整；请以源码为准，自行评估是否满足你的使用场景。

> **功能完整性**  
> 当前实现覆盖 SunnyNet 核心能力的 **大致流程与用法示例**，与桌面版 SunnyNetV5 相比可能存在功能缺失、行为差异或已知问题。欢迎在此基础上二次开发，但不保证所有场景开箱即用。

> **合法使用**  
> 本工具仅供学习、调试及 **经授权** 的网络分析。请勿用于窃取他人隐私、绕过安全机制或其他违法行为；使用者须自行承担合规责任。

## 开源协议

本仓库 **应用层源码** 采用 **[MIT License](LICENSE)** 开源。

```
Copyright (c) 2026 秦天

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

完整许可全文见 [LICENSE](LICENSE) 文件；应用内 **开源协议** 页面亦提供中文版说明。

### 第三方组件

| 组件 | 协议 | 说明 |
|------|------|------|
| [AndroidX](https://developer.android.com/jetpack/androidx) | Apache 2.0 | UI 与架构组件 |
| [Material Components](https://github.com/material-components/material-components-android) | Apache 2.0 | Material Design |
| [ObjectBox](https://objectbox.io/) | Apache 2.0 | 本地数据库 |
| [SunnyNet SDK](https://github.com/qtgolang/SunnyNet) | 见 SDK 仓库 | 预编译原生库，位于 `app/src/main/jniLibs/` |

## 功能概览

以下为本示例当前已实现的主要能力，**不代表 SunnyNet 全量功能**：

### 抓包与展示

- VPN 模式启停抓包，前台服务与通知栏快捷停止
- 按应用包名筛选抓包目标（不选则抓取全部，不含本应用）
- 可开关 HTTP/HTTPS、WebSocket、TCP、UDP 抓取范围
- 列表按协议与关键字筛选；详情页支持总览、请求/响应、时间、流列表与 Hex 查看
- 响应体 JSON / 文本 / 图片等基础展示

### 规则与代理

- 规则类型示例：替换、重写、拦截、屏蔽、Hosts（字段对齐 SunnyNetV5）
- 匹配方式：字符串、十六进制、Base64、正则
- 本机监听端口（默认 `2025`），支持局域网 HTTP/S、SOCKS5 代理接入
- 根证书导出，用于 HTTPS 解密

### 会话与数据

- 会话文件导入/导出（`.syn3` / `.sy4`，与 SunnyNetV5 格式对齐）
- ObjectBox 本地持久化抓包记录

## 环境要求

| 项目 | 要求 |
|------|------|
| Android 系统 | API 24（Android 7.0）及以上 |
| 编译 SDK | Android API 36 |
| JDK | 11 |
| Android Studio | 推荐最新稳定版（含 AGP 9.x 支持） |
| Gradle | 9.4.1（已包含 Wrapper） |

## 快速开始

### 克隆仓库

```bash
git clone https://github.com/qtgolang/SunnyNetAndroid.git
cd SunnyNetAndroid
```

### 命令行构建

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

Debug APK 输出路径：`app/build/outputs/apk/debug/`。

### Android Studio

1. 打开项目根目录，等待 Gradle 同步完成
2. 连接真机（抓包需 VPN 授权，模拟器支持有限）
3. 运行 `app` 模块

## 使用说明

1. **安装根证书**（HTTPS 解密必需）  
   侧栏 → **证书** → 导出根证书 → 在系统设置中安装为「CA 证书」

2. **选择抓包目标**（可选）  
   侧栏 → **抓包目标** → 选择要监控的应用

3. **配置抓取范围**（可选）  
   侧栏 → **抓取范围** → 开关 HTTP、WebSocket、TCP、UDP

4. **配置规则**（可选）  
   侧栏 → **规则设置** → 添加替换 / 重写 / 拦截 / 屏蔽 / Hosts 规则

5. **开始抓包**  
   **抓包工作台** → **开始抓包** → 在系统弹窗中允许 VPN 连接

6. **局域网代理抓包**（可选）  
   侧栏 → **端口设置** 查看内网地址，将其他设备代理指向该地址

7. **导入 / 导出会话**（可选）  
   侧栏 → **会话文件** → 与桌面 SunnyNetV5 交换 `.syn3` / `.sy4`

## 项目结构

```
SunnyNetAndroid/
├── app/
│   ├── src/main/java/com/sunnynet/tools/
│   │   ├── capture/          # 抓包引擎、规则、事件处理
│   │   ├── data/             # ObjectBox 实体与持久化
│   │   ├── net/              # 网络 / VPN 辅助
│   │   ├── service/          # 前台抓包服务
│   │   ├── session/          # 会话文件导入导出
│   │   └── ui/               # 界面与交互
│   ├── src/main/jniLibs/     # SunnyNet 原生 SDK（.so + .jar）
│   └── src/main/res/         # 布局、字符串、主题
├── gradle/
└── build.gradle.kts
```

## 技术栈

- **语言**：Java 11
- **UI**：Material Design、Navigation Drawer、ViewPager2、RecyclerView
- **存储**：ObjectBox 4.x
- **抓包核心**：SunnyNet Native SDK（`libSunnyNet.so` + `SunnyNet-v4.jar`）

## 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 网络通信 |
| `ACCESS_NETWORK_STATE` / `CHANGE_NETWORK_STATE` | VPN 状态检测 |
| `BIND_VPN_SERVICE` | VPN 抓包 |
| `FOREGROUND_SERVICE` | 前台抓包服务 |
| `POST_NOTIFICATIONS` | 通知栏显示停止按钮 |
| `QUERY_ALL_PACKAGES` | 列出已安装应用以供选择抓包目标 |

## 参与贡献

欢迎提交 Issue 与 Pull Request。提交前请确保：

- 代码风格与现有项目保持一致
- 变更范围聚焦，避免无关重构
- 在真机上验证抓包核心流程

## 相关链接

- SunnyNet SDK / 桌面版：[qtgolang/SunnyNet](https://github.com/qtgolang/SunnyNet)

## 常见问题

**Q：HTTPS 抓包显示乱码或无法解密？**  
A：请确认已安装并信任 SunnyNet 根证书；部分应用启用证书锁定（Certificate Pinning）时无法解密。

**Q：启动失败，提示端口被占用？**  
A：前往 **端口设置** 修改监听端口（建议使用 1024–65535）。

**Q：抓包开始后界面无法点击？**  
A：请在系统 VPN 授权弹窗中点击「确定」；若已授权仍异常，尝试重启应用。

**Q：VPN 被其他应用占用后自动停止？**  
A：Android 同一时间仅允许一个 VPN 连接；本应用检测到 VPN 断开后会自动停止抓包。

---

如有问题或建议，欢迎在 [Issues](../../issues) 中反馈。
