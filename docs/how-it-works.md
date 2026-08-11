# 工作原理

工具由电脑端 PowerShell 脚本和电视端小型 Android 应用组成。

## 安装阶段

1. 电脑通过已经授权的 ADB 读取只读设备属性并执行兼容性门禁。
2. 保存 HOME、无障碍服务列表、ADB 开关、`start_3rd_app` 和定期清理状态。
3. 安装官方 Projectivy 与本项目 helper APK。
4. 通过 ADB 授予 helper `WRITE_SECURE_SETTINGS`，设置 Projectivy 为 HOME。
5. 合并而不是覆盖已有无障碍服务，启用 Projectivy 与 helper。
6. 关闭该固件的 `PeriodicCleaner`，然后启动 helper 做最终恢复。

## 电视端恢复

- `BootReceiver` 在冷启动、用户解锁和应用更新后安排恢复。
- `JobScheduler`、一次性 Alarm 和前台 Service 提供多个有界重试窗口。
- `BootAccessibilityService` 不读取界面内容；它只观察 `adb_enabled`，被固件改回 0 时恢复为 1。
- 每次冷启动只强制重绑一次 Projectivy 无障碍，清除 Android 的 crashed-service 状态，避免循环重绑。
- helper 作为 Xiaomi TVHome 的启动中介：先修复 ADB/无障碍，再把前台交还 Projectivy。

## 有意限制的系统桥

目标固件的 TVManager provider 能以系统上下文执行若干操作。公开版只封装两项固定动作：

- 把 TVHome 的启动中介设置为 helper；
- 在 ADB 已启用但 adbd 未运行时请求 `ctl.restart=adbd`，并带有每次启动次数/时间限制。

它不提供通用属性写入、命令执行、任意设置写入、静默安装或 HIDL 探针。

