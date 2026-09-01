# 云端开发环境

本项目本机开发依赖云端 Nacos。启动本机网关和模拟用户服务前，先建立 SSH 隧道；不要将 Nacos 端口暴露到公网。

## 已验证的连接信息

- 云服务器：`115.159.212.177`
- SSH 用户：`ubuntu`
- 公钥路径：`C:\Users\WCQ\.ssh\id_rsa.pub`
- SSH 客户端应使用与该公钥匹配的本机私钥 `C:\Users\WCQ\.ssh\id_rsa`；私钥不得复制、提交或记录在仓库中。

## Nacos 隧道

云端 Nacos 容器只绑定云主机回环地址：云端 `18848` 转发到容器 `8848`，云端 `19848` 转发到容器 `9848`。本机配置使用相同的两个端口：

```powershell
ssh -N -i C:\Users\WCQ\.ssh\id_rsa `
  -L 18848:127.0.0.1:18848 `
  -L 19848:127.0.0.1:19848 `
  ubuntu@115.159.212.177
```

保持该终端运行后，再执行：

```powershell
.\scripts\start-local.ps1
```

启动脚本会检查 `127.0.0.1:18848` 与 `127.0.0.1:19848`；任一端口不可达时，不会启动后端服务。

## 云端状态检查

以下命令仅读取云端服务状态：

```powershell
ssh -i C:\Users\WCQ\.ssh\id_rsa ubuntu@115.159.212.177 `
  "docker ps --format '{{.Names}} {{.Status}} {{.Ports}}'"
```
