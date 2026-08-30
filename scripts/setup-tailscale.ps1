#Requires -RunAsAdministrator
[CmdletBinding()]
param(
    [int]$AlexaPort = 8081,
    [int]$AppPort   = 8443,
    [switch]$Off
)

$ErrorActionPreference = 'Stop'

$ts = Get-Command tailscale -ErrorAction SilentlyContinue
if (-not $ts) {
    $ts = "$env:ProgramFiles\Tailscale\tailscale.exe"
    if (-not (Test-Path $ts)) { throw "tailscale.exe not found" }
} else { $ts = $ts.Source }

$status = & $ts status --json | ConvertFrom-Json
$tsHost = $status.Self.DNSName.TrimEnd('.')
if (-not $tsHost) { throw "no DNSName; is tailscaled running and logged in?" }

if ($Off) {
    & $ts funnel --https=443 --set-path=/alexa off
    Write-Host "funnel removed" -ForegroundColor Yellow
    & $ts funnel status
    return
}

Write-Host "host:                $tsHost"
Write-Host "app  (tailnet-only): https://${tsHost}:$AppPort/"
Write-Host "alexa (public):      https://$tsHost/alexa"
Write-Host ""

$listening = Get-NetTCPConnection -State Listen -LocalPort $AlexaPort -ErrorAction SilentlyContinue
if (-not $listening) {
    Write-Warning "nothing listening on port $AlexaPort"
} elseif ($listening.LocalAddress -notcontains '127.0.0.1') {
    Write-Warning "port $AlexaPort is listening on $($listening.LocalAddress -join ', ') - bind to 127.0.0.1 only"
}

& $ts funnel --https=443 --set-path=/alexa "http://localhost:$AlexaPort"

Write-Host ""
& $ts funnel status

Write-Host "`n--- local probe ---"
try {
    $r = Invoke-WebRequest -Uri "http://localhost:$AlexaPort/alexa" -Method POST `
         -SkipHttpErrorCheck -TimeoutSec 5
    Write-Host $r.StatusCode
} catch { Write-Host "no response: $($_.Exception.Message)" }

Write-Host "`n--- public probe: run from OFF the tailnet (phone on LTE) ---"
Write-Host "curl -i https://$tsHost/alexa"