# 安卓垃圾清扫大师（JunkMaster）

一款面向个人自用、本地侧载（GitHub Release）的 Android 垃圾清理工具，参考 SD Maid 的思路但**完全自研**，不 fork 上游、不使用其图标与翻译。定位魅族 21 Pro（Android 16 / Flyme AIOS 12.7.0.0A），最低支持 Android 8.0（API 26）。

> **删除不可恢复 · 本应用无回收站。** Android 没有系统回收站，任何被删文件都不会进入回收站，也不会提供撤销 / 回滚。本应用如实告知，不宣称可撤销。

> **身份与签名已冻结（2026-11-05）**：`applicationId` / `namespace` 固定为 `io.github.junkmaster.app`，**永不更改**（M8 不再按 GitHub owner 改包名，因为包名决定签名身份与升级链）。正式证书 DN = `CN=JunkMaster Release`，SHA-256 = `aaa47474e78934d2bf52bff6ecd5aabef55566b56b7990a981025d6c9b31557e`。`ShizukuProvider` 的 `authorities` 随之固定，无需单独修改。测试包与未来正式版共用同一把证书，后续修复包 `versionCode` 递增即可覆盖升级。

## 下载

到 [**Releases**](../../releases) 页下载最新 APK（`JunkMaster-<版本>.apk`，同目录附 `.sha256` 可校验）。

| 项 | 说明 |
|---|---|
| 系统要求 | **Android 8.0+**（minSdk 26），targetSdk 36，已适配 Android 16 |
| 安装方式 | 直接侧载；覆盖旧版**无需卸载**（同一把冻结证书，`versionCode` 递增即可升级，本地数据与日志保留） |
| 深度清理 | 需先安装并授权 [Shizuku](https://shizuku.rikka.app/)；**不装也能用基础清理** |
| 网络 | APK **不含 `INTERNET` 权限**，全程离线，扫描结果与路径不出本机 |
| 包名 | `io.github.junkmaster.app`（冻结，勿改） |

> `-rc` / `-beta` 结尾的 tag 是**预发布版**（如 `v1.0.5-rc1`），真机复测通过后才会转正式版。
> 建仓、配 Secrets、打 tag、把预发布转正 —— 全套流程见 [`docs/RELEASE.md`](docs/RELEASE.md)。


## 0. 两种使用模式（老板明确要求）

| | **基础模式**（不开 Shizuku） | **深度模式**（Shizuku 已授权并绑定） |
|---|---|---|
| 前置条件 | 授予「所有文件访问权」（首页有引导按钮）；用量访问权可选，用于读取各 App 缓存大小 | 在基础模式之上，**Shizuku 服务已在运行** → 在本 App 内授权；授权后自动绑定 UserService，冷启动也会自动补绑定，无需手动点「绑定」 |
| 能清什么 | 共享存储中的 `.tmp` 临时文件、缩略图、空目录、崩溃 / ANR 日志、本 App 自身缓存（L1 一键直清） | 基础全部内容 **+** `/sdcard/Android/data/<pkg>/cache`、`code_cache` 应用缓存（L1 可直接清）、卸载残留 / 聊天附件缓存 / 系统日志（L2 逐项二次确认） |
| 怎么删 | App 进程内 NIO 直删，**完全不依赖 Shizuku** | 应用缓存等深度项由 shell UID 2000 的 UserService 在它自己的进程内列举与删除（普通 App 连 `Android/data` 都列不出来） |
| 二次确认 | 缓存类直接清，无弹窗 | L2 逐项勾选 + 默认全不勾 + 强制勾选「我已知晓删除不可恢复」 |
| 掉线保护 | 不受影响 | Shizuku 掉线立即中止所有深度操作（fail-closed），未就绪的深度项降级为「仅报告」；服务恢复后回首页刷新自动重连，恢复期间仍不放行 |

**一句话：不开 Shizuku 也能正常做基础安全清扫；打开 Shizuku 后才解锁类似 SD Maid 的深度清理权限。** 两条路径在 `AppServices.sources()` / `ScanSources.build()` 与 `ScanResultViewModel.cleanL1()` 中显式分流，互不阻塞；Shizuku 不可用时不会产生 App 删不掉的项，避免「失败即停」拖垮基础清理。

`READY` 状态的唯一判据是「Shizuku 存活 + 已授权 + UserService bridge 存活」三者同时成立，**授权回调本身不会产生 READY**（`bindUserService` 是异步的）。绑定决策由纯 JVM 的 `ShizukuBindPolicy` 给出：冷启动、在 Shizuku Manager 里提前授权过、掉线恢复都走自动补绑定；设置页的「绑定 / 解绑」按钮保留作为兜底与调试入口。

## 0.1 本轮交付：用户自测闭环

测试方式改为**用户自行侧载安装 + 自测反馈**，不再要求 USB 数据线连接电脑：

1. 我产出一份 **release 包**放到 `D:\安卓清扫app\delivery\`，附 SHA-256 与 `docs/SELF_TEST.md`。
2. 用户按 `docs/SELF_TEST.md` 在魅族 21 Pro 上自测（S 绑定 / U UI / A 基础 / B 深度 / C release 专项 / D 诊断页）。
3. 遇到问题时打开 App 内「**测试诊断**」页 → 一键生成脱敏报告 → 复制或分享发给我。
4. 我按反馈修复 → `versionCode += 1` → 重新出包 → 用户**直接覆盖安装**（同证书，数据保留）。
5. 循环复测 → 用户明确确认「无问题」→ 发**正式** Release（`v1.0.5`）。

> **发行状态**：本地 git 仓库、GitHub Actions 发行流水线（`.github/workflows/release.yml`）、
> 4 个 Secrets 的配置说明均已就绪，见 [`docs/RELEASE.md`](docs/RELEASE.md)。
> 当前推送的是 **`v1.0.5-rc1` 预发布版**；`docs/TEST_REPORT.md` 仍为 **PENDING**，
> 真机复测通过后才打正式 tag。

> **当前处于轮次 6**（`1.0.5 / versionCode=7`）：按用户第 6 轮反馈改了四件事 ——
> **整体界面主题**（6 套配色 × 浅 / 深 / 跟随系统，整屏背景、卡片、正文一起变，不再只换按钮颜色）、
> **首页底部入口整行可点 + 四个二级页统一框架**（不再折行、不再各写一套）、
> **点任意条目查看实际路径 / 文件位置**（完整路径 + 当场重探 + 交给系统文件管理器打开）、
> **超过 30 天的大文件索引**（>= 50 MB 且 >= 30 天，只索引不删，带完整路径）。
> 与 vc2~vc6 同一把证书，**直接覆盖安装即可**；请按 `docs/SELF_TEST.md` 的 **T 组（主题）+ P 组（路径）+ L 组（大文件）** 先复测。
> 轮次 1~5 的包都在 `delivery\archive\`，根目录只有 vc7，别装错。

> **关于「基础清扫清不出空间」**：Android 11+ 起普通 App 即使有「所有文件访问权」也不能读写
> 其他应用的 `/sdcard/Android/data` 缓存。基础模式能安全清理的本来就是临时文件 / 下载片段 /
> 缩略图 / 日志 / 空目录，其中空目录不占空间；要清出可观容量必须**启动 Shizuku 服务**解锁深度清理。
> 这是系统权限边界，不是 App 缺陷 —— 结果页与首页现在会如实说明并提供启动入口。

诊断页报告（`report_format=4`）含版本与证书指纹、设备与系统、权限状态、Shizuku 状态与
`shizuku_installed` / `shizuku_ping_binder` / `probe` 原始输出、共享存储容量、
**绑定链路**（`last_bind_*` / `user_service_args` / `bind_trace`，FAIL 行即卡点）、
**最近一次扫描分布**（只有分区计数与 ruleId）、**清理失败 / 安全跳过原因与最近一次清理结果摘要**、
**文件系统实测探针**（共享存储能否列举 / 写删、挂载链符号链接数量、PathGuard 对样例文件与结构目录的判定）、
清理日志聚合计数、脱敏崩溃栈、用户附注；
**默认且唯一版本就是脱敏版**（隐藏第三方包名 / 用户文件名 / 完整路径，清理日志只给计数与代码生成的原因），
屏显同样脱敏。报告只在内存里，仅由用户主动点击「复制 / 分享」才外发；本 App 不声明 `INTERNET`、不自动上传。
生成报告时会创建并立即删除一个 1 字节临时文件用于探测写入权限，不读取任何用户文件内容。详见 `PRIVACY.md`。

## 1. 能力范围（四类）

| 类别 | 归属 | 处理方式 |
|---|---|---|
| 应用外部 `cache` / `code_cache`、`.tmp`、缩略图、空目录、崩溃 / ANR 日志 | **L1 安全** | 一键直清、默认全选、无二次弹窗 |
| 卸载残留、聊天应用附件缓存、系统日志 | **L2 需确认** | 逐项勾选、默认全不勾、独立确认页 + 强制勾选「我已知晓删除不可恢复」 |
| Download 大文件 / 旧 APK | **仅报告 REPORT_ONLY** | 只报告大小与路径，**本 App 不自动删** |
| `/data/data`、`/system`、`DCIM/Pictures/Camera`、`Android/obb`、`databases`、`shared_prefs`、凭据路径 | **L3 禁止** | 硬编码不可勾选，App 侧与 UserService 侧双重校验 |

每条扫描结果在 UI 都会常显「是否安全」徽标 + 「删除后会有什么后果」中文说明；首页 / 确认页 / 关于页常驻「删除不可恢复、无回收站」提示。

普通 App 在 Android 11+ 无法直接访问或删除他应用 `/sdcard/Android/data`、`Android/obb`（连「所有文件访问权」也不授予），因此**深度清理以 Shizuku（shell UID 2000）为唯一引擎**：应用缓存只能由 UserService 在 shell 进程里枚举，App 进程拿到的只是路径清单。基础 L1 清理不依赖 Shizuku，可独立使用。AppCacheScanner（App 进程遍历）已废弃，改为 `ShizukuCacheItemSource`。

## 2. 技术与版本链

| 项 | 值 |
|---|---|
| JDK | 17 |
| Gradle Wrapper | 8.13 |
| AGP | 8.11.1 |
| Kotlin | 2.1.20 + `org.jetbrains.kotlin.plugin.compose` |
| Compose BOM | 2025.08.01 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Shizuku-API | `dev.rikka.shizuku:api:13.1.5` + `:provider:13.1.5` |
| 当前版本 | `1.0.5 (7)`；每轮 `versionCode += 1`（轮次 1~5 的 `1.0.0 (2)` / `1.0.1 (3)` / `1.0.2 (4)` / `1.0.3 (5)` / `1.0.4 (6)` 已归档到 `delivery\archive\`） |

版本统一由 `gradle/libs.versions.toml` 管理。单 Activity + Jetpack Compose（Material 3 中文），Navigation-Compose 管理 9 个页面（含「测试诊断」）。

**Shizuku UserService 用手写 Binder 接口**（`app/src/main/java/.../shizuku/ICleanerUserService.kt`），不触发 AIDL 编译：AGP 8.11.1 在 Windows 中文路径下会用 UTF-8 解码 `aidl.exe` 生成的依赖文件并抛 `MalformedInputException`。手写 Binder 与 AIDL 生成类功能等价；协议参考保留在 `docs/aidl-reference/`。同时 `gradle.properties` 加了 `android.overridePathCheck=true`。

## 3. 编译与测试

```bash
# 工具链（全部落 D 盘），三个环境变量必须显式设置，
# 缺 GRADLE_USER_HOME 会让 wrapper 联网重下 Gradle 8.13 而超时：
#   JAVA_HOME='D:\AndroidDev\jdk-17'
#   ANDROID_HOME='D:\AndroidDev\Android\Sdk'
#   GRADLE_USER_HOME='D:\AndroidDev\gradle-home'

# 签名：项目根 keystore.properties（不入库）指向 D:\AndroidDev\keystore\junkmaster-release.jks。
# 缺该文件时 assembleRelease 由硬闸直接拦停；debug 变体仍用默认 debug 签名，仅内部对照。
# keystore 口令见 D:\AndroidDev\keystore\PASSWORDS.txt，请自行离线备份。

# Windows 中文路径注意：本地构建必须用真实纯英文副本
#   D:\AndroidDev\JunkMasterSrc   <- 从 D:\安卓清扫app 增量同步（排除 build/.gradle/.git/*.apk/*.jks）
# CI（ubuntu-latest）路径本身是 ASCII，无需副本；workflow 会去掉本机代理行。

.\gradlew.bat test              # 纯 JVM 单测（0 failures 才继续）
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease   # release 一律用冻结的正式 keystore 签名
```

`./gradlew test` 为纯 JVM 单测，仅依赖 JUnit4 + `kotlinx-coroutines-test`，不引入 Robolectric 或任何 `android.*` 运行时类。当前 **24 个测试类 / 单变体 175 个用例**（debug + release 两套变体共 350 次执行），0 failures / 0 errors / 0 skipped。

> **设备语义盲区（三轮真机反馈的教训）**：桌面 JVM 上 `/sdcard` 不存在，且所有目录都能列举；
> 真机上 `/sdcard` 本身是系统符号链接，`/sdcard/Android/data` 又可能 `listFiles()` 返回 `null`；
> Shizuku 的 `pingBinder` / `checkSelfPermission` 都正常时，UserService 仍可能压根没起来。
> ① v1.0.1 修复「父路径含符号链接 → 所有扫描项被复核拒绝 → 0 删除」；
> ② v1.0.2 修复「不可列举的骨架目录被误判为空目录 → 单项删除失败即停 → 0 删除」；
> ③ v1.0.3 修复「防误删的安全跳过被误计为失败 → 连续 5 次即 fail-closed 中止 → 队列跑不完还谎报系统性异常」；
> ④ v1.0.4 修复「`UserServiceArgs` 缺 processNameSuffix/version 导致 UserService 起不来」+
>   「`bindInFlight` 无超时导致刷新与绑定按钮永久静默」；
> ⑤ v1.0.5 修复「两套页面框架并存 → 首页内容变多后直接溢出、底部入口被挤掉 / 各页 UI 不一致」+
>   「`LazyColumn` + `weight(1f)` 与 `verticalScroll` 叠加会在运行期抛异常」（统一框架用 `scrollable` 显式区分）。
> 现在 `PathGuard` 通过可注入 `SymlinkProbe` 让纯 JVM 单测模拟挂载链，`StorageRoots` 只放行系统挂载链
> 并硬拦截结构目录；`FileEntry.readable` 记录可列举性；目录只在「可列举且确实为空」时才删，绝不递归清空；
> `DeleteOutcome.safeSkip` 把「主动放弃」与「真实 IO 失败」分开，只有连续硬失败或掉线才中止；
> `BindTrace` 把绑定每一步留痕，`ShizukuBindPolicy` / `DeepGate` 让「不可用」的每种原因都可区分、可展示。
> 诊断页另有 `StorageProbe`（真机实测）、`LastScanSummary`（扫描分布）与 `bind_trace`，
> 以无路径文本回传，弥补纯 JVM 覆盖不到的语义差异。

> **本机内存吃紧时**（合并跑三个 task 可能让 Gradle daemon 因 native OOM 崩溃，与代码无关）：
> 分步执行并降低峰值，例如
> `gradlew.bat test --no-daemon --no-parallel --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx1280m -Dfile.encoding=UTF-8"`，
> 再单独跑 `assembleDebug assembleRelease`。

`apksigner.bat verify` 需要显式 `JAVA_HOME`；CI 里由 `.github/workflows/release.yml` 用 `--print-certs` 断言证书指纹必须等于冻结值，防止 CI 用错 key 导致覆盖升级链断裂。

### 3.1 可选 adb 诊断（不再是发布 gate 前提）

`bash scripts/device_baseline.sh` 在恰好有 USB adb 环境时可补充采集 getprop / logcat，默认纯只读。
**改为用户自测后，本脚本不是发布 gate 前提**；设备与系统信息由 App 内「测试诊断」页采集并随报告回传。

## 4. Shizuku 授权与 Flyme 提示

> 前提：**手机上的 Shizuku 服务已经在运行**（只装了 Shizuku App 不够，必须在 Shizuku 里启动服务；
> 无需电脑 adb 做本 App 测试）。诊断报告的 `shizuku_installed` / `shizuku_ping_binder` 会把
> 「装了」与「跑着」分开显示；`status=未检测到 Shizuku 服务` 即表示服务没启动。

1. 在本 App 的「Shizuku 设置」页点「请求 Shizuku 权限」，弹窗选允许。
2. **授权后自动绑定 UserService**，回首页刷新即可看到「已就绪」；冷启动同样自动补绑定，正常无需手动点「绑定」。
3. **Flyme 用户**：若授权 / 绑定失败，请在「开发者选项」中关闭 **「Flyme 支付保护」**（Shizuku 官方 FAQ 对 Flyme 的唯一提示），并确认「USB 调试(安全设置)」。
4. 备注：通过 ADB / 无线调试启动的 Shizuku 服务，**每次手机重启后都需要重新启动服务**；服务恢复后回首页刷新会自动重连。
5. Shizuku 掉线（binder 死亡）时状态机立即切 `LOST` 并**中止所有深度操作（fail-closed）**，基础 L1 不受影响；刷新后自动重连，恢复期间仍保持不可放行态。

`trim-caches` 在 shell 上是否被拒绝存在系统差异，因此采用**实测 + 回退**：成功则按实际释放量记账（不谎报）；被拒则回退到 NIO 直删 `cache` / `code_cache`；再失败仅如实报告原因。结论以 `docs/TEST_REPORT.md` 的用户自测反馈为准。

## 5. 权限与隐私

- 声明：`MANAGE_EXTERNAL_STORAGE`、`PACKAGE_USAGE_STATS`、`QUERY_ALL_PACKAGES`、`READ_MEDIA_*`（仅列举）、`POST_NOTIFICATIONS` / `FOREGROUND_SERVICE`（可选）、`moe.shizuku.manager.permission.API_V23`。
- **不声明** `INTERNET`：无网络、无广告、无统计 / 崩溃上报 SDK。
- **不声明** `CLEAR_APP_CACHE`、不使用 `AccessibilityService`、不使用 `FileProvider`（分享用纯文本 `EXTRA_TEXT`）。
- **代码级禁用** 裸 `pm clear`（带包名参数会删除应用全部数据）。
- 删除日志与崩溃记录只写本应用私有目录，**不记录文件内容、不自动上传**。诊断报告已脱敏，仅在用户主动点击复制 / 分享并自行选择目标时外发；屏显用同一份脱敏版。

详见 `PRIVACY.md`。

## 6. 商店与侧载说明

商店上架基本不可行，本应用定位**个人自用 + GitHub Release 侧载**。侧载需在 Flyme 允许「安装未知来源应用」；Google 自 2026 起逐步推开开发者验证，本应用**不承诺长期可安装性**。

> **若手机上装过 2026-11-05 之前的旧包**（`CN=Android Debug` 证书）：新包证书不同，**必须先卸载旧包**再安装，否则会报「应用未安装 / 签名冲突」。卸载会清掉旧包的私有日志，可接受。

## 7. 目录与文档

- `delivery\`：**当前轮次**的 release APK + SHA-256 + `SELF_TEST.md`（不入库）；历史轮次包在 `delivery\archive\`，勿装
- `clean/StorageRoots.kt`：Android 系统存储挂载链白名单 + 共享存储结构目录判定（详见 `CHANGELOG.md` v1.0.1 / v1.0.2）
- `clean/FileDeleter.kt`：`DeleteOutcome.safeSkip` 三级语义，区分「防误删跳过」与「真实 IO 失败」（v1.0.3）
- `scan/BasicCacheSource.kt`：基础 L1 缓存文件扫描（缓存目录 + 可再生后缀双命中，零误删优先，v1.0.3）
- `shizuku/BindTrace.kt`：UserService 绑定链路留痕（步骤 / 耗时 / 异常类名），供诊断页与设置页自证（v1.0.4）
- `scan/DeepGate.kt`：深度入口放行判定，把「不可用」拆成 8 种可展示原因（v1.0.4）
- `scan/ItemGrouping.kt` / `ui/components/ItemGroupCard.kt`：扫描结果三分区 + 按规则聚合折叠，同类后果只渲染一次（v1.0.4）
- `ui/theme/ThemePalette.kt`：6 套配色 × 浅色 / 深色两份**完整色板**（背景 / 表面 / 5 级容器色 / 正文 / 描边全部派生）
  + `ThemeMode`（跟随系统 / 浅色 / 深色），DataStore 持久化；语义红不随主题变（v1.0.4 引入，v1.0.5 重做）
- `ui/components/ScreenFrame.kt`：**全应用唯一页面框架**（固定头部 + 常驻不可恢复提示 + 可选滚动内容区），
  `scrollable=false` 供内部自带 `LazyColumn` 的页面使用（v1.0.5）
- `scan/LargeFileIndex.kt` / `scan/PathProbe.kt`：超过 30 天的大文件索引（>= 50 MB，只索引不删）与路径实时探测（v1.0.5）
- `ui/components/PathDetailDialog.kt` / `ui/FileOpener.kt`：点开看完整路径 + 当场重探 + 交给系统文件管理器打开（v1.0.5）
- `clean/LastCleanSummary.kt` / `diag/LastScanSummary.kt`：最近一次清理结果 / 扫描分布摘要（无路径，落私有目录供诊断页自证）
- `diag/StorageProbe.kt`：真机文件系统 / PathGuard 实测探针（只输出类别与布尔值，不输出路径）
- `docs\SELF_TEST.md`：**用户自测清单**，S 组（Shizuku 绑定）+ U 组（UI 优化）+ R3 回归 + A/B/C/D 用例、安装前准备、报告发回方式与反馈模板
- `docs\TEST_REPORT.md`：**唯一发布 gate**，用户自测 + 反馈闭环判定 PASS/FAIL
- `docs\ENV.md`：工具链、冻结签名与证书指纹
- `scripts\device_baseline.sh`：可选 adb 诊断（不再是 gate 前提）
- `CHANGELOG.md`：分级说明、Shizuku 依赖、Flyme 提示、已知限制
- `LICENSE`：Apache-2.0；`NOTICE`：第三方归属

## 8. 发布纪律

`docs/TEST_REPORT.md` 判定 **PASS 且老板确认无问题前**，不做任何远程写操作（不 push、不建仓、不打 tag、不发 Release、不写 Secrets）。PASS 的来源是**用户自测全组通过 + 所有反馈问题闭环 + 用户明确回复确认**，不再是 USB adb 实测。M8 是最后阶段，沿用同一把 key、同一别名、同一 `applicationId`。
