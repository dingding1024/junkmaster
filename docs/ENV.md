# 环境与签名基线

> 2026-11-05 更新：测试方式改为**用户自行侧载安装 + 自测反馈**，设备与系统信息由 App 内「测试诊断」页采集并随报告回传，
> **不再依赖 USB adb / `getprop` 作为发布 gate 前提**。`scripts/device_baseline.sh` 保留为 adb 环境下的可选诊断工具。

## 工具链（本机，全部落 D 盘）

| 项 | 路径 / 值 |
|---|---|
| JDK 17 | `D:\AndroidDev\jdk-17` |
| Android SDK | `D:\AndroidDev\Android\Sdk` |
| GRADLE_USER_HOME | `D:\AndroidDev\gradle-home` |
| Build-Tools | `36.0.0` |
| Platform | `platforms;android-36` |
| 代理 | `127.0.0.1:4057`（gradle.properties systemProp 已配置；CI 里由 workflow 去掉） |
| Gradle Wrapper | 8.13 |
| platform-tools / adb | `37.0.1`（`D:\AndroidDev\Android\Sdk\platform-tools\adb.exe`） |
| 下载源 | JDK 17 = 清华 Adoptium 镜像；Gradle 8.13 = 腾讯镜像；cmdline-tools = dl.google.com |
| 中文路径 | `D:\安卓清扫app` 含非 ASCII：AGP 拒绝、manifest merger 按系统编码读 XML 失败、junction 也被解析回中文路径 → `android.overridePathCheck=true`，本地构建统一走真实英文副本 `D:\AndroidDev\JunkMasterSrc`（增量同步，不删除） |
| 构建目录 | `D:\AndroidDev\JunkMasterSrc`，命令 `gradlew.bat test assembleDebug assembleRelease`；需显式 `JAVA_HOME` / `ANDROID_HOME` / `GRADLE_USER_HOME` |
| 构建注记 | 2026-11-05 轮次 2 合并跑三个 task 时 Gradle daemon 因系统瞬时内存不足 native OOM 崩溃（`arena.cpp:191`，与代码无关）。改用分步 + `--no-daemon --no-parallel --max-workers=2 -Xmx1280m` 后全绿；`gradle.properties` 仍是 `-Xmx2048m` + `parallel=true`，内存吃紧时沿用分步参数 |
| 交付目录 | `D:\安卓清扫app\delivery\`（当前轮次 release 包 + SHA-256 + `SELF_TEST.md`）；历史包在 `delivery\archive\` |

## 签名（已冻结，2026-11-05）

| 项 | 值 |
|---|---|
| `applicationId` / `namespace` | `io.github.junkmaster.app`（**永不更改**，M8 不再按 GitHub owner 改包名） |
| keystore | `D:\AndroidDev\keystore\junkmaster-release.jks` |
| 格式 / 算法 | PKCS12 / RSA 3072 / SHA256withRSA / 有效期 30 年 |
| 别名 | `junkmaster` |
| 证书 DN | `CN=JunkMaster Release, OU=Personal, O=JunkMaster, C=CN` |
| 证书 SHA-256（带冒号） | `AA:A4:74:74:E7:89:34:D2:BF:52:BF:F6:EC:D5:AA:BE:F5:55:66:B5:6B:79:90:A9:81:02:5D:6C:9B:31:55:7E` |
| 证书 SHA-256（规范化，CI 断言用） | `aaa47474e78934d2bf52bff6ecd5aabef55566b56b7990a981025d6c9b31557e` |
| 口令 | `D:\AndroidDev\keystore\PASSWORDS.txt`（PKCS12 下 store/key 口令相同）；`keystore.properties` 在项目根；**两者均不入库**，请自行离线备份 |
| CI | `.github/workflows/release.yml` 的 `KEYSTORE_BASE64` 必须是**同一文件**的 base64；workflow 用 `apksigner --print-certs` 断言指纹，不一致即失败 |
| 当前轮次 | 轮次 6：`versionCode=7` / `versionName=1.0.5`（APK SHA-256 `24e983fd02cb1a95f5a279c025a4ccdd3734972f96791be0b795b52d531336a1`，1,642,369 B）；轮次 1 `2 / 1.0.0`、轮次 2 `3 / 1.0.1`、轮次 3 `4 / 1.0.2`、轮次 4 `5 / 1.0.3`、轮次 5 `6 / 1.0.4` 均已归档。后续每轮 `versionCode += 1`、补丁位递增，永不复用 |

> 轮次 1~5 **同证书**，可直接覆盖升级（私有日志与配色偏好保留）。更早（2026-11-05 之前）的包使用
> `CN=Android Debug` 证书，**与本证书不同，无法覆盖升级**，若装过需先卸载。

## 设备与系统实测（用户自测回传，非 adb）

以下字段由「测试诊断」页一键采集，随脱敏报告回传；**不再要求 USB 连接或 `adb shell getprop`**：

```
report_format=4（2026-11-05 轮次 5 起）
机型 / 厂商 / 品牌 / Android 版本 / SDK / Build.DISPLAY（Flyme 版本） / fingerprint
所有文件访问权 / 用量访问权 / 通知权限
Shizuku 状态 / shizuku_installed / shizuku_ping_binder / uid / UserService 探测原始输出
绑定链路：last_bind_started / last_bind_reason / user_service_args / bind_trace（FAIL 行即卡点）
共享存储可用空间与总空间（不再取根分区 /）/ capacity_probe_partition
存储与路径探针：根目录可列举性 / 写删实测 / 挂载链符号链接数量 / PathGuard 样例判定
最近一次扫描分布（无路径）：total / l1 / l2 / l3 / report_only / l1_checked / l1_bytes / categories
最近一次清理结果（无路径摘要）：requested / deleted / empty_dirs_deleted / failed / skipped /
  guard_rejected / only_empty_dirs / stop_reason / 各类原因
清理日志：聚合计数 + skipped / skip_reasons / fail_reasons / last_reason / last_rule / last_ok
最近一次崩溃栈（脱敏）/ 用户附注
```

> 轮次 4 回传的报告实测：MEIZU 21 Pro / Android 16 / SDK 36 / Flyme 12.7.0.0A，
> `all_files_access=true` / `usage_stats=true` / `notifications=not_granted`；
> `usable_bytes=79996133376`（约 80 GB 可用）、`storage_root_listable=true`、`write_delete_test=ok`、
> `pathguard_sample_file=allowed` —— 路径 / 权限 / 容量层修复均已验证生效。
> **`shizuku_ping_binder=true` + `shizuku_permission=granted`，但 `status=服务未运行`、`uid=-1`、
> `raw_probe` 为空** ⇒ 授权与 binder 都正常，失败在 `bindUserService` 之后（UserService 未起来）。
> v1.0.4 已补齐 `UserServiceArgs` 四个参数并给 `bindInFlight` 加 8s 超时；v1.0.5 未改动 Shizuku 链路，
> 同时新增 `bind_trace`，下一份报告可直接看到卡点。
> 该轮 `requested=28 / deleted=7 / freed_bytes=0 / only_empty_dirs=true`：清理的是空目录，
> 不占空间属如实结果；基础清扫清不出可观容量属于 Android scoped storage 权限边界。

Shizuku Manager 版本：`v13.6.0`（仅运行时依赖，本仓库不分发 APK）；启动 / 权限 / 绑定指引见 `docs/SELF_TEST.md`。

## 结论

- 本地构建、单测、签名、权限校验全部 PASS（详见 `docs/TEST_REPORT.md`）。
- 真实 API 级别、Flyme 行为、SELinux 对 `/data/data` 的拒绝等，改由**用户自测 + 诊断报告**确认。
- 发布 gate 仍为 `docs/TEST_REPORT.md` 判定 PASS 且用户明确确认无问题；此前远程零写入。
