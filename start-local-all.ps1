Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$backendRoot = $PSScriptRoot
$frontendRoot = Join-Path (Split-Path $backendRoot -Parent) '前端程序省赛版\herb-vue'
$redisRoot = 'D:\develop\redis'

function Test-LocalPort([int]$port) {
    $connection = [System.Net.Sockets.TcpClient]::new()
    try {
        $attempt = $connection.ConnectAsync('127.0.0.1', $port)
        return $attempt.Wait(1500) -and $connection.Connected
    } catch {
        return $false
    } finally {
        $connection.Dispose()
    }
}

function Wait-LocalPort([int]$port, [int]$seconds) {
    for ($elapsed = 0; $elapsed -lt $seconds; $elapsed++) {
        if (Test-LocalPort $port) { return }
        Start-Sleep -Seconds 1
    }
    throw "Local port $port did not become ready within $seconds seconds. Check the start logs."
}

if (-not (Test-Path -LiteralPath (Join-Path $backendRoot '.env.local'))) {
    throw 'Missing backend .env.local. See SECURITY_HARDENING.md.'
}
if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot 'package.json'))) {
    throw "Frontend project not found: $frontendRoot"
}
if (-not (Test-LocalPort 3306)) {
    throw 'MySQL is not listening on 127.0.0.1:3306. Start the MySQL service first.'
}

if (-not (Test-LocalPort 6379)) {
    $redisExecutable = Join-Path $redisRoot 'redis-server.exe'
    $redisConfig = Join-Path $redisRoot 'redis.windows.conf'
    if (-not (Test-Path -LiteralPath $redisExecutable) -or
            -not (Test-Path -LiteralPath $redisConfig)) {
        throw 'Redis is not running and its local installation was not found.'
    }
    Start-Process -FilePath $redisExecutable -ArgumentList "`"$redisConfig`"" `
        -WorkingDirectory $redisRoot -WindowStyle Hidden | Out-Null
    Wait-LocalPort 6379 15
}

if (-not (Test-LocalPort 8081)) {
    $backendScript = Join-Path $backendRoot 'start-local.ps1'
    Start-Process -FilePath 'powershell.exe' `
        -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$backendScript`"") `
        -WorkingDirectory $backendRoot -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $backendRoot 'backend-start.local.log') `
        -RedirectStandardError (Join-Path $backendRoot 'backend-error.local.log') | Out-Null
    Wait-LocalPort 8081 90
}

if (-not (Test-LocalPort 5173)) {
    Start-Process -FilePath 'cmd.exe' `
        -ArgumentList @('/c', 'npm run dev -- --host 127.0.0.1') `
        -WorkingDirectory $frontendRoot -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $frontendRoot 'frontend-start.local.log') `
        -RedirectStandardError (Join-Path $frontendRoot 'frontend-error.local.log') | Out-Null
    Wait-LocalPort 5173 30
}

$loginRouteStatus = 0
try {
    $response = Invoke-WebRequest -Uri 'http://127.0.0.1:5173/api/user/userInfo' `
        -UseBasicParsing -TimeoutSec 5
    $loginRouteStatus = [int]$response.StatusCode
} catch {
    if ($_.Exception.Response) {
        $loginRouteStatus = [int]$_.Exception.Response.StatusCode
    }
}
if ($loginRouteStatus -ne 401) {
    throw "Frontend proxy cannot reach the backend (HTTP $loginRouteStatus). Check the start logs."
}

Write-Host 'Local project is ready: http://127.0.0.1:5173/#/login'
