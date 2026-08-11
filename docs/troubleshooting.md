# 故障排查

## ADB 开关回弹或没有确认框

首次安装仍需要一次有效的 ADB 授权。连接成功后运行 `setup.ps1`；helper 会保持 `Settings.Global.adb_enabled=1` 并在必要时有界重启 adbd。若电视 IP 变化，先在路由器查看新地址并设置 DHCP 保留。

## Projectivy 显示“已启用：否”

运行：

```powershell
pwsh -NoProfile -File scripts/setup.ps1 -Device 192.0.2.10:5555 -RepairOnly
```

helper 会在当前启动周期执行一次“移除组件 -> 写回规范组件名”的重绑，从 crashed-service 列表恢复。它不会读取窗口内容。

## 应用几秒后自动退出

先运行 `verify.ps1`，确认 `PeriodicCleaner` 已关闭。若固件没有 `cmd periodic`，请附脱敏诊断结果提交兼容性 Issue。

## 开机仍回到红米桌面

检查验证输出中的 HOME 是否为：

`com.spocky.projengmenu/.ui.home.MainActivity`

然后从电视设置确认 helper 的设备管理员已激活。设备管理员不使用锁屏、抹除或密码策略，仅降低固件把 helper 标记为 stopped 的概率。

## 回滚后 ADB 仍开启

这是默认安全行为，防止回滚中途断连。如果确实要恢复安装前的 ADB 开关，使用 `rollback.ps1 -RestoreAdbState`；该动作可能立即让设备离线。

