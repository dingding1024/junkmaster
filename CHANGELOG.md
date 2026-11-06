# 更新日志（安卓垃圾清扫大师 / JunkMaster）

> **身份与签名已冻结（2026-11-05）**：`applicationId` / `namespace` 固定为 `io.github.junkmaster.app`，**永不更改**（M8 不再按 GitHub owner 改包名）。正式证书 DN = `CN=JunkMaster Release`，SHA-256 = `aaa47474e78934d2bf52bff6ecd5aabef55566b56b7990a981025d6c9b31557e`。测试包、后续修复包与未来正式版共用同一把证书，`versionCode` 递增即可覆盖升级；M8 沿用同一 key / 同一别名 / 同一包名。

> **删除不可恢复 · 无回收站**：Android 没有系统回收站，任何被删文件都不会进入回收站，也不提供撤销 / 回滚。本应用如实告知，不宣称可撤销。

---

## v1.0.5 (7) — 轮次 6：配色改为整体界面主题 + 路径查看 + 大文件索引（2026-11-06，待用户覆盖安装复测）

老板轮次 6 反馈 4 条，全部为体验问题（无红色告警、无崩溃）：

```text
1. 目前主题色不对，目前的主题色是按键色，我说的是整体界面主题色，如深色背景青瓷色按键配色这种搭配
2. 首页界面的最下面设置与白名单，关于与许可，段行有问题，下面三个界面的 ui 效果应该保持一致
3. 清理目录的时候增加可点选查看实际手机目录路径文件位置
4. 添加手机超过 1 个月以上的大文件索引显示，并给出路径方便我查看
```

### 1. 主题：从「只换按键色」改为「整套界面色板 + 明暗模式」

上一轮每套配色只覆盖 `primary / tertiary / surfaceVariant`，其余仍是 Material 3 默认的紫灰色，
卡片的容器色（`surfaceContainerHighest` 系列）也还是默认值 —— 所以看起来「只是按钮换了颜色」。

现在 `ThemePalette` 每套方案都带**浅色 + 深色两份完整色板**（`PaletteTone`）：
背景、表面、5 级容器色、正文、次要文字、描边、投影色全部由配色派生，卡片与底板一起变。
容器色用 `lerp` 现算，不手写 6 套 × 7 个值，同一套配色内明暗层次单调。

同时新增 `ThemeMode`：跟随系统 / 浅色 / 深色，默认跟随系统。设置页色卡改成**双层预览**：
外圈是该方案的背景色、内圈是按键主色，一眼就能看出「深色背景 + 青瓷按键」的实际搭配。

**唯一不随主题变的是语义红**（禁止 / 不可恢复）：浅色 `#C62828`、深色提亮为 `#FF6B6B`，
安全含义不打折。

### 2. 首页底部入口 + 三个二级页 UI 不一致

`设置与白名单` 6 个字在窄屏三等分按钮里会折行，三个按钮高度还各不相同。
改为**整行可点的入口列表**（`EntryRow`：整行点击区 + 右侧箭头 + 单行省略号），不再折行。
诊断页原本也是三个等宽按钮并排（「一键生成报告」同样会折成两行），改为
主操作独占一行、复制 / 分享两等分。

更关键的是页面框架：原来有两套（`ScreenFrame` 与 `SubScreen`），标题字号、内边距、
返回按钮位置、能否滚动全不一样，首页内容变多后还会**直接溢出屏幕、底部入口被挤掉**。
现在统一为唯一的 `ScreenFrame`：固定头部（标题 + 可选返回）+ 常驻不可恢复提示 + 内容区，
默认内容区可滚动；`SubScreen` 退化为委托调用，四个二级页与首页观感完全一致。

> 滚动与否由 `scrollable` 参数显式控制：扫描结果页 / 深度确认页内部有 `LazyColumn` 且依赖
> `weight(1f)`，外层若再套 `verticalScroll` 就是「无限高约束 + weight」，运行期直接抛异常。
> 这两页传 `scrollable = false`，其余页面默认滚动。

### 3. 可点选查看实际路径 / 文件位置

扫描结果里每条路径都是一行、被省略号截断，用户没法确认「这到底是哪个目录」。
现在**整行可点**（不管能不能勾选），弹出「实际路径 / 文件位置」：

- 完整路径（等宽字体 + 可长按选取 + 一键复制）；
- **当场重新探测一次**：现在还在不在、是文件还是目录、大小、目录有几个直接子项；
  扫描是快照，点开时可能已被别的应用删掉或又写入了文件 —— 这也顺带解释了上一轮
  「扫描显示空目录、删除却报非空」的困惑；
- 删除模式（可删 / 仅报告 / 已禁止）与该类型的后果摘要；
- 「在文件管理器中打开」：只把路径换成系统 DocumentsUI 的标准 external storage URI
  交给系统文件管理器，**不申请新权限、不自带浏览器、不读文件内容**；打不开时提示改用复制路径。

深度确认页的条目也支持点击查看（删除前先确认到底是哪个目录）。

### 4. 超过 1 个月的大文件索引

新增 `LargeFileIndex` / `LargeFileScanner`：**体积 >= 50 MB 且最后修改 >= 30 天**的文件单独成组，
显示大小、最后修改时间与「已 N 天未改动」，点开即完整路径。

- 判定**不靠路径规则**，任何目录里的大文件都会被看见（微信目录、截图目录、备份目录……）；
- **只索引、永不删除**：产物恒为 `REPORT_ONLY`（受保护路径为 `FORBIDDEN`），不可勾选、不进删除队列；
- 相册 / 视频 / OBB 等受保护目录**照样进索引**（用户需要知道它们占了多少空间），但标记为 L3；
- 拿不到 `mtime` 的文件一律排除：无法判断是否超过 1 个月，宁可不显示也不误导；
- **同一个文件只出现一次**：Download 目录与 `.apk` 已由 `DownloadScanner` 单独报告，
  索引里排除它们，否则会被算两遍、用户以为要清两次；
- 复用已有的文件快照，**不额外遍历一次文件系统**（真机上多一次全盘遍历是明显的耗时与发热源）。

### 5. 本轮修掉的编译期 / 隐性缺陷

- `ThemePalette.tone(dark: Boolean)` 的参数名与枚举属性 `dark` 同名，
  `if (dark) dark else light` 三处都指向 Boolean 参数，返回类型被推断为 `Any` → 编译失败。
  改名为 `isDark`。
- `ScanResultScreen` / `DeepConfirmScreen` 在统一框架后必须显式 `scrollable = false`，
  否则「`LazyColumn` + `weight(1f)`」与「`verticalScroll`」冲突（见上）。

### 验证

```text
testDebugUnitTest: classes=24 tests=175 failures=0 errors=0 skipped=0
assembleDebug assembleRelease: BUILD SUCCESSFUL
apksigner: Verifies / v2=true / 指纹 aaa47474…31557e（未变）
aapt2: versionCode=7 versionName=1.0.5 / 无 INTERNET 权限
AndroidManifest.xml: 2658 B / 16:43（零改动）
dex: report.large_old / junkmaster-cleaner / bindUserService 均在

交付：安卓垃圾清扫大师-v1.0.5-vc7-release.apk，1,642,369 B，
SHA-256 24e983fd02cb1a95f5a279c025a4ccdd3734972f96791be0b795b52d531336a1，同证书覆盖安装
```

新增单测：`LargeFileIndexTest`（9 例：天数向下取整与时钟回拨、50 MB 与 30 天双条件、
Download/APK 去重、恒 `REPORT_ONLY` 且不可勾选、受保护目录仍进索引但 L3、
规则与后果 id 一致、Scanner 过滤目录/小文件/无 mtime、取消即停）、
`PathProbeTest`（3 例：`emptyDirectory` 必须「可列举且子项为 0」，
列不出来（`childCount = -1`）绝不能算空目录 —— 上一轮 178 项误判的直接回归）。

---

## v1.0.4 (6) — 轮次 5：修复 Shizuku 绑定失败/无反馈 + 4 条 UI 优化（2026-11-05，待用户覆盖安装复测）

老板轮次 4 回传 `report_format=3` 诊断报告（`version=1.0.3 (5)`）+ Shizuku 设置页截图，
确认「不再有红色告警」，并提出 5 条改进。**第 5 条是真缺陷，其余 4 条是体验优化。**

### 缺陷：Shizuku 已授权却恒显示「服务未运行」，按钮零反馈

报告关键证据：

```text
status=服务未运行
shizuku_ping_binder=true     ← 服务在跑
shizuku_permission=granted   ← 本 App 已授权
uid=-1 / sdk_from_userservice=0 / raw_probe=   ← UserService 从未连上
```

前两项都成立 ⇒ 卡在 `bindUserService` 之后。两个原因：

1. **`UserServiceArgs` 缺参数**：Shizuku 官方要求同时设 `daemon(false)` /
   `processNameSuffix(...)` / `debuggable(...)` / `version(versionCode)`。
   缺 `processNameSuffix` 时 UserService 进程名与宿主 App 相同，Shizuku 侧启动会失败且**不回调**；
   缺 `version` 时 App 升级后会复用旧版本 UserService 进程（加载旧 APK 的 dex），
   表现为「装了新包却永远连不上」。
2. **`bindInFlight` 无超时**：只在 `onServiceConnected` / `onServiceDisconnected` / `catch` 里复位，
   而这次回调没来 ⇒ 标志永久 `true` ⇒ 之后所有 `bind()` 与刷新被静默短路，
   即老板看到的「刷新也无反应」「点绑定没反应」。

### 修复（5 处）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `shizuku/ShizukuGateway.kt` | `UserServiceArgs` 补齐四个参数（后缀固定 `cleaner`，version 取 `BuildConfig.VERSION_CODE`，debuggable 取 `BuildConfig.DEBUG`） |
| 2 | `shizuku/ShizukuGateway.kt` | `bindInFlight` 加 **8s 超时自愈**；`tryAutoBind` 同步按超时判定；`dropBridge` 不再清在途标志，避免重复 bind 堆积 connection |
| 3 | `shizuku/BindTrace.kt`（新增） | 环形记录 ping / 授权 / `bindUserService` 调用与异常 / `onServiceConnected` 耗时 / probe 结果；`BindResult` 携带可展示原因 |
| 4 | `shizuku/ShizukuGateway.kt` | 新增 `forceRebind()`（先真正解绑再重连）与 `diagnose()`（一键输出全链路实况） |
| 5 | `scan/DeepGate.kt`（新增）+ UI | 「进入深度确认」返回 8 种可区分原因，非 ok **必弹窗**并给「去 Shizuku 设置」；首页 / 设置页常显「上次绑定：<原因>」 |

### 优化（按老板 4 条建议）

| 反馈 | 处理 |
|---|---|
| 想有几种配色，可手动选 | 新增 `ui/theme/ThemePalette.kt`：**海天蓝 / 青瓷 / 胭脂 / 黛山 / 鎏金 / 樱粉** 6 套低饱和配色；DataStore 持久化，`MainActivity` 响应式应用；设置页色卡横向滚动，点击即时生效 |
| 「一键清理安全项」不贴切 | 改名 **「扫描清理手机」**，并补副文案「先只读扫描，你再逐项确认后才会删除」 |
| 后果说明逐项重复、太占地方 | 新增 `scan/ItemGrouping.kt` + `ui/components/ItemGroupCard.kt`：按 `ruleId` 聚合，**同类只渲染一份后果**（折叠时一行摘要，展开后完整说明），组内条目折叠为紧凑单行 |
| 结果太多，按是否需特殊权限分开 | `ScanResultScreen` 改为 **3 个 Tab**（免权限 / 需 Shizuku / 仅报告），每 Tab 内再分组；组头支持三态整组勾选 |

### 其它

- 诊断 `report_format=4`：新增 `last_bind_started` / `last_bind_reason` / `user_service_args` / `bind_trace`。
- 首页 Shizuku 卡片文案改为中性表述，不再武断地说「只安装未启动」。
- 单测 142 → **163 例**（+21：`BindTraceTest` 6、`DeepGateTest` 7、`ItemGroupingTest` 8）。
- 安全边界未放松：L3 组仍不可勾选、仅报告组仍不可删、`PathGuard` 双重校验 / 白名单 / 掉线 fail-closed 全部不变；
  配色只影响 `ColorScheme`；`AndroidManifest.xml` 零 diff、无 `INTERNET`、0 处裸 `pm clear `。
- 交付：`安卓垃圾清扫大师-v1.0.4-vc6-release.apk`，1,598,045 B，
  SHA-256 `d549dd4cd0116137511ffa8289e811efb6b498917e9489533df34e5185b8cc44`，
  同一把冻结证书，直接覆盖安装。

---

## v1.0.3 (5) — 轮次 4：修复「清了 67 项却 0 B + 误报已中止」+ 基础扫描补强（2026-11-05，待用户覆盖安装复测）

老板轮次 3 回传 `report_format=2` 诊断报告（`version=1.0.2 (4)`）+ 结果页截图：
`requested=178 / deleted=67 / freed_bytes=0 / failed=8 / stopped_early=true`、
`stop_reason=连续 5 项删除失败…已中止（fail-closed）`、`rules=l1.empty_dir=76`、
`failed_reasons=目录已非空，已跳过（防误删） x8`，反馈「依然出现问题，无法正常操作常规清理」。
报告同时证明前两轮修复已生效：`deleted>0`、`storage_root_listable=true`、
`write_delete_test=ok`、`pathguard_sample_file=allowed`、`usable_bytes=80 GB`。**本地已闭环，真机 R3 组待测。**

### 根因（第三个独立问题）

- **语义回归**：「目录已非空，已跳过（防误删）」是防误删策略主动放弃，却被 `CleanExecutor`
  计入 `failedCount` 并累加 `consecutiveFailures`；连续 5 个竞态即触发 fail-closed，
  178 项队列只跑 75 项就停，还误报「系统性异常」。
- **基础模式没有有体积的可删项**：178 项全是空目录（`l1.empty_dir`），释放 0 B 是如实结果。
  真正有体积的应用缓存在 `/sdcard/Android/data`，Android 11+ 起普通 App 无法读写，必须靠 Shizuku
  （报告 `shizuku_installed=true` / `shizuku_ping_binder=false`，服务连续三轮未启动）。
- **结果页文案误导**：「已删文件合计 67 / 0 B」+ 红字「已中止（系统性异常）」看起来就是失败。
- **重复枚举**：`sources()` 对 `/sdcard` 和 `/storage/emulated/0` 各遍历一次，同一目录两个别名，
  `distinctBy { path }` 因前缀不同去不掉。

### 修复（7 处）

- `clean/FileDeleter.kt`：`DeleteOutcome` 增加 `safeSkip`，把「目标符号链接 / 目录不可列举 /
  目录已非空」标为安全跳过；`AccessDeniedException` 等真实 IO 异常仍是硬失败。
- `clean/CleanExecutor.kt` / `FailurePolicy.kt`：结果分四类 —— 成功 / 安全跳过 / 硬失败 / 复核拒绝；
  新增 `skippedCount` / `skippedReasons` / `emptyDirDeletedCount` / `onlyEmptyDirs`，`combine()` 同步合并；
  **连续失败只对硬失败计数**；日志 reason 加「安全跳过：」前缀。
- `shizuku/BridgeFileDeleter.kt` / `CleanerUserService.kt`：`deletePaths()` 复核拒绝与符号链接
  改为跳过继续（不再一项被拒就中止整批）；跳过结果回传 `safeSkip=true`，双端语义一致。
- `scan/BasicCacheSource.kt`（新增）：扫描「明确缓存目录 + 明确可再生后缀」双命中的文件，
  排除 `/android/data|obb|media`、目录与 0 字节文件；L3 仍由 `RiskClassifier` 判禁。
  **用户文档 / 媒体 / 数据库一律不默认勾删**（零误删红线）。
- `scan/TempFileFilter.kt` / `rule/BuiltInRules.kt`：补 `.crdownload` / `.download` / `.tmpl` / `.dctmp`。
- `AppServices.sources()`：改为 `storageRoot()` 单根遍历，消除重复枚举；加入 `BasicCacheSource`。
- UI：`ui/result/CleanResultScreen.kt` 重写，区分「已清理项 / 释放空间 / 其中空目录 / 安全跳过 /
  真实失败 / 安全拦截」，释放 0 B 时解释「空目录不占空间」并给「去启动 Shizuku 解锁深度清理」入口；
  `ui/home/HomeScreen.kt` 在服务未启动时提供「打开 Shizuku 启动服务」按钮与说明。

### 诊断报告升级 `report_format=3`（零新权限、Manifest 零改动）

- 新增 `diag/LastScanSummary.kt`：扫描完成后把 `total / l1 / l2 / l3 / report_only /
  l1_checked / l1_bytes / l1_requires_shizuku / categories` 写成**只有计数与 ruleId、无路径**的
  key=value 到私有 `files/diag/last_scan.txt`；`ScanProgressViewModel` 落盘。
- `clean/LastCleanSummary.kt`：新增 `skipped` / `skipped_reasons` / `empty_dirs_deleted` /
  `only_empty_dirs`。
- `diag/ReportSanitizer.kt`：`JournalSummary` 新增 `skippedCount` / `skippedReasonCounts`，
  把「安全跳过：」与真实失败分开统计。
- `diag/DiagnosticsReportBuilder.kt`：`report_format=3`，新增「最近一次扫描分布」段与
  `skipped` / `skip_reasons` 字段，尾部提示解释两者区别。
- 分享仍为纯文本 `ACTION_SEND`，不产生 URI、不需要 FileProvider、不引入 `INTERNET`。

### 版本与验证

- `versionCode=5` / `versionName="1.0.3"`，沿用**同一把冻结证书**与同一 `applicationId`，可直接覆盖 vc4 安装。
- 单测 126 → **142 例**（+16）：新增 `BasicCacheSourceTest`(7)、`LastScanSummaryTest`(3)，
  扩展 `CleanExecutorTest`(4)、`NioFileDeleterTest`、`ReportSanitizerTest`、`LastCleanSummaryTest`；
  19 个测试类 × 2 变体 = 284 次执行，0 failures / 0 errors / 0 skipped。
- `test` 34s、`assembleDebug assembleRelease` 1m23s 全绿（分步 + 低堆内存，避开本机 JVM native OOM）；
  `apksigner` v2=true、指纹 `aaa47474…31557e`；aapt2 无 `INTERNET`；`AndroidManifest.xml` 零改动；源码 0 处裸 `pm clear `。
- 交付：`delivery\安卓垃圾清扫大师-v1.0.3-vc5-release.apk`（1,549,233 B），
  SHA-256 `06421587d28751675e178527635b8a9dbf3c058ee5029c74dc617bb0da33f5a4`；vc2/vc3/vc4 移入 `delivery\archive\`。
- 文档：`docs/SELF_TEST.md` 新增 R3 组 8 条、`docs/TEST_REPORT.md` 新增轮次 3 闭环与轮次表第 4 行、
  `docs/ENV.md` / `README.md` / `PRIVACY.md` 同步。发布 gate 仍 **PENDING**。

> **说明**：基础清扫清不出可观容量是 Android scoped storage 的权限边界，不是缺陷。
> 普通 App 即使有 `MANAGE_EXTERNAL_STORAGE` 也不能读写其他应用的 `/sdcard/Android/data` 缓存；
> 要深度清理必须启动 Shizuku 服务。本轮已在 App 内如实说明并提供启动入口。

---

## v1.0.2 (4) — 轮次 3：修复「vc3 仍 0 删除」+ 诊断可自证（2026-11-05，待用户覆盖安装复测）

老板轮次 2 回传 `report_format=1` 诊断报告（`version=1.0.1 (3)`）：`entries=1 / success=0 / failed=1 /
rules=l1.empty_dir=1`、`usable_bytes=0`、`status=未安装 Shizuku` /
`shizuku_permission=unknown (IllegalStateException)`，反馈「仍然是这个错误」。
`latest_ts=1793874456935`（18:27:36）证明 vc3 上确实又跑了一次清理并失败。**本地已闭环，真机 R2 组待测。**

### 根因（第二个独立 bug，不是上一轮没修好）

- `FileSystemWalker` 把 `listFiles()` 返回 `null` 的目录仍加入快照；`EmptyDirFinder` 因「快照无子项」
  把 `/sdcard/Android/data` 这类**不可列举的骨架目录**误判为空目录并默认勾选 → 删除必然失败。
- `CleanExecutor` 单项删除失败即触发「失败即停」，队列第一项失败 → 整批 0 删除。
- 额外安全隐患：目录删除用 `walkFileTree` 递归清空，扫描后若有新文件写入会被一并误删。
- `usable_bytes=0` 是容量探针取根分区 `/`（Android 只读 ramdisk），非存储已满。
- `status=未安装 Shizuku` 是标签误导；`IllegalStateException` 实际表示**服务未启动**（只装了 App）。

### 修复

- `scan/FileSystemWalker.kt`：`FileEntry` 新增 `readable`，目录只列举一次并如实记录可列举性；
  `scan/EmptyDirFinder.kt` 排除不可读目录与共享存储结构目录。
- `clean/StorageRoots.kt` 新增 `DEFAULT_STORAGE_PATH` / `relativeToStorageRoot` /
  `isUnderSystemStorageRoot` / `isStructuralStorageDir`；`clean/PathGuard.kt` 新增
  `targetIsDirectory` 与结构目录硬拦截（原始 / canonical / 解析后三种形态都查），
  厂商自定义挂载别名改为按「解析目标是否仍在系统挂载点内且非 L3」判定，不再靠白名单猜名字。
- `clean/FileDeleter.kt` / `shizuku/CleanerUserService.kt`：**目录不再递归删除**，只在
  「可列举且确实为空」时删除；非空 / 不可列举 / 缺失一律如实返回并跳过，防误删。
- `clean/FailurePolicy.kt` / `clean/CleanExecutor.kt`：单项失败**跳过继续**并聚合
  `failedReasons`，结果页展示「失败项 + 失败原因 + 其余项继续清理」；只有连续 5 次失败
  或 Shizuku 掉线才 fail-closed 中止。容量探测改指向共享存储根。
- `ui/home/HomeViewModel.kt`：首页容量同样改取共享存储根。
- `shizuku/ShizukuStatus.kt` 标签改为「未检测到 Shizuku 服务（未安装或未启动）」；
  `strings.xml` 增加 `shizuku_step3` 并在 `ShizukuSetupScreen` 展示。

### 诊断报告升级 `report_format=2`（零新权限、Manifest 零改动）

- 新增 `clean/LastCleanSummary.kt`：每次清理后把 `requested / deleted / failed / guard_rejected /
  stop_reason / guard_reasons / failed_reasons` 写成**无路径**的 key=value 到私有
  `files/diag/last_clean.txt`；`AppServices.writeLastClean()` 在 L1 一键清理与 L2 深度确认两处落盘。
- 新增 `diag/StorageProbe.kt`：真机实测共享存储根能否列举 / 能否写删、挂载链符号链接数量、
  PathGuard 对样例 L1 文件 / 空目录 / 结构目录的判定；只输出类别与布尔值，**不输出任何路径**；
  生成报告时创建并立即删除 1 字节临时文件，不读取任何用户文件内容。
- `diag/ReportSanitizer.kt`：`JournalSummary` 新增 `failedReasonCounts` / `lastReason` /
  `lastRule` / `lastOk`；只抽取 reason，绝不读取 `path`。
- `diag/DiagnosticsReportBuilder.kt`：`report_format=2`；容量改取共享存储并标注
  `capacity_probe_partition`；新增 `shizuku_installed` / `shizuku_ping_binder` 区分「装了」与「跑着」；
  新增「存储与路径探针」「最近一次清理结果」两段与 `fail_reasons` / `last_reason` 等字段。
- `strings.xml` 的 `diag_privacy_hint` 明示新增内容与临时文件行为；分享仍为纯文本 `ACTION_SEND`。

### 版本与验证

- `versionCode=4` / `versionName="1.0.2"`，沿用**同一把冻结证书**与同一 `applicationId`，可直接覆盖 vc3 安装。
- 单测 101 → **126 例**（+25）：新增 `EmptyDirFinderTest`(5)、`NioFileDeleterTest`(5)、
  `FileSystemWalkerTest`(2)、`LastCleanSummaryTest`(4)；`CleanExecutorTest` 重写为单项失败跳过 /
  连续失败中止 / 结构目录复核拒绝；`PathGuardTest` +5；`ReportSanitizerTest` +2。
  两套变体共 252 次执行，0 failures / 0 errors / 0 skipped。
- `test`（39s）、`assembleDebug assembleRelease`（1m32s）分步 BUILD SUCCESSFUL；R8 + lintVital 通过。
- `apksigner verify --print-certs`：`Verifies`、v2=true、DN `CN=JunkMaster Release`、
  指纹 `aaa47474…31557e` 与冻结值一致；aapt2 无 `INTERNET`；`AndroidManifest.xml` 零 diff（16:43 / 2658 B）；
  全仓 0 处裸 `pm clear `；aapt2 资源表检出本轮新增 4 个字符串资源。
- 交付：`安卓垃圾清扫大师-v1.0.2-vc4-release.apk`（1,544,449 B），
  SHA-256 `9604b20f650e375a4abc0ed98ba501f945784b2a60873fa9fd2d81223a3010ed`；
  vc2、vc3 均已移入 `delivery\archive\`。
- `docs/SELF_TEST.md` 重写为轮次 3，新增 **R2 组 7 条必测回归**与 `report_format=2` 字段读法；
  `docs/TEST_REPORT.md` 新增轮次 2 缺陷记录与轮次 3 交付行。
- **发布 gate 不变**：总体仍 **PENDING**，M8 严格等用户自测全组通过、问题闭环、明确确认无问题后才执行，当前远程零写入。

---

## v1.0.1 (3) — 轮次 2：修复「一键清理 0 删除」阻断（2026-11-05，待用户覆盖安装复测）

老板轮次 1 真机反馈：点「一键清理安全项」后结果页 `已删文件合计 0 / 0 B`、`失败项 1`、
`已中止（失败即停 / 掉线保护）：删除前复核拒绝：父路径含符号链接`。**本地已闭环，真机 R1~R4 待测。**

### 根因

- Android 上 `/sdcard` **本身就是系统符号链接**（→ `/storage/self/primary` → `/storage/emulated/0`）。
  旧 `PathGuard` 对父路径链里任何符号链接一律拒绝，导致 `/sdcard` 下**每一个**扫描项在删除前复核阶段
  全被拦下；`CleanerUserService.listCandidates()` 复用同一 `PathGuard`，深度列表同样被全量过滤。
- `CleanExecutor` 又把「复核拒绝」当成致命失败触发「失败即停」，第一批第一项就中止 → 整批 0 删除。
- **测试盲区**：桌面 JVM 上 `/sdcard` 不存在，`Files.isSymbolicLink` 恒 false，轮次 1 的 87 个纯 JVM
  用例全绿也没能覆盖设备真实文件系统语义。

### 修复

- 新增 `clean/StorageRoots.kt`：系统存储挂载链白名单（`/sdcard`、`/storage/self/primary`、
  `/storage/emulated/<n>`、`/mnt/user/<n>/primary` 等）。只放行系统自己搭的别名，用户数据区路径不在集合内。
- `clean/PathGuard.kt`：抽出可注入 `SymlinkProbe` / `NioSymlinkProbe`（纯 JVM 单测可模拟 Android 挂载链）；
  父链符号链接改为「只有系统挂载链才放行」；新增真实路径逃逸检查（解析后命中 L3 即拒）；
  目标本身是符号链接仍拒绝；`..` / L3 / 白名单判定不变。
- `clean/CleanExecutor.kt`：**复核拒绝 = 安全拦截，跳过该项并继续清理**，不再中止整批。
  `CleanReport` 新增 `guardRejectedCount` / `guardRejectReasons`，`combine()` 合并并去重原因；
  拦截也写私有日志（诊断页仍只输出聚合计数）。**删除执行失败与 Shizuku 掉线仍保持失败即停 / fail-closed**。
- `ui/result/CleanResultScreen.kt` + `strings.xml`：结果页新增「安全拦截 N 项 + 拦截原因 + 不影响其余项」。
- `clean/FileDeleter.kt`、`shizuku/CleanerUserService.kt`：目录内遇到符号链接不再抛异常中断整个目录；
  `walkFileTree` 默认不跟随，只删链接本身、不触及链接目标。

**安全边界未放松**：目标本身 symlink 拒绝、用户数据区 symlink 拒绝、链接解析后指向 L3 拒绝、
`..` 逃逸 / L3 硬编码禁止 / 用户白名单（deny 优先）/ 掉线 fail-closed / 删除失败即停，全部保持。

### 版本与验证

- `versionCode=3` / `versionName="1.0.1"`，沿用**同一把冻结证书**与同一 `applicationId`，可直接覆盖 vc2 安装。
- 单测 87 → **101 例**（+14）：`PathGuardTest` +10（含 `/sdcard` 放行、用户数据区 symlink 拒绝、
  逃逸到 `/data/data` 拒绝、挂载链被篡改拒绝、L3 / `..` / 白名单不变、StorageRoots 边界）、
  `CleanExecutorTest` +3、`DualModeTest` +1。两套变体共 202 次执行，0 failures / 0 errors / 0 skipped。
- `test`（45s）、`assembleDebug assembleRelease`（1m38s）分步 BUILD SUCCESSFUL；R8 + lintVital 通过。
  首次合并跑时 Gradle daemon 因系统瞬时内存不足崩溃（`arena.cpp:191`），与代码无关，改为
  `--no-daemon --no-parallel --max-workers=2 -Xmx1280m` 分步执行后全绿；`gradle.properties` 未改。
- `apksigner verify --print-certs`：`Verifies`、v2=true、DN `CN=JunkMaster Release`、
  指纹 `aaa47474…31557e` 与冻结值一致；aapt2 无 `INTERNET`；`AndroidManifest.xml` 零 diff；全仓 0 处裸 `pm clear `。
- 交付：`安卓垃圾清扫大师-v1.0.1-vc3-release.apk`（1,542,845 B），
  SHA-256 `8ca542f6640674ec785023310bd446e33cd468b96cc363bdedb74866721c6514`；vc2 已移入 `delivery\archive\`。
- `docs/SELF_TEST.md` 新增第 0 节「本轮修复了什么」与 **R 组必测回归**；反馈模板版本同步为 `1.0.1 (3)`。
- **发布 gate 不变**：总体仍 **PENDING**，M8 严格等用户自测全组通过、问题闭环、明确确认无问题后才执行，当前远程零写入。

---

## v1.0.0 (2) — 用户自测交付版（2026-11-05，待用户自测确认 / M8 发行）

测试方式改为「**我出包 → 用户自行侧载安装自测 → 诊断报告反馈 → 修复并递增版本重新出包**」，不再要求 USB adb 连接电脑。本轮变更：

### 签名与身份冻结

- 生成专用正式 keystore：`D:\AndroidDev\keystore\junkmaster-release.jks`（PKCS12 / RSA 3072 / SHA256withRSA / 30 年 / alias `junkmaster`），口令见同目录 `PASSWORDS.txt`（PKCS12 下 store/key 口令相同），两者均不入库。
- `app/build.gradle.kts`：`versionCode=2` / `versionName="1.0.0"`；`release` **只用 `releaseSigned`**，去掉 debug 签名兜底，并加硬闸——缺 `keystore.properties` 时 `assembleRelease` 直接失败；`buildConfig=true` 供关于页 / 崩溃记录读版本号；`debug` 变体保持默认签名、不交付。
- `.github/workflows/release.yml`：`apksigner --print-certs` 断言证书指纹必须等于冻结值，防止 CI 用错 key 导致覆盖升级链断裂；注明 4 个 Secrets 必须来自同一把 key。
- 每轮修复包 `versionCode += 1`、`versionName` 补丁位递增，永不复用；同证书 + 更大版本号 = 用户直接覆盖安装，私有日志与崩溃记录跨轮保留。

### 测试诊断页（新增，零新权限、Manifest 零改动）

- 入口：首页底部 + 关于页 →「测试诊断」。一键汇总**脱敏报告**：版本与签名指纹、设备与系统、权限状态、Shizuku 状态与 `probe` 原始输出（uid / sdk）、容量、清理日志**聚合计数**、最近崩溃栈、用户附注。
- 三通道：屏显（可选中复制）/「复制报告」/「分享报告」（纯文本 `ACTION_SEND`）。只用 `EXTRA_TEXT`，不需要 FileProvider、不产生 `content://` URI、不受 scoped storage 影响、不引入 `INTERNET`。报告只在内存 StateFlow，不落公共目录。
- **脱敏**：`ReportSanitizer` 隐藏第三方包名、用户文件名、共享存储前缀；清理日志绝不逐条输出 `path`；`rawProbe` / 失败原因 / 崩溃栈 / 用户附注过同一脱敏器并截断。屏显与外发用同一份脱敏版，不提供「含真实路径」开关。新增 `ReportSanitizerTest`（7 例）。
- `AppServices.init()` 安装崩溃捕获，落盘到私有 `files/crash/latest.txt` 并 delegate 原 handler；`proguard-rules.pro` 已有 `-keepattributes SourceFile,LineNumberTable`，R8 后栈可读。
- `PRIVACY.md` / `about_privacy_body` 补「唯一例外：用户主动分享已脱敏报告」；本应用仍不联网、不自动上传。

### 文档与流程

- 新增 `docs/SELF_TEST.md`：A 基础 / B 深度 / C release 专项 / D 诊断页四组用例、安装前准备（卸载旧 debug 签名包、允许未知来源、SHA-256 校验）、Shizuku 授权指引（前提：服务已在运行）、诊断报告发回方式与反馈模板。
- 重写 `docs/TEST_REPORT.md` 为「**用户自测 + 反馈闭环**」gate，新增交付轮次表；`adb devices` / `getprop` 不再是 gate 前提，`scripts/device_baseline.sh` 降为可选诊断。
- `docs/ENV.md` 记录冻结证书指纹与口令保管位置；`README.md` 去掉「M8 必须改包名」「debug 签名兜底」等过时措辞。
- **发布 gate 不变**：M8 严格等 `TEST_REPORT.md` 判定 PASS 且老板明确确认无问题后才执行，当前远程零写入。

### 本轮本地验证

`test assembleDebug assembleRelease` = BUILD SUCCESSFUL（2m52s）；13 测试类 / 单变体 **87 例**（两套变体共 174 次执行）0 failures / 0 errors / 0 skipped；`apksigner verify --print-certs` = `Verifies`、v2=true、DN `CN=JunkMaster Release`、指纹与冻结值一致；aapt2 无 `INTERNET`；`versionCode=2`；全仓 0 处裸 `pm clear `；`AndroidManifest.xml` 零 diff。

---

## v1.0.0 — M1~M6 首次完整实现

### 三级安全模型（分级说明）

- **L1 安全（一键直清、默认全选、无二次弹窗）**：应用外部 `cache` / `code_cache`、`.tmp` 临时文件、缩略图、空目录、崩溃 / ANR 日志。后果：首次启动变慢、资源重新下载；不影响登录态 / 设置 / 聊天记录；不可恢复但会自动重建。
- **L2 需确认（逐项勾选、默认全不勾、独立确认页 + 强制勾选「我已知晓删除不可恢复」）**：卸载残留、聊天应用附件缓存、系统日志。后果：残留的存档 / 导出文件可能永久丢失；聊天语音与附件可能永久丢失、文件已过期，文字记录不受影响。
- **仅报告 REPORT_ONLY（恒不删除）**：Download 大文件 / 旧 APK。后果：需重新下载，本 App 不自动删。
- **L3 硬编码禁止（App 侧 + UserService 侧双重校验）**：`/data/data`、`/system`、`DCIM/Pictures/Camera`、`Android/obb`、`databases/`、`shared_prefs/`、凭据路径。展示「已禁止」与拦截原因。

每条扫描结果在 UI 常显「是否安全」徽标 + 「删除后会有什么后果」中文说明；首页 / 确认页 / 关于页常驻「删除不可恢复、无回收站」。

### 防误删机制

- 扫描阶段 **纯 dry-run 只读，绝不删除**。
- 路径 canonical 归一化、拒符号链接与 `..` 逃逸；内置规则 + 用户白名单（deny > allow）。
- 卸载残留需「包未安装 + mtime > 30 天 + 已知残留名 + 删除前重查」。
- `REPORT_ONLY` 恒不入删除队列；删除前**逐项重新 PathGuard 校验**。
- **50 项一批、单项硬失败跳过继续、防误删的安全跳过单列统计；连续 5 次硬失败或 Shizuku 掉线才 fail-closed 中止**（v1.0.3 起；早期为「失败即停」）。
- 结构化删除日志写 `files/clean_logs` 目录下的 jsonl 文件（不记录文件内容、不上传）。
- 结果页区分「已清理项 / 释放空间 / 其中空目录 / 安全跳过 / 真实失败 / 安全拦截」，释放 0 B 时解释「空目录不占空间，不是清理失败」；同时给出「可用空间变化」并说明两者可能不等（块分配 / 其他应用写入）。

### Shizuku 深度层

- `bindUserService` + **手写 Binder 接口**（`ICleanerUserService.kt` / `CleanerServiceStub`，**禁用已弃用的 `newProcess`**），`UserServiceArgs` 显式 `tag`，binder 生命周期监听；`dropBridge()` **不调用 `destroy()`**（避免进程复用实例被永久标记已销毁）。手写 Binder 是为规避 Windows 中文路径下 AGP AIDL 编译的 `MalformedInputException`。
- UserService 提供 `probe()` / `listCandidates()`（dry-run）/ `deletePaths()`（**NIO 进程内直删，不拼 shell 字符串**）/ `trimCaches()` / `clearCacheViaBinder()` / `queryCacheBytes()` / `destroy()`。
- 状态机 `NOT_INSTALLED → NOT_RUNNING → NEED_PERMISSION → READY`（异常 `LOST / DENIED / PORTER`），**READY 只在 UserService bridge 已连接且存活时成立**。
- **双模式主开关**：不开 Shizuku 只跑基础源；READY 才加入 `ShizukuCacheItemSource`；深度项在掉线时降级为「仅报告」；L1 清理按 `requiresShizuku` 分流到 App 进程 NIO 或 UserService。
- **依赖**：Shizuku-API 13.1.5（MIT，© RikkaW）；Shizuku Manager v13.6.0 仅运行时依赖、本仓库不分发。
- **Flyme 提示**：请在开发者选项中关闭「**Flyme 支付保护**」，否则授权 / 绑定可能失败。
- **ADB 重启提示**：通过 ADB / 无线调试启动的 Shizuku 服务，每次手机重启后都需要重新启动服务；服务恢复后回首页刷新即自动重连。

### 真机前静态复核修复（5 项，均待用户自测确认）

1. **冷启动自动绑定**：新增纯 JVM `ShizukuBindPolicy`，`refreshStatus()` 在「Shizuku 已运行 + 已授权 + bridge 未连接」时自动 `bindUserService`；用户在 Shizuku Manager 里提前授权过、或 App 冷启动，都无需再手动点「绑定」。
2. **消除假 READY**：`onPermissionGranted()` 不再直接置 `READY`（`bindUserService` 是异步的）；READY 唯一判据改为三者同时成立。
3. **掉线可恢复**：`LOST` 不再被 `refreshStatus()` 永久锁死，刷新时重新探测并自动重连；恢复期间保持不可放行态，**清理过程中的掉线立即中止语义不变**。
4. **bind 幂等**：增加 `bindInFlight` 与存活连接短路；绑定失败保持 `NOT_RUNNING`，不误置 `LOST`。
5. **不在状态刷新时 destroy UserService**：`dropBridge()` 只清本地引用，避免复用实例被永久标记已销毁。

### trim-caches 实测与回退结论

- `cmd package trim-caches` / `pm trim-caches` 是否被 shell 拒绝存在系统差异（AOSP 已加 `@EnforcePermission("CLEAR_APP_CACHE")`），**不押宝**。
- 回退链：trim-caches 成功 → 按实际释放量记账（不谎报）→ 被拒 → NIO 直删 `cache` / `code_cache` → 再失败仅报告原因。
- 隐藏 API `deleteApplicationCacheFiles` 为 signature 级，仅在 UserService 进程内尽力尝试，失败如实报告。
- **逐条实测结论以 `docs/TEST_REPORT.md` 的用户自测反馈为准**：失败但已回退且如实报告，不视为阻断。

### 合规与构建

- 不声明 `INTERNET` / `CLEAR_APP_CACHE`，不使用 AccessibilityService，不集成统计 / 广告 / 崩溃上报 SDK。
- **代码级禁用** 裸 `pm clear`（带包名参数会删除应用全部数据）。
- 版本链：JDK 17 / Gradle 8.13 / AGP 8.11.1 / Kotlin 2.1.20 + compose 插件 / Compose BOM 2025.08.01 / compileSdk 36 / minSdk 26 / targetSdk 36。
- `proguard-rules.pro` keep 手写 Binder 接口 / `CleanerServiceStub` / `Proxy` / Parcelable / `CleanerUserService`，保障 release 包 R8 后 `bindUserService` 可用。

### 已知限制

- 普通 App 无法直接删除他应用 `/sdcard/Android/data`、`Android/obb`（Android 11+ 官方限制），**深度清理必须 Shizuku 且需 READY**。
- 用量访问权未开启时，他应用缓存大小可能读不到，按 0 显示。
- `trim-caches` 可能被系统拒绝；隐藏 API 清缓存可能不可用；均以回退 + 如实报告处理。
- Shizuku 服务重启后需重新启动服务（回首页刷新自动重连）；Flyme 需关闭支付保护。
- 侧载需允许「安装未知来源应用」；Google 自 2026 起逐步推开开发者验证，**不承诺长期可安装性**。
- 商店上架基本不可行，定位个人自用 + GitHub Release 侧载。
- 本应用**不提供回收站、不提供撤销**：删除永久不可恢复。

### 发布 gate

`docs/TEST_REPORT.md` 判定 **PASS 且老板确认无问题前**，不做任何远程写操作。当前状态：**PENDING（用户自测 + 反馈闭环进行中）**。
