# 兼容性

## MFTR0 静态调查（尚未验证运行支持）

MiTV-MFTR0 / `freeguy` / `OS3.0.107.0.UFTMATV` 已完成实际安装 APK 的只读静态调查，详见 [start_3rd_app 技术报告](mftr0-start-3rd-app-static-analysis.md)。固件存在目标接口及 TVHome 消费路径，但当前 helper 的 `blueplanet` 门禁不接受该设备；本次未执行 Provider 调用或恢复动作，不将它列为已验证兼容。

## 已验证

| 项目 | 值 |
| --- | --- |
| 产品 | MiTV-ASTR0 |
| 设备/板级代号 | `blueplanet` |
| 固件 | `OS2.0.5.0.RSTAATV` |
| Android | 11 / API 30 |
| 内核 | 5.15.78 Android vendor tree |
| 用户空间 | 32-bit ARM (`armeabi-v7a`) |
| 启动链 | Bootloader locked, AVB green |

## 兼容性门禁

`setup.ps1` 在修改设置前读取 `ro.product.device`、`ro.board.platform`、`ro.product.model`、Android 版本和 build fingerprint。默认只有设备代号或板级平台为 `blueplanet` 才继续。

`-AllowUntestedFirmware` 只跳过门禁，不表示兼容。不同固件可能缺少 Xiaomi TVManager provider、`cmd periodic` 或使用不同的 HOME/启动策略。

## 不应直接尝试的设备

- 非小米/红米 Android TV；
- Google TV、Fire TV 或运营商盒子；
- 已解锁并运行第三方 ROM 的设备；
- 企业、酒店、学校等不属于操作者的电视。

请为新型号提交“设备兼容性”Issue，并仅附脱敏后的诊断结果。

