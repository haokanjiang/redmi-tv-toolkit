# Redmi TV Toolkit

把特定红米电视从“每次开机回到官方桌面、ADB 开关不可靠、无障碍服务掉线”改造成一个可持续使用的 Android TV 工作终端。

> 当前版本提供的是**无 Root 的系统增强**，不是 Root 工具。它不会解锁 bootloader、关闭 AVB 或修改系统分区。Root 的真实研究状态见 [docs/root-status.md](docs/root-status.md)。

## 已验证效果

在 MiTV-ASTR0 / `blueplanet`、HyperOS `OS2.0.5.0.RSTAATV`（Android 11）上实机验证：

- Projectivy 成为系统 HOME，按主页键回到 Projectivy；
- 冷启动和软关机唤醒后重新拉起 Projectivy；
- 修复 Projectivy 无障碍“已启用：否”和服务落入崩溃名单的问题；
- 保持 `adb_enabled=1`，在小米设置界面开关/确认框失灵后自动恢复；
- 关闭该固件的 `PeriodicCleaner`，避免 YouTube 等应用空闲数秒后被退回上一级；
- 提供安装前备份、安装后验证、独立诊断和一键回滚。

## 重要边界

- 首次运行前，电视必须已经临时开启 ADB，并在电视上接受这台电脑的调试授权。
- 默认只接受 `blueplanet` 设备。其他固件必须显式使用 `-AllowUntestedFirmware`，风险自担。
- ADB 重启后的可达性仍受电视网络、DHCP 地址和小米固件行为影响；建议给电视设置 DHCP 地址保留。
- Projectivy 是独立第三方软件，本项目从其[官方 GitHub Releases](https://github.com/spocky/miproja1/releases)下载，不复制其 APK。
- 不会复制你的账户、应用数据、网络配置、Projectivy 个性化布局或付费许可。

## 最快安装

要求：Windows 10/11、PowerShell 7、Android Platform Tools；电脑和电视位于可信局域网。

```powershell
git clone https://github.com/haiktl/redmi-tv-toolkit.git
cd redmi-tv-toolkit
pwsh -NoProfile -File scripts/setup.ps1 -Device 192.0.2.10:5555
```

把示例地址替换为电视的实际 IP。脚本会：检测固件、保存设置快照、下载官方 Projectivy 和本项目 Release APK、安装并授权、设置 HOME、启动恢复服务，最后运行验证。

如果你从源码构建了 APK：

```powershell
pwsh -NoProfile -File app/build.ps1
pwsh -NoProfile -File scripts/setup.ps1 -Device 192.0.2.10:5555 `
  -HelperApk app/dist/redmi-tv-toolkit.apk
```

## 验证与回滚

```powershell
pwsh -NoProfile -File scripts/verify.ps1 -Device 192.0.2.10:5555
pwsh -NoProfile -File scripts/diagnose.ps1 -Device 192.0.2.10:5555
pwsh -NoProfile -File scripts/rollback.ps1 -Device 192.0.2.10:5555
```

回滚脚本默认使用本机最近一次备份，恢复 HOME、无障碍列表、启动应用和 PeriodicCleaner，然后移除辅助应用；默认保留当前 ADB 状态，避免回滚中途失联。

## 仓库结构

| 目录 | 用途 |
| --- | --- |
| `app/` | 电视端最小化恢复 APK 的完整源码和构建脚本 |
| `scripts/` | 安装、诊断、验证、回滚和静态自检 |
| `docs/` | 原理、兼容性、故障排查、隐私和 Root 状态 |
| `.github/` | CI、Issue 表单、PR 模板和 Release 配置 |

## 文档

- [工作原理](docs/how-it-works.md)
- [兼容性与风险](docs/compatibility.md)
- [故障排查](docs/troubleshooting.md)
- [隐私说明](docs/privacy.md)
- [Root 研究现状](docs/root-status.md)
- [参与贡献](CONTRIBUTING.md) / [安全策略](SECURITY.md)

## 许可证与商标

本项目源码采用 Apache-2.0。Redmi、Xiaomi、HyperOS、Android、Projectivy 等名称属于各自权利人；本项目与小米及 Projectivy 作者无隶属或背书关系。

