# MFTR0 / OS3.0.107.0.UFTMATV 系统 APK 样本

三个独立 ZIP 包含 2026-10-20 从 MiTV-MFTR0（`freeguy`）实际安装路径读取的原始 APK。设备构建标识为 `xiaomi/freeguy/freeguy:14/UQ1A.240105.004.A1/OS3.0.107.0.UFTMATV:user/release-keys`。它们用于核对 [静态分析](../../docs/mftr0-start-3rd-app-static-analysis.md)，不是本项目编译的应用。

| ZIP（内含同名 APK） | 包名 | 活动安装位置 | APK SHA-256 |
| --- | --- | --- | --- |
| `com.xiaomi.mitv.tvmanager-0.apk.zip` | `com.xiaomi.mitv.tvmanager` | `/data/app` | `782cf92220b01ec0d4214d3592df2643f77c23fb1c3ca1dddac9e772bb8e382b` |
| `com.mitv.tvhome-0.apk.zip` | `com.mitv.tvhome` | `/data/app` | `f0a5b149db71ba5660a69bccdd89859668fee13f3a03065ded64fb8d3af04986` |
| `com.xiaomi.mitv.settings-0.apk.zip` | `com.xiaomi.mitv.settings` | `/system/priv-app` | `54db7165c94ea2429015075ed1714d143f0012a1446bd077bb9d114407a0cce9` |

各 ZIP 的 SHA-256 依次为 `8b5092e8acf90f192a6af4e8c3dfafc48889c95c78ac690f6acb0f0547a7d187`、`f260da6e0fc6c26451ba1edf6bc10274051cbcd863f56d915be842345243102d`、`61303eb65467cbd4bd8c060e47f576b3add903e5c804feb95b1bfe1752134103`。`SHA256.json` 记录各 APK 的字节数及摘要。三个 ZIP 均为普通 Git 文件，解压即可得到 APK。

小米及相关软件的权利归各自权利人所有。此目录仅记录该固件的原始样本与校验信息，不能用这些 APK 判断其他型号或固件版本的行为。
