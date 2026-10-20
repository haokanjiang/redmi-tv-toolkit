# MFTR0 / freeguy：start_3rd_app 静态分析

分析日期：2026-10-20（Asia/Shanghai）。目标：MiTV-MFTR0，`OS3.0.107.0.UFTMATV`，Android 14。

## 结论

| 问题 | 本次证据支持的答案 |
| --- | --- |
| `start3rdApp` 是否存在 | 存在，是 TVManager `PermissionProvider.call()` 的字符串分派分支，而非同名 Java 方法。 |
| 普通第三方 APK 能否调用 | 静态上存在可达路径：Provider exported，无声明的入口权限，目标分支没有 UID、包名、签名或权限检查。尚未用普通 APK 实测，不能把静态可达当成实机成功。 |
| `start_3rd_app` 用途 | 保存自动启动目标的**包名**，由 TVHome 在特定启动流程中消费；不是组件名、命令、立即启动 API，也不是通用保活开关。 |
| TVHome 是否读取 | 是，完整 DEX/Smali 检索定位三处：MainActivity 的 STR 任务、BootActivity.finish、AlarmReceiver.checkAutoLaunchApp。 |
| 开机/唤醒机制 | 存在冷启动与 STR 相关消费路径，以及广告结束路径；不是每次 SCREEN_ON、回桌面或每次软唤醒都无条件执行。 |
| Toolkit 自动恢复 | 可作为“由 TVHome 启动恢复入口”的候选机制；不等于已支持 MFTR0。现有 helper 的 blueplanet 门禁会在本机阻止执行。 |
| Root / 系统签名 | 目标分支本身没有要求调用方 Root 或系统签名。TVManager 自身以系统 UID 运行；这不使调用方取得系统身份。其他写设置、属性及恢复动作有各自限制。 |
| 实验状态 | 仅 APK 拉取、静态分析及只读设备查询。没有调用 Provider、启动 helper、修改 Settings、禁用包、重启或切换电视电源状态。 |

## 样本与复现边界

设备 fingerprint：`xiaomi/freeguy/freeguy:14/UQ1A.240105.004.A1/OS3.0.107.0.UFTMATV:user/release-keys`。
`ro.product.device=freeguy`、`ro.product.name=freeguy`、`ro.board.platform=mt5879`。
本次 `settings get system start_3rd_app` 的保存输出为空白；不能据此区分“键不存在”和“空字符串”，也不能推断缓存为空。

样本全部位于仓库外的本地目录：
`C:\Users\7sovo\Documents\Codex\2026-10-20\mftr0-static-analysis`（下称 A）。
`apks/` 保存原 APK，`jadx/` 保存 Java/Manifest，`smali/` 保存 Apktool 输出，`evidence/` 保存查询、哈希、日志及字符串清单。
这些文件不属于 Git 交付内容；本文只记录分析结论与位置，不包含厂商源码副本。

| 包 | `pm path` 得到的实际活动路径 | 版本 | SHA-256 |
| --- | --- | --- | --- |
| `com.xiaomi.mitv.tvmanager` | `/data/app/~~58gaTWI18uRnRlFro4GzPw==/com.xiaomi.mitv.tvmanager-Jvg9tiEAos714sxD1Ui5Dw==/base.apk` | `3.04.016.051815` / `30040016` | `782cf92220b01ec0d4214d3592df2643f77c23fb1c3ca1dddac9e772bb8e382b` |
| `com.mitv.tvhome` | `/data/app/~~YCM9cHQFOOECQawjHYFumA==/com.mitv.tvhome-67wWGA3SIFkl5E30bGJPKw==/base.apk` | `4.8.413` / `100413` | `f0a5b149db71ba5660a69bccdd89859668fee13f3a03065ded64fb8d3af04986` |
| `com.xiaomi.mitv.settings` | `/system/priv-app/MiTVSettings/MiTVSettings.apk` | `4.01286` / `401286` | `54db7165c94ea2429015075ed1714d143f0012a1446bd077bb9d114407a0cce9` |

三个包的 `pm path` 本次均只返回一个 APK；拉取逻辑遍历全部 `package:` 行，未写死 data/system 路径。dumpsys 同时包含更新前 system 包条目，本文版本对应拉取的活动 APK，不混用旧条目。

使用 ADB SDK platform-tools；JDK `21.0.12.1+1`；JADX `1.5.6`，选项 `-j 4 --no-replace-consts`；Apktool `3.0.3`，选项 `d -r`（Baksmali）。工具下载来源：[JADX 官方发布](https://github.com/skylot/jadx/releases/tag/v1.5.6)、[Apktool 官方发布](https://github.com/iBotPeaches/Apktool/releases/tag/v3.0.3)。
REA Android MCP 缺少 `REA_JADX_MCP_JAR`，返回 `capability_unavailable`；实际反编译使用上述独立 JADX CLI，不依赖该 MCP。没有为了修复工具而更改电视或 REA 配置。

JADX 完成后分别报告 TVManager 27、TVHome 120、Settings 99 个错误，不能宣称全 APK Java 无遗漏。重点结论用 Smali 交叉核对，错误日志保留在 `A/evidence/*-jadx.log`。

### 证据位置约定

下表及正文中的 Java/Manifest 路径相对于 `A/jadx/<包名>/`，Smali 路径相对于 `A/smali/<tvmanager|tvhome|settings>/`。行号基于本次上述工具输出，不是厂商原始工程行号。可用 APK 哈希、类/方法描述符和 DEX 偏移复查，其他 JADX 版本的行号可能不同。

## TVManager：入口和权限

`resources/AndroidManifest.xml:3` 声明 `android.uid.system`；活动包 dumpsys 也显示 shared UID 1000（`A/evidence/com.xiaomi.mitv.tvmanager-package.txt:259`）。
Manifest `:375–379` 声明 `com.xiaomi.mitv.permission.PermissionProvider`，authority `com.mitv.security.permission`，`exported=true`，运行在 `:centralservice`。Provider 没有 `permission/readPermission/writePermission`，application `:63–72` 也没有继承的 permission。

### start3rdApp 的真实行为

位置：`sources/com/xiaomi/mitv/permission/PermissionProvider.java:45–59,285–292`；Smali `smali/com/xiaomi/mitv/permission/PermissionProvider.smali:1260–1310`，描述符 `call(Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;`。

1. `call` 记录 method 和第二参数，按需初始化权限 helper，然后以字符串匹配分派。
2. method 为 `start3rdApp` 时，从 extras 获取 `start_3rd_app`，缺省值为空字符串。
3. 调用 `Settings.System.putString` 写同名键；该 boolean 结果被丢弃，记录日志。
4. 公共末尾返回 `null`。没有构造成功 Bundle，没有立即 `startActivity`，没有把“写入成功”返回给调用者。

此分支不使用第二个参数 `arg` 作校验，不检查实际 UID/PID、签名、目标包是否存在或字符串是否为合法包名，也不进入权限 helper。空 extras 可导致异常；异常不等价于返回 `null`。初始化 helper 的副作用和系统 ContentProvider 框架依然存在，静态分析没有排除厂商框架额外限制。

因此普通应用通过 ContentResolver.call 存在静态可达路径。第二参数传 TVManager 包名是 Bridge 的协议用法，不能称为真实调用者身份。现代 targetSdk 的包可见性可能需要在测试 APK 声明 provider authority 或包查询；本次未验证不同 targetSdk 的实机行为。

### 相邻分支不能一概而论

| 分支 | 输入与动作 | 自有权限路径 | 返回 |
| --- | --- | --- | --- |
| `start3rdApp` | extras 中固定 `start_3rd_app` → System 同名键 | 无上述 helper 检查 | `null`；忽略写入 boolean |
| `putSetting` | `database/name/value`；仅 secure/system 分支写入 | arg 非空；`w1.b.a(arg,4)==0` | `null`，拒绝与成功不能靠返回区分 |
| `setProperty` | `key/value` → SystemProperties.set | arg 非空；`w1.b.a(arg,16)==0` | 无论通过检查与否均可返回空 Bundle；异常可能外抛 |

位置：PermissionProvider.java `:107–129,257–272`。
`sources/w1/b.java:45–68` 的 `a(String,int)` 通过 Binder calling PID 枚举进程，但把传入的 arg 交给 `b(arg,type)`；没有比较 arg 与枚举出的真实包名是否相等。仅调用 `Binder.getCallingUid()` 并丢弃结果，不能视为 UID 授权。
`b.java:74` 起的 `b(String,int)` 对系统/更新系统应用及缓存系统包放行；非系统包走白名单、证书集合比较和权限位。Java 在此反编译不完整，Smali `smali/w1/b.smali:269–587` 证实证书集合不等会进入拒绝路径（`:487–498`），之后检查权限位。不能根据错误 Java 中空的 if 认定签名检查失效。
`b.smali:592–996` 的 `c()` 从 `/data/system/apk_permissions.xml`，否则应用 files 下 `permissions.xml`，解析 package/cert/item。未读取受保护白名单文件，因此不声称知道它当前授权哪些第三方包。

这表示 `putSetting/setProperty` 的 arg 与调用进程绑定不足，静态上值得单独评估；本次没有调用、验证绕过或测试 `ctl.restart`。系统属性服务/SELinux 对具体属性的最终限制也未测试。

## TVHome：全部字面量读取点

| 类和方法 | Java 位置 | 实际作用与条件 |
| --- | --- | --- |
| `com.mitv.tvhome.MainActivity$15.run(Object...)` | `sources/com/mitv/tvhome/MainActivity.java:459–628`，读键 `:590–591` | `onResume` 安排的 STR 任务中，无广告路径且 `!mAdPlayed` 时尝试启动；受冷启动/STR/静默重启等门控和信号源优先级影响。 |
| `com.mitv.tvhome.BootActivity.finish()` | `sources/com/mitv/tvhome/BootActivity.java:1027–1077`，读键 `:1071–1072` | 广告 Activity 收尾；OneTouchPlay 或 boot autoplay 优先，其次系统键非空目标，再其次 Preferences 的目标，最后结束 Activity。 |
| `com.mitv.tvhome.content.AlarmReceiver.checkAutoLaunchApp(Context)` | `sources/com/mitv/tvhome/content/AlarmReceiver.java:165–232`，读键 `:168` | 非空时写入 TVHome 私有 Preferences.AutoLaunchApp 并返回；**不直接启动**。空时可按 userLevel>1000 和半小时间隔查服务器自动启动配置。 |

Smali 全树对同名字符串的检索仅命中：

- `smali_classes6/com/mitv/tvhome/MainActivity$15.smali:81`。
- `smali_classes6/com/mitv/tvhome/BootActivity.smali:5342`。
- `smali_classes5/com/mitv/tvhome/content/AlarmReceiver.smali:297`。

同一方法复用字符串寄存器，Smali 的三处 const-string 不等于三个 getString 调用；Java 主界面/广告方法各读两次（判空、取值），AlarmReceiver 读一次。
检索覆盖 APK 内所有 10 个 DEX 的静态 Smali。没有发现其他对该字面量的引用；动态生成字符串、运行时下载代码和外部库仍不在此否定结论内。

`com.mitv.tools.AppUtil.openAutoApp(Context,String,Bundle)`，`sources/com/mitv/tools/AppUtil.java:257–275`：先查目标包，取 `getLaunchIntentForPackage`，附可选 extras，再 startActivity；正常完成返回 true，包不存在或其他 Exception 返回 false。调用方传 null extras。目标须有可解析且可启动的普通 launcher 入口；此方法没有改用 getLeanbackLaunchIntentForPackage，也没有创建 Job/Service。
`DialogBootActivity` 继承 BootActivity 且不重写 finish（`sources/com/mitv/tvhome/DialogBootActivity.java:12` 起），因此新式广告对话框路径同样继承该消费行为。

### 触发时间与优先级

`MainActivity.onResume():2382–2451` 在 `:2449` 调度名为 STR 的 `MainActivity$15`。任务并不因名字就只在 STR 执行：它每次进入相应 onResume 时调度，再在 `:503` 附近依据启动状态提前返回。
可用布尔条件描述主要门控：`((!cold && (!str || box)) || mAdPlayed || silentReboot)` 成立时不会进入后面的该键启动分支；精简桌面相关分支另行处理并返回。进入广告流程后，若本地/离线广告可播放，会先 `startBootActivity`；否则在 `!mAdPlayed` 的无广告路径消费该键。
冷启动判定来自 `BootManager.isBootUpLaunch():180–199`：进程内允许标志与 elapsedRealtime<50000ms；并非一个无条件 BOOT_COMPLETED 接收器启动应用。`onBootComplete():231–234` 会清标志。
STR 判定来自 `BootManager.isSTRBootUpLaunch():206–209`，读取 system_str / persist.sys.system_str，1=enter，2=exit。`MainReceiver.onReceive():156–166` 将 `mitv.goto.str_mode` / `mitv.action.STR_BOOT_COMPLETED` 转给 BootManager。
`MainActivity.onStrExit():2568–2574` 在 Activity 已 resumed 时走 startAutoPlay 并 resetSTRStatus，本身不读此键；`onScreenOn():2533` 起同样不直接读取。因而不能保证仅有唤醒广播、TVHome 不 resumed 时也执行第三方启动。

优先级有差异：BootActivity.finish 中 `OneTouchPlay || bootAutoPlay` 总是优先；主界面无广告分支还要求 `mNeedTVSurfaceView` 才进入信号源优先路径。非空但无效的系统键会遮住 Preferences 回退：不会在 openAutoApp=false 后继续尝试另一个自动启动包。
该配置不替代默认 HOME；日常 HOME 键返回并不等于再次开机，不应据此承诺自动恢复。

### 缓存与配置覆盖

`Preferences.java:403–405,1292–1295` 用 SharedPreferences 的 `AutoLaunchApp` 保存值。
`AlarmReceiver.doUpdate():793–829` 在 `sys.cts.running` 为空、后台序列执行至 `:821` 时调用 checkAutoLaunchApp；`onReceive():832–839` 安排下一次定时任务并启动 CheckUpdate。它不是精确每 30 分钟重新启动第三方应用的看门狗。
**非空系统键会复制到私有缓存；清空/删除 System 键不会由这段代码同步清掉缓存。** 即使恢复原系统键，曾写入的新目标仍可能通过 Preferences 回退被启动。普通 shell 能否读取/恢复私有缓存本次未验证，不能许诺用单条 settings 恢复就完全回滚。

TVManager 还有配置写入方：`DeviceCustomizeService.checkSourceNeedUpdata()`，Java `:88–114`，会依据服务端定制 APK 是否有效、已安装及版本等状态调用 `q5.a.W()`；`sources/q5/a.java:443–446` 将定制包名写到同一键。`processDeviceCustomizeSplash` 对某些无效定制状态也会清键。说明这是厂商设备定制/自动启动配置的一部分，后续可能被厂商定制同步覆盖，不能认定永久归 Toolkit 所有。

## MiTVSettings：FallbackHome

Manifest `resources/AndroidManifest.xml:108–120`：FallbackHome exported、directBootAware，MAIN/HOME/DEFAULT，priority=-1000。
`sources/com/xiaomi/mitv/settings/entry/FallbackHome.java:30–36` 在 onCreate 注册 USER_UNLOCKED，初始化主线程 Handler，再 maybeFinish；`:56–70` 在用户已解锁后解析 MAIN+HOME：

- 解析出的 HOME 仍属于 Settings：500ms 后重试。
- 解析到其他包：注销 receiver 并 finish。
- 用户未解锁：等待 USER_UNLOCKED，再重试。

它没有直接 startActivity 跳到 TVHome、Projectivy 或 start_3rd_app 指定包；“跳转”实质是结束临时 HOME，由系统 HOME 流程接续。代码未处理 resolveActivity 为 null 的情形，不应人为制造无 HOME 状态来测试。Java 已与 `A/smali/settings/smali/com/xiaomi/mitv/settings/entry/FallbackHome*.smali` 核对；Settings 全部两个 DEX 的字符串表不含 start_3rd_app/start3rdApp。

## 对比原版 haiktl/redmi-tv-toolkit

本地目标仓库 origin 为 `haokanjiang/redmi-tv-toolkit`，HEAD `73756fcd1d0f52d861cc5fd0469c2d2c845947f2`。分析时两个仓库 ls-remote HEAD 相同。
原版 [XiaomiBridge.java 固定版本](https://github.com/haiktl/redmi-tv-toolkit/blob/73756fcd1d0f52d861cc5fd0469c2d2c845947f2/app/src/local/blueplanet/boothelper/XiaomiBridge.java) 已下载到 A/evidence，与目标仓库同文件逐字 diff 无差异；以下逐项对比的是原版实际代码与 MFTR0 APK，不是推测旧版本固件。

| 原版 Bridge 项目 | MFTR0 核对结果 / 限制 |
| --- | --- |
| authority `com.mitv.security.permission` | 完全匹配 Manifest。 |
| TVMANAGER_PACKAGE 作为 call 的 arg | 包名匹配；arg 是应用传入参数，不是真实身份凭据。start3rdApp 不使用它授权。 |
| `setStartupMediator` 使用 start3rdApp，extras=start_3rd_app=自身包名 | 参数格式吻合；作用只是登记目标，启动由 TVHome 后续执行。 |
| `restoreStartupSetting` 使用 putSetting(system,start_3rd_app,value) | 输入格式吻合，但走 permission type 4，不能从 start3rdApp 可达推导恢复必然成功；返回 null 不能验证，且存在 TVHome 私有缓存残留。 |
| `restartAdbd` 使用 setProperty(ctl.restart,adbd) | 分派与 key/value 格式吻合；走 type 16，最终属性服务权限未验证。本次没有执行。 |
| call 捕获 Throwable 后返回 null | 无法向上层区分异常、未授权、写入失败和正常 null；需要独立读回验证。 |
| DeviceGuard 限制 blueplanet | 本机 device/product=freeguy、platform=mt5879，三个条件均不匹配；Bridge 和 RecoveryActions/MainActivity 都会提前返回。 |
| app Manifest targetSdk=29 的旧固件说明 | MFTR0 的 Provider 目标分支没有 targetSdk 检查，不能把旧 Android 11 注释视为 Android 14 实验证据；框架兼容性仍需分版本测试。 |

本地源码位置：`app/src/local/blueplanet/boothelper/XiaomiBridge.java:20–67`、`DeviceGuard.java:13–25`、`RecoveryActions.java:40–48`、`MainActivity.java:27–35`、`app/AndroidManifest.xml:7–9`。
完整自动恢复还涉及 WRITE_SECURE_SETTINGS、无障碍、ADB 等独立权限与服务限制。目标 start3rdApp 分支没有要求 Root/系统签名，不代表这些动作也无需授权或全部有效。此次不改动 helper、脚本、门禁或原有功能。

## DEX / Smali 交叉证据

直接从 ZIP 中逐个解析 DEX header.string_ids 与 string_data，保存 DEX 哈希、字符串索引和偏移到 `A/evidence/dex-string-search.json`；未只对 ZIP 压缩数据运行 strings。

| 样本 / DEX | 字符串 | string index / string_data offset |
| --- | --- | --- |
| TVManager classes.dex | `start3rdApp` | 28236 / `0x339c4f` |
| TVManager classes.dex | `start_3rd_app` | 28296 / `0x33a417` |
| TVManager classes.dex | `putSetting` | 25302 / `0x32a80e` |
| TVManager classes.dex | `setProperty` | 27450 / `0x335cb5` |
| TVHome classes5.dex | `start_3rd_app` | 41911 / `0x39c338` |
| TVHome classes6.dex | `start_3rd_app` | 57430 / `0x5adac1` |
| Settings 全部 2 DEX | 两个 start 字符串 | 均无匹配 |

TVManager 全部 1 DEX、TVHome 全部 10 DEX、Settings 全部 2 DEX 已枚举。字符串证明存在性，不能单独证明调用路径；前述方法路径依据 Java + Smali。Permission helper 的证书/白名单解析因 Java 不完整，明确以 Smali 为准。

## 适用于 MFTR0 的安全实验方案（本次未执行）

1. 先维持只读基线：保存上述 APK 哈希、版本、默认 HOME、System 键的存在性与值，记录冷启动时长/STR 状态；可在用户自然开机/唤醒期间被动采集日志，不主动广播或切换电源。
2. 在本地做独立“只读诊断/纯日志入口”测试 APK，普通签名，不复用现有 RecoveryActions，不写设置、不操作 ADB、不请求管理策略。先仅分析其 Manifest 与 launcher 入口，验证不会连带执行其他恢复动作；安装和运行都属于后续实验，不在本次范围。
3. 要实测写入机制，必须另外授权有状态测试。先解决 TVHome AutoLaunchApp 私有缓存的可验证备份/恢复问题；若无可验证恢复手段，停止在上述只读阶段。不要通过清 TVHome 数据、禁用系统包或开启 Root 绕过这一条件。
4. 满足可回滚条件后，未来可只登记该纯日志测试包，读回 System 值，随后由用户自然冷启动/STR/返回 HOME，记录哪个调用链启动它及延迟。按“有/无广告、HDMI/信号源优先、静默重启、桌面是否 resumed、默认 HOME”分开记录，不自动循环断电，不触碰 setProperty。
5. 恢复 System 的原始存在性和值、私有缓存原值并验证；不要仅以 Provider 返回 null/空 Bundle 判定成功。测试 APK 尚未安装时无需在本次执行卸载/回滚操作。
6. 只有上述结果成立后，才另行适配 MFTR0 门禁与恢复逻辑；不能为了试验直接把 freeguy 加入支持列表并运行完整 Toolkit。

## 未证实假设与覆盖限制

- 普通第三方 UID 经本机厂商框架实际调用成功，以及不同 targetSdk / 包可见性组合的表现；本次不调用 Provider，因此未获运行时证明。
- SettingsProvider 写入的实机结果、跨用户语义、最终权限拦截；目标代码忽略 boolean。
- 每一次软唤醒都会走相同 TVHome Activity/广告路径；代码明确存在提前返回、状态重置、信号源及模式分支。
- 默认 HOME 改成其他桌面后 TVHome 是否仍会在所有启动方式下进入上述消费路径。
- 现有 TVHome 私有 AutoLaunchApp 的实际值、可无损恢复性、厂商云端后续覆盖频率。
- 所有其他固件/TVManager 更新版本仍保留该接口和权限行为；本文仅绑定样本哈希。
- 下载插件、反射生成的键、外部 framework/native 代码可能另有读写者；未覆盖整机全部系统镜像。
- 原静态调查阶段仅在本地新增技术文档，未调用或修改电视。随后按用户新指令将三个原始 APK 打包存入 `firmware-samples/MFTR0-OS3.0.107.0.UFTMATV/`；DEX、Smali、反编译源码及完整 dumpsys 没有作为独立分析产物上传。
