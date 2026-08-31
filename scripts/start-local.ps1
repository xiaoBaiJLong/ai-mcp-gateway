[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localConfigPath = Join-Path $projectRoot 'config/local.env'
$logDirectory = Join-Path $projectRoot 'logs'

function Import-LocalEnvironment([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "缺少本机配置文件：$path。请复制 config/local.env.example 为 config/local.env 并填写 Nacos 地址。"
    }

    Get-Content -LiteralPath $path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $parts = $line.Split('=', 2)
            if ($parts.Length -eq 2) {
                [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
            }
        }
    }
}

function Use-MachineJava21 {
    $machineJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    if ($machineJavaHome -and (Test-Path -LiteralPath (Join-Path $machineJavaHome 'bin/java.exe'))) {
        $machineJavaVersion = & (Join-Path $machineJavaHome 'bin/java.exe') -version 2>&1 | Select-Object -First 1
        if ($machineJavaVersion -match 'version "21') {
            $env:JAVA_HOME = $machineJavaHome
            $env:Path = (Join-Path $machineJavaHome 'bin') + ';' + $env:Path
        }
    }
}

function Assert-Java21 {
    $javaVersion = & java -version 2>&1 | Select-Object -First 1
    if ($javaVersion -notmatch 'version "21') {
        throw "需要 Java 21；当前检测到：$javaVersion"
    }
}

function Assert-TcpPortReachable([string]$serverHost, [int]$serverPort, [string]$displayName) {
    $tcpClient = [System.Net.Sockets.TcpClient]::new()
    try {
        $connectTask = $tcpClient.ConnectAsync($serverHost, $serverPort)
        if (-not $connectTask.Wait(5000)) {
            throw "无法在 5 秒内连接 $displayName"
        }
        $null = $connectTask.GetAwaiter().GetResult()
    } catch {
        throw "无法连接 $displayName。请检查 config/local.env、SSH 隧道或网络连通性。"
    } finally {
        $tcpClient.Dispose()
    }
}

function Assert-NacosReachable {
    if ([string]::IsNullOrWhiteSpace($env:NACOS_SERVER_ADDR)) {
        throw 'config/local.env 必须提供 NACOS_SERVER_ADDR。'
    }

    $nacosAddress = ($env:NACOS_SERVER_ADDR -split ',')[0].Trim()
    $addressParts = $nacosAddress.Split(':')
    [int]$nacosPort = 0
    if ($addressParts.Count -ne 2 -or -not [int]::TryParse($addressParts[1], [ref]$nacosPort)) {
        throw "NACOS_SERVER_ADDR 必须是 host:port 格式；当前值：$nacosAddress"
    }

    Assert-TcpPortReachable $addressParts[0] $nacosPort "Nacos HTTP 端口：$nacosAddress"
    Assert-TcpPortReachable $addressParts[0] ($nacosPort + 1000) "Nacos gRPC 端口：$($addressParts[0]):$($nacosPort + 1000)"
}

function Set-DefaultPorts {
    if ([string]::IsNullOrWhiteSpace($env:MCP_GATEWAY_PORT)) {
        $env:MCP_GATEWAY_PORT = '8080'
    }
    if ([string]::IsNullOrWhiteSpace($env:MOCK_USER_SERVICE_PORT)) {
        $env:MOCK_USER_SERVICE_PORT = '8081'
    }
}

function Get-StartupTimeoutSeconds {
    if ([string]::IsNullOrWhiteSpace($env:LOCAL_STARTUP_TIMEOUT_SECONDS)) {
        return 180
    }

    [int]$timeoutSeconds = 0
    if (-not [int]::TryParse($env:LOCAL_STARTUP_TIMEOUT_SECONDS, [ref]$timeoutSeconds) -or $timeoutSeconds -lt 1) {
        throw 'LOCAL_STARTUP_TIMEOUT_SECONDS 必须是大于 0 的整数。'
    }
    return $timeoutSeconds
}

function Stop-StartedProcess([System.Diagnostics.Process]$process) {
    if ($process -and -not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F | Out-Null
    }
}

function Wait-ForEndpoint([System.Diagnostics.Process]$process, [string]$name, [string]$url, [string]$standardLog, [string]$errorLog, [int]$timeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    $lastError = ''
    while ((Get-Date) -lt $deadline) {
        if ($process.HasExited) {
            throw "$name 启动失败（进程已退出）。请查看：$standardLog 和 $errorLog"
        }
        try {
            $response = Invoke-WebRequest -Uri $url -TimeoutSec 2 -UseBasicParsing
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 1
    }

    throw "$name 未在 $timeoutSeconds 秒内就绪：$lastError。请查看：$standardLog 和 $errorLog"
}

Import-LocalEnvironment $localConfigPath
Set-DefaultPorts
Use-MachineJava21
Assert-Java21
Assert-NacosReachable
$startupTimeoutSeconds = Get-StartupTimeoutSeconds
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

$gatewayLog = Join-Path $logDirectory 'mcp-gateway-server.log'
$gatewayErrorLog = Join-Path $logDirectory 'mcp-gateway-server-error.log'
$mockUserLog = Join-Path $logDirectory 'mock-user-service.log'
$mockUserErrorLog = Join-Path $logDirectory 'mock-user-service-error.log'
$webAdminLog = Join-Path $logDirectory 'web-admin.log'
$webAdminErrorLog = Join-Path $logDirectory 'web-admin-error.log'

$mavenSettings = Join-Path $projectRoot '.mvn/settings.xml'
$webAdminRoot = Join-Path $projectRoot 'web-admin'
if (-not (Test-Path -LiteralPath (Join-Path $webAdminRoot 'node_modules'))) {
    & npm.cmd install --prefix $webAdminRoot
    if ($LASTEXITCODE -ne 0) {
        throw '管理端依赖安装失败，未启动任何后台服务。请检查 npm 输出后重试。'
    }
}

& mvn.cmd -gs $mavenSettings -s $mavenSettings -pl 'mcp-gateway-server,mock-user-service' package -DskipTests
if ($LASTEXITCODE -ne 0) {
    throw '后端构建失败，未启动任何后台服务。请检查 Maven 输出后重试。'
}

$javaExecutable = Join-Path $env:JAVA_HOME 'bin/java.exe'
$gatewayJar = Join-Path $projectRoot 'mcp-gateway-server/target/mcp-gateway-server-0.0.1-SNAPSHOT.jar'
$mockUserJar = Join-Path $projectRoot 'mock-user-service/target/mock-user-service-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $gatewayJar) -or -not (Test-Path -LiteralPath $mockUserJar)) {
    throw '后端构建未生成可运行 JAR，未启动任何后台服务。'
}

$gatewayProcess = Start-Process -PassThru -WindowStyle Hidden -FilePath $javaExecutable -ArgumentList '-jar', $gatewayJar -WorkingDirectory $projectRoot -RedirectStandardOutput $gatewayLog -RedirectStandardError $gatewayErrorLog
$mockUserProcess = Start-Process -PassThru -WindowStyle Hidden -FilePath $javaExecutable -ArgumentList '-jar', $mockUserJar -WorkingDirectory $projectRoot -RedirectStandardOutput $mockUserLog -RedirectStandardError $mockUserErrorLog
$webAdminProcess = Start-Process -PassThru -WindowStyle Hidden -FilePath 'npm.cmd' -ArgumentList 'run', 'dev', '--', '--host', '127.0.0.1' -WorkingDirectory $webAdminRoot -RedirectStandardOutput $webAdminLog -RedirectStandardError $webAdminErrorLog

try {
    Wait-ForEndpoint $gatewayProcess 'MCP 网关' "http://127.0.0.1:$env:MCP_GATEWAY_PORT/actuator/health" $gatewayLog $gatewayErrorLog $startupTimeoutSeconds
    Wait-ForEndpoint $mockUserProcess '用户模拟服务' "http://127.0.0.1:$env:MOCK_USER_SERVICE_PORT/v3/api-docs" $mockUserLog $mockUserErrorLog $startupTimeoutSeconds
    Wait-ForEndpoint $webAdminProcess '管理端' 'http://127.0.0.1:5173' $webAdminLog $webAdminErrorLog $startupTimeoutSeconds
} catch {
    Stop-StartedProcess $gatewayProcess
    Stop-StartedProcess $mockUserProcess
    Stop-StartedProcess $webAdminProcess
    throw
}

Write-Host '已启动本机服务：'
Write-Host "- 管理端：http://127.0.0.1:5173"
Write-Host "- MCP 网关健康检查：http://127.0.0.1:$env:MCP_GATEWAY_PORT/actuator/health"
Write-Host "- 用户模拟服务 OpenAPI：http://127.0.0.1:$env:MOCK_USER_SERVICE_PORT/v3/api-docs"
Write-Host "日志目录：$logDirectory"
