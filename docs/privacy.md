# 隐私

本项目没有服务器、遥测、广告、账户系统或自动日志上传。

电脑端备份默认写到仓库外的用户目录：

`%USERPROFILE%\.redmi-tv-toolkit\backups`

备份包含固件 fingerprint、HOME 组件、无障碍组件列表和少量系统设置，不包含应用数据或 ADB 私钥。诊断文件可能包含局域网设备地址和安装包列表，公开前仍应人工检查并脱敏。

永远不要提交：

- `%USERPROFILE%\.android\adbkey`；
- 完整 `logcat`、账户数据库或配网文件；
- 从电视拉取的厂商 APK、分区镜像或 OTA 私有内容；
- 真实 IP、MAC、序列号、设备名和家庭网络拓扑。

