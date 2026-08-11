# Root 研究现状

## 结论

截至 2026-08-11，本项目**没有获得 Root**，也没有可公开复现的完整 Root 链。当前工具达到的是持久化的无 Root 系统增强。

## 已确认的启动链

- Bootloader 为 locked；
- Verified Boot 状态为 green，verity enforcing；
- `ro.oem_unlock_supported=0`；
- A/B 动态分区与 AVB 1.1/1.2；
- user build 的 adbd 即使设置 `service.adb.root=1` 仍会按编译策略降权。

因此，以下任一项才可能形成稳定 Root：官方解锁能力、设备信任的签名密钥、可验证的启动链漏洞，或从应用/驱动到内核的完整本地提权链。目前均未取得。

## 已排除或未闭合的方向

- 单纯修改 ADB 属性无法把 user-build adbd 变成 Root；
- 已发现的 TVManager 系统 provider 能力可以解决设置、启动和安装类问题，但不等于 UID 0；
- 部分厂商 HAL 可到达，但 Binder/SELinux 边界阻止了关键子接口返回；
- `/dev/mali0` 等内核攻击面需要精确驱动版本、可靠利用和设备级回滚方案，尚未形成可验证链；
- 没有 bootloader 私钥、设备签名密钥或官方 unlock token。

## 项目政策

Root 相关贡献必须有明确型号/固件范围、可重复证据、失败恢复方案和负责任披露记录。仓库不接受声称成功但没有 UID、SELinux、启动后持久性和冷启动验证的“Root”。

