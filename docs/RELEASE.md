# 发布手册（GitHub Release）

> 仓库形态：**公开** · 签名**已冻结** · APK 由 **GitHub Actions 自动构建**
> 本文件只写「怎么发版」，不含任何口令、不含 token。看完这一份就能自己发版。

---

## 0. 一次性准备（只做一遍）

### 0.1 在 GitHub 建空仓库

1. 打开 <https://github.com/new>
2. **Repository name**：`junkmaster`（想用别的名字也行，后面命令里改掉即可）
3. 可见性选 **Public**
4. **README / .gitignore / license 三个勾选项全部不要勾** —— 本地已经全都有了，勾了会在 push 时冲突
5. 点 **Create repository**

### 0.2 推送（本地已提交好，只差一个 remote）

执行前把 `<你的用户名>` 换成你的 GitHub 用户名（仓库名如果没叫 `junkmaster` 也一起改）：

```bash
cd /d/安卓清扫app
git remote add origin https://github.com/<你的用户名>/junkmaster.git
git push -u origin main
```

**首次 push 的登录**：本机 Git 已配置 `credential.helper=manager`（Git Credential Manager），
正常会**弹出浏览器窗口**让你登录 GitHub，点 Authorize 就走完，**不需要手动造 token**。

如果没弹窗、或提示输入用户名 / 密码，就退回 PAT 方式：

1. <https://github.com/settings/tokens> → `Generate new token (classic)`
2. 勾 **`repo`** 与 **`workflow`**
3. 用户名填你的 GitHub 用户名，**密码位置粘贴 token**（GitHub 从 2021 年起不接受账号密码）
4. 有效期建议 7 天，推完即可撤销

> 想用自己的名字作为提交作者？push 前先执行这两条（可选，不改也能推）：
> ```bash
> git config user.name "你的名字"
> git config user.email "你的GitHub邮箱"
> git commit --amend --reset-author --no-edit   # 只改作者信息，不改内容
> ```

### 0.3 配置 4 个 Secrets（**没配就跑 CI 会直接失败**）

仓库页 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

| Name | Value | 从哪里取 |
|---|---|---|
| `KEYSTORE_BASE64` | 单行 base64 文本（4716 字符） | 本机 `D:\安卓清扫app\delivery\KEYSTORE_BASE64.txt` **全文**，直接全选复制 |
| `KEYSTORE_PASSWORD` | keystore 口令 | 本机 `D:\AndroidDev\keystore\PASSWORDS.txt` |
| `KEY_ALIAS` | `junkmaster` | 固定值 |
| `KEY_PASSWORD` | 与 `KEYSTORE_PASSWORD` **完全相同** | PKCS12 下 store 口令与 key 口令必须一致 |

三条硬规则：

1. 这 4 个值必须来自本机**同一把** key（`D:\AndroidDev\keystore\junkmaster-release.jks`），**绝不允许在 CI 里另生成**。
2. `KEYSTORE_BASE64` 是 `base64 -w0` 生成的**单行**文本，粘贴时不要带换行。
3. 配完之后可以把 `delivery\KEYSTORE_BASE64.txt` 删掉，GitHub 侧是只写不读的。

### 0.4 先发一个预发布版（当前就是这一步）

> ⚠️ **必须在 §0.3 配完 4 个 Secrets 之后执行**。顺序反了 CI 会在第一步就失败。
> 万一真忘了：配好 Secrets 后到 Actions → 选中失败的那次 run → 右上 **Re-run all jobs** 即可，不用重打 tag。

```bash
cd /d/安卓清扫app
git push origin v1.0.5-rc1
```

推完打开仓库的 **Actions** 页，约 3~5 分钟走完：单测 → 构建 → 验签 → 发 Release。
成功后 **Releases** 里会出现 `v1.0.5-rc1`，标记为 **Pre-release**，附件是
`JunkMaster-v1.0.5-rc1.apk` 与同名 `.sha256`。

---

## 1. tag 命名即发布语义

| tag | 结果 |
|---|---|
| `v1.0.5-rc1` / `v1.0.5-beta.2`（**含 `-`**） | **Pre-release**（预发布，给真机复测用） |
| `v1.0.5`（不含 `-`） | **正式 Release** |

同一个 workflow 全搞定，不用改代码。真机复测通过后，把 rc 转正的方式见 §4。

---

## 2. 发版前，CI 会替你把这几关

| 关卡 | 不通过会怎样 |
|---|---|
| tag 版本 == `app/build.gradle.kts` 的 `versionName` | 直接失败（防止打错 tag 导致 Release 与 APK 版本不符） |
| `CHANGELOG.md` 里有 `## v<版本>` 小节 | 直接失败（防止 Release 说明为空） |
| 4 个 Secrets 齐备 | 直接失败，并提示去配 Secrets |
| 全部单测通过 | 直接失败 |
| `apksigner` 验签 v2 + 证书 SHA-256 == 冻结指纹 | 直接失败（**指纹不符绝不放行**） |
| APK 里没有 `INTERNET` 权限 | 直接失败 |

冻结指纹（非机密，公开可查）：

```text
aaa47474e78934d2bf52bff6ecd5aabef55566b56b7990a981025d6c9b31557e
```

---

## 3. 每次发新版的固定动作

1. `app/build.gradle.kts`：`versionCode` **+1**（永不复用）、`versionName` 递增补丁位
2. `CHANGELOG.md` 顶部加一节：`## v1.0.6 (8) — 标题（日期）`
3. 本地出包自测一遍（`assembleRelease` + 装到手机上过一遍 `docs/SELF_TEST.md`）
4. `git commit` 改动
5. 打 tag 并推送：

```bash
cd /d/安卓清扫app
git tag -a v1.0.6-rc1 -m "v1.0.6-rc1 预发布：<一句话说明>"
git push origin main
git push origin v1.0.6-rc1
```

> tag 一旦推上去就会被 CI 抓走发版。**推错 tag 只能删了重打**：
> `git push --delete origin v1.0.6-rc1` 然后删掉这次 Release，再重新打。

---

## 4. 把预发布转成正式版

真机复测通过后：

```bash
cd /d/安卓清扫app
git tag -a v1.0.5 -m "v1.0.5 正式版"
git push origin v1.0.5
```

会另起一个**正式** Release（同一个 APK 源码、同一把 key）。旧的 `v1.0.5-rc1` 保持 Pre-release 不动，
作为「预发布 → 正式」的痕迹留在仓库里。**不要**把 rc 的 Release 手动改成正式版，
因为那样 Actions 里就没有正式版的构建记录。

如果不想在 Releases 列表里留着 rc，转正后到该 Release 页面点删除即可（tag 可保留）。

---

## 5. 常见失败与处理

| 现象 | 原因 | 处理 |
|---|---|---|
| `./gradlew: Permission denied`（exit 126） | 仓库在 **Windows** 上维护，`core.fileMode=false` 时 git 不记录可执行位，`gradlew` 以 `100644` 入库，Linux runner 上就没有 x 位 | 已用 `git update-index --chmod=+x gradlew` 修正为 `100755`；workflow 里另有 `chmod +x gradlew` 兜底。以后新增任何 `.sh` 脚本同样要执行这条 |
| `缺少 KEYSTORE_BASE64` | Secrets 没配 | 回 §0.3 |
| `tag 版本(x) 与 versionName(y) 不一致` | tag 打错，或忘了改 `versionName` | 删 tag 重打 |
| `CHANGELOG.md 缺少 vX.Y.Z 小节` | 忘了写更新日志 | 补写后重新打 tag |
| `CI 证书与冻结 key 不一致` | Secrets 里的 keystore 不是本机那一份 | 重新导 `KEYSTORE_BASE64.txt` |
| `keystore was tampered with` / `password was incorrect` | 口令写错，或 `KEY_PASSWORD` 与 `KEYSTORE_PASSWORD` 不一致 | 两者填同一个值 |
| 构建卡住 / `Connection timed out` | `gradle.properties` 里混进了本机代理 | 本地已移到机器级配置；若又出现，检查有没有人提交 `systemProp.*proxy*` |
| APK 装不上 | versionCode 没递增 | 递增后重新发 |

---

## 6. 密钥安全（很重要，一次说清）

- **keystore 永不入库**：`.gitignore` 已排除 `*.jks` / `*.keystore` / `keystore.properties`；
  CI 从 Secrets 还原到 `app/release.jks`，只存在于当次 runner 的临时磁盘。
- **指纹公开、私钥不公开**：`aaa47474…` 是证书指纹，可以写在 README 里；私钥只在
  `D:\AndroidDev\keystore\junkmaster-release.jks` 一处，**不要**传到网盘 / 邮箱 / 聊天工具。
- **私钥泄漏 = 升级链断掉**：Android 只接受「同包名 + 同签名」的覆盖升级。一旦这把 key 外流，
  你**不能**换 key（换了老用户就必须卸载重装、数据全丢），只能一直用下去。所以宁可麻烦也别外发。
- **私钥丢了 = 再也发不出能覆盖安装的包**。建议离线备份两份（U 盘 + 纸质口令分离存放）。

---

## 7. CI 实际执行的步骤（对照 Actions 日志看）

```text
Checkout
Setup JDK 17 (temurin)                  ← 缓存 Gradle，第二次起明显更快
Setup Android SDK + platforms;android-36 / build-tools;36.0.0
Strip accidental local proxy settings    ← 防御性，仓库里本来就没有
Verify tag matches versionName           ← tag / versionName / CHANGELOG 三方对齐
Restore keystore from Secrets            ← 4 个 Secrets → app/release.jks
Write keystore.properties                ← 指向 app/release.jks
Run unit tests                           ← ./gradlew test
Build release APK                        ← ./gradlew assembleRelease（R8 已开）
Verify APK signature (v2 + 冻结指纹)
Verify no INTERNET permission
Compute SHA-256
Prepare release notes                    ← 只取 CHANGELOG 最新一节 + 安装/校验说明
Publish GitHub Release                   ← 含 "-" 的 tag 自动标 Pre-release
```

> CI 构建的 APK 与你本机出的包：**同一份源码、同一把 key、同一个 versionCode**，
> 但因为构建环境差异，字节和 SHA-256 会不同，两个包可以互相覆盖安装。以 Release 页附带的
> `.sha256` 为准即可。
