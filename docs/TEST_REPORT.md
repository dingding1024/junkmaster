# 测试报告（唯一发布 gate）

> **总体状态：PENDING（用户自测 + 反馈闭环进行中）** —— 本地静态、单测、构建、正式签名与权限校验**全部 PASS**；
> 轮次 1（vc2）~ 轮次 5（vc6）五次用户反馈已分别定位并修复，
> 已产出轮次 6（`1.0.5 / versionCode=7`）release 包，
> **等待老板覆盖安装、按 `docs/SELF_TEST.md` 的 T 组（主题配色）+ F 组（页面一致）+ P 组（查看路径）+ L 组（大文件索引）+ S 组（绑定回归）+ A/B/C/D 全组复测并回传诊断报告**。
> 测试方式已改为「用户自行侧载安装 + 自测反馈」，**不再要求 USB adb / `getprop` 作为 gate 前提**。
> **GATE（2026-11-06 变更）**：老板明确要求「发布到我的仓库」，gate 从「禁止一切远程写」调整为
> **「预发布放行、正式版仍卡测试」**：
> - **已放行**：本地建仓提交、打 `v1.0.5-rc1` 预发布 tag、推送公开仓库、由 CI 构建并发布 **Pre-release**。
> - **仍然禁止**：打正式 tag（`v1.0.5`）、把 rc 改标为正式版、把本报告改成 PASS —— 都要等真机复测。
> - **执行边界**：AI 全程未持有任何 GitHub 凭据，`git push` 由老板本人在本机执行；
>   本次会话内**没有任何远程写操作**由 AI 发起。

## 发行资产（预发布）

| 项 | 状态 |
|---|---|
| 本地 git 仓库 | 已初始化（`main`），158 个文件已提交，工作区干净 |
| 敏感文件排查 | `git ls-files` 无 `*.jks` / `keystore.properties` / `*.apk` / `delivery/` / `.workbuddy/` |
| 发行流水线 | `.github/workflows/release.yml`：单测 → 构建 → 验签(v2+冻结指纹) → 无 INTERNET → 发 Release |
| tag 语义 | 含 `-` 为 Pre-release（`v1.0.5-rc1`），不含为正式（`v1.0.5`） |
| CI 前置守卫 | tag 版本 == `versionName`、`CHANGELOG.md` 有对应小节、4 个 Secrets 齐备，否则直接失败 |
| 4 个 Secrets | 配置说明见 `docs/RELEASE.md` §0.3；`KEYSTORE_BASE64` 由本机 key 生成并做 base64 往返校验（SHA-256 一致） |
| 远程写入 | **AI 未执行任何 push / 建仓 / 打 tag / 建 Release / 写 Secrets** |

## 本地静态与构建结果（2026-11-06，轮次 6 最终）

| 项 | 结果 | 证据 |
|---|---|---|
| `gradlew.bat testDebugUnitTest` | **PASS** | 24 个测试类、**单变体 175 个用例**、0 failures / 0 errors / 0 skipped（轮次 5 为 22 类 / 163 例） |
| 大文件索引回归 | **PASS** | 新增 `LargeFileIndexTest`(9)：天数向下取整与时钟回拨、50 MB 与 30 天**双条件**、Download / `.apk` 去重、恒 `REPORT_ONLY` 且不可勾选、受保护目录仍进索引但为 L3、规则与后果 id 一致、Scanner 过滤目录 / 小文件 / 无 mtime、取消即停 |
| 路径探测回归 | **PASS** | 新增 `PathProbeTest`(3)：`emptyDirectory` 必须「可列举且子项为 0」；`childCount = -1`（列不出来）**绝不能**算空目录——轮次 2 的 178 项误判直接回归 |
| 既有回归 | **PASS** | `CleanExecutorTest` / `PathGuardTest` / `DualModeTest` / `BindTraceTest` / `DeepGateTest` / `ItemGroupingTest` / `ShizukuStateMachineTest` / `ReportSanitizerTest` / `SourceHygieneTest` 等全部保持通过 |
| `gradlew.bat assembleDebug assembleRelease` | **PASS** | BUILD SUCCESSFUL（1m33s），`minifyReleaseWithR8` + `lintVitalRelease` 通过 |
| `apksigner verify --print-certs` | **PASS** | `Verifies`（exit 0）；v2=true；DN `CN=JunkMaster Release`；SHA-256 `aaa47474…31557e` == 冻结指纹（与 vc2~vc6 同一把 key） |
| 版本 | **PASS** | aapt2 badging：`versionCode='7'` / `versionName='1.0.5'` / `minSdk 26` / `targetSdk 36` |
| APK 权限清单 | **PASS** | aapt2 dump：**无 `INTERNET`**；权限集合与 vc6 逐项一致；`AndroidManifest.xml` **零 diff**（2658 B / 16:43） |
| R8 后关键类 | **PASS** | dex 内检出 `report.large_old`（大文件索引规则 id，未被裁）/ `junkmaster-cleaner` / `bindUserService` |
| 源码卫生 | **PASS** | 0 处裸 `pm clear `；无 AccessibilityService / CLEAR_APP_CACHE / FileProvider；未引入网络依赖 |
| 本地 APK | 已产出 | `安卓垃圾清扫大师-v1.0.5-vc7-release.apk`（1,642,369 B），SHA-256 `24e983fd02cb1a95f5a279c025a4ccdd3734972f96791be0b795b52d531336a1` |

> 本轮编译期修掉一个隐性缺陷：`ThemePalette.tone(dark: Boolean)` 的参数名与枚举属性 `dark` 同名，
> `if (dark) dark else light` 三处都指向 Boolean 参数，返回类型被推断为 `Any` → 编译失败；改名为 `isDark`。
> 另有两页在统一页面框架后必须显式 `scrollable = false`：内部 `LazyColumn` + `weight(1f)` 与外层
> `verticalScroll` 叠加＝「无限高约束 + weight」，运行期会抛异常。

---

## 本地静态与构建结果（2026-11-05，轮次 5 最终）

| 项 | 结果 | 证据 |
|---|---|---|
| `gradlew.bat test` | **PASS** | 22 个测试类、**单变体 163 个用例**（debug + release 两套变体共 **326 次执行**）、**0 failures / 0 errors / 0 skipped** |
| Shizuku 绑定链路回归 | **PASS** | 新增 `BindTraceTest`(6)：序号与环形覆盖、`lastFailure` 定位、单行渲染与截断、空态占位、`BindResult` 原因透传 |
| 深度入口判定回归 | **PASS** | 新增 `DeepGateTest`(7)：READY 放行、空选优先拒绝、服务未运行 / 未授权 / 被拒 / 掉线 / 正在绑定 / 绑定失败 / 备选桥接**八种原因互不混淆**，失败原因原样透传 |
| 结果页分区与分组回归 | **PASS** | 新增 `ItemGroupingTest`(8)：三 Tab 互斥且并集覆盖、`tabOf` 路由、按 ruleId 聚合、组内按体积降序、组间按总体积降序、同类单份后果、三态勾选聚合、L3 组不可勾选 |
| 既有回归 | **PASS** | `CleanExecutorTest` / `NioFileDeleterTest` / `BasicCacheSourceTest` / `PathGuardTest` / `DualModeTest` / `ShizukuStateMachineTest` / `ReportSanitizerTest` / `LastCleanSummaryTest` / `LastScanSummaryTest` / `SourceHygieneTest` 等全部保持通过 |
| `gradlew.bat assembleDebug` | **PASS** | BUILD SUCCESSFUL（debug 变体仅内部对照，不交付） |
| `gradlew.bat assembleRelease` | **PASS** | BUILD SUCCESSFUL（1m28s），`minifyReleaseWithR8` + `lintVitalRelease` 通过 |
| 全链路 | **PASS** | 分步执行 `test`（1m16s）→ `assembleDebug assembleRelease`（1m28s），均 BUILD SUCCESSFUL；参数与上一轮一致（低峰值内存） |
| `apksigner verify --print-certs` | **PASS** | `Verifies`；v2=true；signers=1；DN `CN=JunkMaster Release`（**非 Android Debug**）；SHA-256 `aaa47474…31557e` == 冻结证书指纹（与 vc2~vc5 同一把 key）；RSA 3072 |
| CI 同证书 | **已配置** | `.github/workflows/release.yml` 用 `--print-certs` 断言指纹 == 冻结值，不一致即 `exit 1`；4 个 Secrets 必须来自同一把 key |
| 版本 | **PASS** | aapt2 badging：`versionCode='6'` / `versionName='1.0.4'` / `minSdk 26` / `targetSdk 36` |
| APK 权限清单 | **PASS** | aapt2 dump：无 `INTERNET`；权限集合与上一版逐项一致；**`AndroidManifest.xml` 零 diff**（时间戳仍为 16:43、2658 B） |
| 新增资源核对 | **PASS** | aapt2 dump resources 检出 `scan_tab_local*` / `group_count_format` / `deep_deny_bind_failed` / `settings_theme_title` / `shizuku_diagnose*` / `home_l1_button_hint` |
| R8 后 UserService 可加载性 | **PASS** | dex 内检出 `CleanerUserService` / `ICleanerUserService` / `junkmaster-cleaner` / `processNameSuffix` / `bindUserService`，keep 规则与四个 `UserServiceArgs` 参数均生效 |
| 源码卫生 | **PASS** | 全仓 0 处裸 `pm clear `；无 AccessibilityService / CLEAR_APP_CACHE / FileProvider；未引入任何网络依赖 |
| 本地 APK | 已产出 | `安卓垃圾清扫大师-v1.0.4-vc6-release.apk`（1,598,045 B），SHA-256 `d549dd4cd0116137511ffa8289e811efb6b498917e9489533df34e5185b8cc44` |

> **构建环境注记**：轮次 2 首次合并跑三个任务时 Gradle daemon 因系统瞬时内存不足崩溃
> （`hs_err_pid31896.log`：`There is insufficient memory for the Java Runtime Environment to continue` /
> `arena.cpp:191`，C2 CompilerThread malloc 失败），**与代码无关**。此后各轮沿用分步 + 降峰值参数：
> `--no-daemon --no-parallel --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1280m -Dfile.encoding=UTF-8`，
> 先 `test` 再 `assembleDebug assembleRelease`。项目 `gradle.properties` 未改。
>
> 构建环境：`D:\安卓清扫app` 含中文，本地统一走纯英文增量副本 `D:\AndroidDev\JunkMasterSrc`；
> 独立 shell 必须显式设置 `JAVA_HOME` / `ANDROID_HOME` / `GRADLE_USER_HOME` 三个环境变量（漏第三个会联网重下 Gradle 8.13 超时）。
> CI 在 ubuntu-latest 路径本身是 ASCII，workflow 会去掉本机代理行。
> 本轮单测首跑即全绿（9 个新增用例文件均一次编译通过）。

## 轮次 1 用户反馈与闭环（2026-11-05，A3 符号链接误杀）

老板回传结果页截图：**「一键清理安全项」扫描后无法清理**，`已删文件合计 0 / 0 B`、`失败项 1`、
`已中止（失败即停 / 掉线保护）：删除前复核拒绝：父路径含符号链接`。

**根因**：`/sdcard` 本身是系统符号链接，旧 `PathGuard` 对父链中任何 symlink 一律拒绝；
`CleanExecutor` 又把复核拒绝当成致命失败触发「失败即停」，第一批第一项即中止。
桌面 JVM 上 `/sdcard` 不存在，87 个纯 JVM 用例全绿也没暴露设备语义差异。

**修复**：新增 `StorageRoots` 挂载链白名单、`SymlinkProbe` 注入、真实路径逃逸检查；
复核拒绝改为跳过继续 + 结果页展示「安全拦截 N 项」。单测 87 → 101 例。

**状态：本地闭环；vc3 真机复测确认该层已通过，但暴露第二个独立 bug（见下）。**

## 轮次 2 用户反馈与闭环（2026-11-05 18:28，仍 0 删除）

老板回传 `report_format=1` 诊断报告（`version=1.0.1 (3)`、`signer_sha256` 正确）+ 沿用 18:10 的 vc2 老截图，
反馈「仍然是这个错误」。报告关键证据：`usable_bytes=0`、`status=未安装 Shizuku`、
`entries=1 / success=0 / failed=1 / rules=l1.empty_dir=1`；`latest_ts` 换算为 **18:27:36**，
说明 vc3 上确实又跑了一次清理。

**根因（第二个独立 bug）**：不可列举的骨架目录被误判成空目录并默认勾选 → 删除必然失败；
`CleanExecutor` 单项失败即 `break` → 整批 0 删除；目录递归删除存在误删隐患；
容量探针取了根分区；Shizuku 状态标签误导（实际是服务未启动）。

**修复（6 处）**：`FileEntry.readable` / `EmptyDirFinder` 双重过滤 / `PathGuard` 结构目录硬拦截 /
目录只删真空 / 单项失败跳过继续 + 连续 5 次才中止 / 诊断链升级 `report_format=2`。
单测 101 → **126 例**（+25）。

**状态：本地闭环；vc4 真机复测确认删除已能执行，但暴露第三个独立问题（见下）。**

## 轮次 3 用户反馈与闭环（2026-11-05 21:14，清了 67 项 / 0 B / 显示已中止）

老板回传 `report_format=2` 诊断报告（`version=1.0.2 (4)`）+ 新的结果页截图，
反馈「依然出现问题，无法正常操作常规清理」。关键证据：

```text
requested=178 / deleted=67 / freed_bytes=0 / failed=8 / stopped_early=true
stop_reason=连续 5 项删除失败，疑似权限或存储异常，已中止（fail-closed）
rules=l1.empty_dir=76
failed_reasons=目录已非空，已跳过（防误删）
shizuku_installed=true / shizuku_ping_binder=false
storage_root_listable=true / write_delete_test=ok / pathguard_sample_file=allowed
usable_bytes=80031567872 / capacity_probe_partition=external_storage
```

**这份报告证明前两轮的修复都已生效**：文件能删（`deleted=67`）、权限与 PathGuard 探针全部正常、
容量取对了分区。剩下的是三个新问题：

1. **我上一轮引入的语义回归**：「目录已非空，已跳过（防误删）」是防误删策略主动放弃，
   却被 `CleanExecutor` 计入 `failedCount` 并累加 `consecutiveFailures`，连续 5 个竞态即触发
   fail-closed，178 项只跑 75 项就停，并误报「系统性异常」。
2. **基础模式没有任何有体积的可删项**：178 项全是空目录（`l1.empty_dir`），释放 0 B 是如实结果。
   其它 L1 来源在共享存储上 0 命中；真正有体积的应用缓存在 `/sdcard/Android/data`，
   按 Android 11+ scoped storage 限制必须靠 Shizuku（本轮报告显示服务仍未启动）。
3. **结果页文案误导**：「已删文件合计 67 / 0 B」+ 红字「已中止（系统性异常）」，
   用户自然认为清理失败。另有 `sources()` 对 `/sdcard` 与 `/storage/emulated/0` 双根遍历造成重复枚举。

**修复（7 处，详见 `CHANGELOG.md` v1.0.3 / `docs/SELF_TEST.md` 第 0 节）**：

| # | 文件 | 改动 |
|---|---|---|
| 1 | `clean/FileDeleter.kt` | `DeleteOutcome.safeSkip` 三级语义：安全跳过 / 成功 / 硬失败 |
| 2 | `clean/CleanExecutor.kt` | `skippedCount` / `skippedReasons` / `emptyDirDeletedCount` / `onlyEmptyDirs`；连续失败只对硬失败计数；日志 reason 加「安全跳过：」前缀 |
| 3 | `shizuku/BridgeFileDeleter.kt` / `CleanerUserService.kt` | UserService 复核拒绝改为跳过继续；跳过回传 `safeSkip`；双端语义一致 |
| 4 | `scan/BasicCacheSource.kt`（新增） | 缓存目录 + 可再生后缀双命中才产出，排除 Android/data|obb|media 与 L3 |
| 5 | `scan/TempFileFilter.kt` / `rule/BuiltInRules.kt` | 补 `.crdownload` / `.download` / `.tmpl` / `.dctmp` |
| 6 | `AppServices.kt` | 单根遍历 `storageRoot()`，消除重复枚举；`sources()` 加入 `BasicCacheSource` |
| 7 | UI + 诊断链 | `CleanResultScreen` / `HomeScreen` 重写 + 「打开 Shizuku」按钮；`LastScanSummary`（新增）、`LastCleanSummary`、`ReportSanitizer`、`DiagnosticsReportBuilder` 升级 `report_format=3` |

**安全边界未放松**：新增缓存扫描额外要求「缓存目录 + 可再生后缀」双命中，用户文档 / 媒体 /
数据库 / L3 一律不默认勾删；目录只删真空、symlink / `..` / 白名单 / 掉线 fail-closed 全部保持。
单测 126 → **142 例**（+16）。

**状态：本地已闭环；vc5 真机复测确认「无红色告警、可正常清理」，但暴露 Shizuku 绑定层缺陷（见下）。**

> **关于「基础清扫清不出空间」的说明**：这不是 bug，是 Android 权限边界。普通 App 即使拿到
> `MANAGE_EXTERNAL_STORAGE` 也不能读写其他应用的 `/sdcard/Android/data` 缓存。
> 基础模式能安全清理的本来就是临时文件 / 下载片段 / 缩略图 / 日志 / 空目录，其中空目录不占空间。
> 要清出可观容量必须启动 Shizuku 服务解锁深度清理。本轮已在结果页与首页把这点写清楚，
> 并提供一键跳转 Shizuku 的入口。

## 轮次 4 用户反馈与闭环（2026-11-05 23:03，Shizuku 绑定 + 4 条 UI 建议）

老板回传 `report_format=3` 诊断报告（`version=1.0.3 (5)`）+ Shizuku 设置页截图，
反馈「这次测试不再有红色告警情况」，并提出 5 条改进。其中第 5 条是**真缺陷**，其余 4 条是体验优化。

### 缺陷：Shizuku 已授权却恒显示「服务未运行」，点按钮无反应

报告证据：

```text
status=服务未运行
shizuku_installed=true
shizuku_ping_binder=true          ← 服务在跑
shizuku_permission=granted        ← 本 App 已授权
uid=-1
sdk_from_userservice=0
raw_probe=                         ← UserService 从未连上
```

`pingBinder` 与 `checkSelfPermission` 都成立，说明卡在 **`bindUserService` 之后**。
两个具体原因：

1. **`UserServiceArgs` 缺参数**。Shizuku 官方 sample 要求同时设置
   `daemon(false)` / `processNameSuffix(...)` / `debuggable(BuildConfig.DEBUG)` / `version(versionCode)`。
   缺 `processNameSuffix` 时 UserService 进程名与宿主 App 相同，Shizuku 侧启动会失败且**不回调**；
   缺 `version` 时 App 升级后 Shizuku 会复用旧版本 UserService 进程（加载旧 APK 的 dex），
   表现为「装了新包却永远连不上」。
2. **`bindInFlight` 无超时**。原实现只在 `onServiceConnected` / `onServiceDisconnected` / `catch` 里复位标志，
   而这次回调压根没来 → 标志永久为 `true` → 之后所有 `bind()` 与刷新都被静默短路，
   这正是老板说的「刷新也无反应」「点『绑定深度服务』没有任何反应」。

**修复（5 处）**：

| # | 文件 | 改动 |
|---|---|---|
| 1 | `shizuku/ShizukuGateway.kt` | `UserServiceArgs` 补齐 daemon / processNameSuffix(`cleaner`) / debuggable / version 四个参数 |
| 2 | `shizuku/ShizukuGateway.kt` | `bindInFlight` 加 **8s 超时自愈**；`tryAutoBind` 同样按超时判定；`dropBridge` 不再清在途标志（避免重复 bind 堆积 connection） |
| 3 | `shizuku/BindTrace.kt`（新增） | 环形记录 ping / 授权 / bindUserService 调用与异常 / 回调耗时 / probe 结果；`BindResult` 携带可展示原因 |
| 4 | `shizuku/ShizukuGateway.kt` | 新增 `forceRebind()`（先真正解绑再重连）与 `diagnose()`（一键输出全链路实况） |
| 5 | `scan/DeepGate.kt`（新增）+ UI | 「进入深度确认」返回 8 种可区分原因，非 ok **必须弹窗**并给「去 Shizuku 设置」；首页 / 设置页常显「上次绑定：<原因>」 |

### 优化：按老板 4 条建议重做

| 反馈 | 处理 |
|---|---|
| 想有几种配色，可手动选 | 新增 `ui/theme/ThemePalette.kt` 6 套（海天蓝 / 青瓷 / 胭脂 / 黛山 / 鎏金 / 樱粉），DataStore 持久化，`MainActivity` 响应式应用；设置页色卡一行横向滚动 |
| 「一键清理安全项」不贴切 | 改名「**扫描清理手机**」+ 副文案「先只读扫描，你再逐项确认后才会删除」 |
| 后果说明逐项重复、太占地方 | 新增 `scan/ItemGrouping.kt` + `ui/components/ItemGroupCard.kt`：按 ruleId 聚合，**同类只渲染一份后果**，组内折叠为紧凑单行 |
| 结果太多，按是否需特殊权限分开 | `ScanResultScreen` 改为 3 个 Tab（免权限 / 需 Shizuku / 仅报告），每 Tab 内再分组；组头支持三态整组勾选 |

**安全边界未放松**：L3 组仍不可勾选、仅报告组仍不可删、`PathGuard` 双重校验 / 白名单 / 掉线 fail-closed 全部不变；
新增配色只影响 `ColorScheme`，不触碰任何扫描或删除逻辑；`AndroidManifest.xml` 仍零 diff、无 `INTERNET`。
单测 142 → **163 例**（+21）。

**状态：本地已闭环，等待老板覆盖安装 vc6 做 S 组 + U 组 + A/B/C/D 真机复测确认。**

## 交付轮次表

| 轮次 | versionCode | versionName | 文件名 | SHA-256 | 证书指纹 | 日期 | 反馈摘要 | 状态 |
|---|---|---|---|---|---|---|---|---|
| 1 | 2 | 1.0.0 | `安卓垃圾清扫大师-v1.0.0-vc2-release.apk` | `ebd9f003d49267b5ff1f4a585757924988b620971f49d609aeb660e9e5c78e4d` | `aaa47474…31557e` | 2026-11-05 | **A3 阻断**：0 删除，「父路径含符号链接」 | 已反馈 → 本地闭环 → `delivery\archive\` |
| 2 | 3 | 1.0.1 | `安卓垃圾清扫大师-v1.0.1-vc3-release.apk` | `8ca542f6640674ec785023310bd446e33cd468b96cc363bdedb74866721c6514` | `aaa47474…31557e` | 2026-11-05 | **仍 0 删除**：`l1.empty_dir` 失败 / 失败即停；Shizuku 服务未启动；容量取错分区 | 已反馈 → 本地闭环 → `delivery\archive\` |
| 3 | 4 | 1.0.2 | `安卓垃圾清扫大师-v1.0.2-vc4-release.apk` | `9604b20f650e375a4abc0ed98ba501f945784b2a60873fa9fd2d81223a3010ed` | `aaa47474…31557e` | 2026-11-05 | **已能删除 67 项但 0 B**：全是空目录；安全跳过被误计失败 → 误报「已中止」；基础扫描无有体积产出 | 已反馈 → 本地闭环 → `delivery\archive\` |
| 4 | 5 | 1.0.3 | `安卓垃圾清扫大师-v1.0.3-vc5-release.apk` | `06421587d28751675e178527635b8a9dbf3c058ee5029c74dc617bb0da33f5a4` | `aaa47474…31557e` | 2026-11-05 | **无红色告警，可正常清理**；但 Shizuku 已授权仍显示服务未运行、按钮无反应；另提 4 条 UI 建议 | 已反馈 → 本地闭环 → `delivery\archive\` |
| 5 | 6 | 1.0.4 | `安卓垃圾清扫大师-v1.0.4-vc6-release.apk` | `d549dd4cd0116137511ffa8289e811efb6b498917e9489533df34e5185b8cc44` | `aaa47474…31557e` | 2026-11-05 | **不再有红色告警**；Shizuku 绑定失败 + 深度按钮无反应（已修）；另提 4 条 UI 建议 | 已反馈 → 本地闭环 → `delivery\archive\` |
| 6 | 7 | 1.0.5 | `安卓垃圾清扫大师-v1.0.5-vc7-release.apk` | `24e983fd02cb1a95f5a279c025a4ccdd3734972f96791be0b795b52d531336a1` | `aaa47474…31557e` | 2026-11-06 | 待老板覆盖安装复测 | **待测（当前轮次）** |

> 后续每轮：`versionCode += 1`、`versionName` 补丁位递增，新文件名带轮次，不覆盖历史包；
> 同证书 + 更大版本号 = 用户直接覆盖安装，`files/clean_logs`、`crash/latest.txt`、
> `diag/last_clean.txt`、`diag/last_scan.txt`、配色偏好跨轮保留。
> 轮次 1~5 的旧包已移到 `delivery\archive\`，避免误装；`delivery\` 根目录只有 vc7。

## 真机前静态复核（早前已修复，仍待用户自测确认）

用户明确要求「打开 Shizuku 后即可得到类似 SD Maid 的深度清理权限」，逐行核对后修复 5 项：
① 冷启动自动绑定 UserService；② 消除授权回调产生的「假 READY」；③ 掉线 `LOST` 可恢复而非永久锁死；
④ `bind()` 幂等；⑤ 状态刷新时不再误调 `destroy()` 导致复用实例被永久标记已销毁。
这些修复仍需真机确认，对应下方 B1 / B2 / B5 / B6 / C1。详见 `CHANGELOG.md`。

## 用户自测清单（对齐 `docs/SELF_TEST.md`，全部待测）

> 结果以**用户反馈 + 诊断报告**为准；不再要求 adb、`device_baseline.sh` 或 `getprop`。
> `scripts/device_baseline.sh` 保留为 adb 环境下的可选诊断工具，**不是 gate 前提**。
> 本轮要求：**按「只读扫描 → 清理 → 再生成诊断报告」顺序做一次**，
> 并额外去 Shizuku 设置页点一次「一键诊断绑定」，让 `bind_trace` 与清理摘要同时带上真实数据。

**S 组 · Shizuku 绑定修复（轮次 4 反馈，本轮最优先）**
- [ ] **S1 覆盖安装后点「重新绑定」，状态能在数秒内变「已就绪」或明确显示失败原因**
- [ ] **S2 「一键诊断绑定」输出 `user_service_uid=2000` / `user_service_sdk=36`，`bind_trace` 中 `onServiceConnected` 与 `probe` 均 OK**
- [ ] S3 「刷新状态」有反馈文案（不再静默）
- [ ] **S4 深度确认按钮不可用时必弹窗说明原因（8 种原因之一），不再无反应**
- [ ] S5 弹窗「去 Shizuku 设置」可跳转
- [ ] S6 首页显示「上次绑定：<具体原因>」
- [ ] S7 冷启动自动补绑定，无需手动点按钮
- [ ] **S8 若 S1 失败，`bind_trace` 的 FAIL 行能指出卡点**

**U 组 · UI 优化验收（轮次 4 反馈）**
- [ ] **U1 6 套配色逐个点，全应用立即换色且重开 App 保持**
- [ ] **U2 首页主按钮显示「扫描清理手机」**
- [ ] **U3 结果页 3 个 Tab（免权限 / 需 Shizuku / 仅报告），各带项数**
- [ ] **U4 同类后果只出现一次（挂在组头）**
- [ ] U5 组头可展开 / 收起，组内为紧凑单行
- [ ] U6 组头三态整组勾选可用
- [ ] U7 仅报告 Tab 内 L3 无勾选框且显示拦截原因
- [ ] **U8 同样 1800+ 项时滑动长度明显变短**

**R3 组 · 轮次 3 回归（vc5 已通过，保留为长期回归）**
- [ ] R3-1 队列能跑完，结果页不出现红色「已中止（系统性异常）」
- [ ] R3-2 「已清理项 / 释放空间」「其中空目录 N 个」「安全跳过 N」「失败项 0」分开显示
- [ ] R3-3 释放 0 B 时结果页明确解释「空目录不占空间，不是清理失败」
- [ ] R3-4 「打开 Shizuku 启动服务」能拉起 Shizuku App
- [ ] R3-5 L1 列表无重复项
- [ ] R3-6 报告 `最近一次扫描分布` 段齐全且只有计数 / ruleId
- [ ] R3-7 报告 `skipped` / `skip_reasons` / `only_empty_dirs` / `empty_dirs_deleted` 齐全
- [ ] **R3-8 清理后零误删**

**A 组 · 不开 Shizuku（基础安全清扫）**
- [ ] A1 首启不闪退 / A2 权限引导正确
- [ ] **A3 「扫描清理手机」有结果** / A4 扫描阶段纯只读不删
- [ ] A5 L3 显示「已禁止」不可勾 / A6 Download 仅报告不删 / A7 白名单生效
- [ ] **A8 零误删抽查**（登录态 / 设置 / 聊天文字记录 / 照片完好）

**B 组 · 开 Shizuku（服务必须已启动）**
- [ ] B1 授权后自动绑定 + 冷启动自动绑定（无需手动点绑定）
- [ ] B2 不出现「假 READY」
- [ ] B3 `Android/data/*/cache` 可列举删除、`/data/data` 被拒
- [ ] B4 L2 逐项二次确认 + 强制勾选「我已知晓删除不可恢复」
- [ ] **B5 清理中停 Shizuku 立即中止；刷新自动重连且恢复期间不放行**
- [ ] B6 撤销再授权后 UserService 仍可用
- [ ] B7 `trim-caches`（失败但回退且如实报告不算阻断）

**C 组 · release 专项**
- [ ] **C1 R8 后 `bindUserService` 回归**（手写 Binder + 显式 tag + 四个 `UserServiceArgs` 参数）
- [ ] C2 无崩溃 / ANR
- [ ] C3 报告 `signer_sha256` 与冻结指纹一致、`version=1.0.4 (6)`、`report_format=4`

**D 组 · 测试诊断页**
- [ ] D1 一键生成 / D2 屏显可读 / D3 复制成功 / D4 分享能发回
- [ ] **D5 报告里看不到真实文件名 / 第三方包名 / 完整绝对路径；扫描分布只有计数；`bind_trace` 只有步骤名与异常类名**
- [ ] D6 崩溃后重进能看到脱敏栈

## 反馈与失败结论（按诊断报告 + 用户反馈填写）

- **A3「一键清理 0 删除 / 父路径含符号链接」：轮次 1 定位并本地修复；vc3 真机复测确认该层已通过。**
- **「vc3 仍 0 删除 / l1.empty_dir 失败 / 失败即停」：轮次 2 定位并本地修复；vc4 真机复测确认已能删除。**
- **「vc4 清了 67 项 / 0 B / 误报已中止」：轮次 3 定位并本地修复；vc5 真机复测确认「无红色告警、可正常清理」。**
- **「Shizuku 已授权却恒显示服务未运行 / 刷新无反应 / 深度确认按钮无反应」：轮次 4 定位（`UserServiceArgs` 缺四个参数 + `bindInFlight` 无超时）并本地修复（vc6），S 组待测。**
- **Shizuku 服务未启动**（轮次 1~3 的 `shizuku_ping_binder=false`）：轮次 4 报告已变为 `true` 且 `permission=granted`，
  说明服务侧已就绪，问题转为「UserService 绑定」层，本轮到该层修复。
- `trim-caches`：（待测：成功 / 被拒 → 回退 NIO 直删 / 再失败仅报告）
- `deleteApplicationCacheFiles`：（待测：成功 / 反射失败如实报告）
- SELinux 对 `/data/data` 的拒绝复核：（待测）
- Flyme「支付保护」/「USB 调试(安全设置)」影响：（待测）
- 冷启动自动绑定 / UserService 进程复用在 Flyme 后台策略下的表现：（待测，本轮 S7 覆盖）
- 诊断报告脱敏（含新增 `bind_trace` / 扫描分布 / skip 原因段）在真实数据下的表现：（待测，本地单测已覆盖规则）

## GATE 判定（M8 触发条件不变）

**PASS 当且仅当全部满足**：

1. 本地 `test` / `assembleDebug` / `assembleRelease` 全绿（0 failures，R8 + lintVital 通过）。
2. `apksigner --print-certs` 指纹 == 冻结证书指纹，且非 `CN=Android Debug`；v2=true。
3. aapt2 权限复核无 `INTERNET`，`AndroidManifest.xml` 零改动。
4. 用户按 `docs/SELF_TEST.md` 完成 **S / U / R3 / A / B / C / D 全组**，且「**零误删**」「**掉线中止**」「**R8 后 bindUserService**」「**深度入口必有弹窗提示**」四项无阻断问题。
5. 所有反馈问题已闭环（每轮 `versionCode` 递增、重新出包、用户覆盖安装复测）。
6. 用户在签字栏明确回复「**确认无问题 / 可以发版**」。

只有 PASS 才允许 M8 远程写（push / 建仓 / 打 tag / 写 Secrets / 发 Release）。
此前远程保持**零写入**；M8 沿用同一把 key、同一别名、同一 `applicationId`。

## 签字

| 项 | 结论 |
|---|---|
| 单测 failures | **0（本地 PASS，单变体 163 例）** |
| `./gradlew assembleRelease` + 正式签名 | **PASS（本地）** |
| 轮次 1 / 2 / 3 / 4 反馈问题 | **均已本地闭环，真机待复测** |
| 零误删 | **待测（用户自测）** |
| 用户自测全组 | **待测** |
| 总体 | **PENDING — 不得发布** |

签字人（用户）/ 日期：____________  确认方式（聊天回复 / 诊断报告回执）：____________  **PASS / FAIL**：____________
