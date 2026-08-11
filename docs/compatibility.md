# 兼容性

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

