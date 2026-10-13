# Glass Browser · 纯 Compose + Material 3 液态玻璃浏览器

从零实现：**Kotlin + Jetpack Compose + Material 3，零 XML 布局、零 EditText、零 View 体系**（唯一保留的 XML 是 Manifest 与字符串/矢量资源，Android 上无替代）。

| 你要的东西 | 在哪个文件 |
|---|---|
| 液态玻璃库调研（7 个库逐项评估）+ 选型结论 | **[`docs/液态玻璃库评估与选型.md`](docs/液态玻璃库评估与选型.md)** |
| 坑与解决方案（47 条，分五类） | **[`docs/踩坑与规避清单.md`](docs/踩坑与规避清单.md)** |
| 项目结构（完整目录树） | 见下方「项目结构」 |
| Gradle 依赖 | `gradle/libs.versions.toml` + `app/build.gradle.kts` |
| AndroidManifest（默认浏览器 + 权限） | `app/src/main/AndroidManifest.xml` |
| Compose UI（Scaffold + TopAppBar + 玻璃搜索栏 + 设置键） | `ui/BrowserScreen.kt`、`ui/GlassSearchBar.kt` |
| WebView 封装（返回键 / 进度 / 标题 / favicon） | `web/BrowserWebView.kt` |
| 网址识别 / 补协议 / 跳转 / 搜索 | `core/UrlResolver.kt`（**60/60 实测通过**） |
| 玻璃集成 + **三档自动降级** | `glass/GlassTier.kt`、`glass/GlassSurface.kt` |

---

## 一分钟结论

- **玻璃库选 Haze 2.0.1**（`dev.chrisbanes.haze:haze` + `haze-blur`）。理由：Compose 原生、Maven 坐标稳定、维护活跃、**采样源可控**——浏览器场景背后是 WebView，采样必须由我们决定。7 库完整对比见评估文档。
- **降级靠自己分三档**，不寄希望于库的内部判断：
  | 档 | 判定 | 行为 |
  |---|---|---|
  | Full | API 33+ 且非低内存 | 24dp 实时模糊 |
  | Reduced | API 31–32 或低内存 | 12dp 实时模糊 |
  | **Scrim** | API ≤30 或低内存强制 | **完全不做模糊**，半透明纯色 + 描边，零离屏分配 |
- **"输入网址不跳转"的三个真凶**都已修：`shouldOverrideUrlLoading` 返回 `true` 不加载、只判 `startsWith("http")`、`host:port` 被误当协议。

---

## 项目结构

```
glass-browser/
├── settings.gradle.kts / build.gradle.kts
├── gradle/libs.versions.toml                 # 版本目录（Haze / Compose / Coil）
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml           # 默认浏览器 Intent + 包可见性 + 明文流量
│       │   ├── java/com/glassbrowser/
│       │   │   ├── MainActivity.kt           # 装配 + 外部 Intent（冷/热启动）+ 返回键
│       │   │   ├── core/
│       │   │   │   ├── UrlResolver.kt        # ★ 纯 Kotlin：网址/搜索判定、补协议、展示文本
│       │   │   │   ├── SearchEngine.kt       # 引擎模板 + 容错反查
│       │   │   │   └── BrowserState.kt       # 地址栏状态（url/title/favicon/progress/canGoBack）
│       │   │   ├── glass/
│       │   │   │   ├── GlassTier.kt          # ★ 三档降级判定（SDK_INT + isLowRamDevice）
│       │   │   │   └── GlassSurface.kt       # ★ glassSurface() 一个 modifier 收口全部玻璃
│       │   │   ├── ui/
│       │   │   │   ├── BrowserScreen.kt      # Scaffold + TopAppBar + WebView + 进度条
│       │   │   │   ├── GlassSearchBar.kt     # 玻璃搜索栏（点开编辑 / ✓ / IME 自适应）
│       │   │   │   ├── FaviconImage.kt       # favicon 三重兜底（修裂图）
│       │   │   │   ├── SettingsScreen.kt     # 设置页（引擎 / 玻璃档位 / 网页延伸）
│       │   │   │   └── Theme.kt              # 深色 M3 主题（玻璃需要层次背景）
│       │   │   ├── web/BrowserWebView.kt     # ★ WebView 封装（导航放行收口）
│       │   │   └── settings/
│       │   │       ├── BrowserPrefs.kt       # 设置持久化（StateFlow，改完立刻生效）
│       │   │       └── SystemSettings.kt     # 跳系统"默认应用"页（带 ROM 兜底）
│       │   └── res/                          # 只有字符串/颜色/矢量图（无 layout）
│       └── test/java/com/glassbrowser/core/
│           └── UrlResolverCheck.kt           # ★ 60 条断言，脱离设备可跑
├── docs/
│   ├── 液态玻璃库评估与选型.md
│   ├── 踩坑与规避清单.md
│   └── UrlResolverCheck-输出.txt             # 实测输出
└── tools/run-urlcheck.sh                     # 一键复跑核心逻辑验证
```

## 跑起来

```bash
# ① 编译安装（需要 Android Studio / SDK 36）
./gradlew :app:installDebug

# ② 只验证"网址识别 / 搜索分发"逻辑（不需要设备，也不需要 Gradle 构建）
bash tools/run-urlcheck.sh
# → 结果: 60 通过, 0 失败

# ③ 验证默认浏览器 / 外部链接
adb shell am start -a android.intent.action.VIEW -d "https://yutongxue.cc.cd"
adb shell am start -a android.intent.action.WEB_SEARCH --es query "神秘海函数"
```

## 已实测的证据

`core/UrlResolver.kt` + `SearchEngine.kt` 是用**真实 Kotlin 编译器编译、真实 JVM 运行**过的，不是"看起来对"：

```
结果: 60 通过, 0 失败
```

覆盖：裸域名 / 多级域名 / 带与不带协议 / 大写协议 / 端口 / 路径查询锚点 / 中文与空格 /
纯数字 / `abc` / `1.5` / localhost / 私网 IP / 公网 IP / 越界 IP /
`javascript:` / `intent:` / `file:` / `mailto:` / 未知协议 / 零宽字符与 BOM 清洗 /
空格编码 `%20` 而非 `+` / 输入法动作键自适应。

**验证过程中修掉的两个真 Bug**（细节见评估文档 §3.4）：
1. `localhost:8080`、`example.com:8080` 被误判成"未知协议"→ 去搜索（用户视角：带端口的地址跳不动）；
2. 半截域名 `yutongxue.` 当网址 → 会让 WebView 打开必然报错的页面；现在只有 TLD 完整（≥2 字母）才跳。

## 未验证项（如实说明）

- **Compose / Android 层的代码没有在本机编译**：本环境没有完整 Android 构建链（AGP + SDK + 依赖下载）来跑 `assembleDebug`。
  纯 Kotlin 部分（`core/`）已真编译真运行；UI/WebView/玻璃代码是按 Haze 2.0.1 官方 API 与 Material 3 规范编写并逐行核对导入的，
  请在 Android Studio 里首跑一次 `./gradlew :app:assembleDebug`，若有版本差异按 `libs.versions.toml` 调整。
- **玻璃观感与帧率未在真机采样**：需要真机验证（建议用 Macrobenchmark 量一下滚动帧率）。
- **默认浏览器**需在系统设置里由用户确认选择，App 无法自行抢占。
