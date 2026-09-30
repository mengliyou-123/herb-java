#requires -RunAsAdministrator
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Local loopback connections remain usable; remote inbound traffic is blocked.
$rules = @(
    @{ Name = 'Herb Block Remote MySQL'; Port = '3306' },
    @{ Name = 'Herb Block Remote Redis'; Port = '6379' }
)
foreach ($rule in $rules) {
    if (-not (Get-NetFirewallRule -DisplayName $rule.Name -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName $rule.Name -Group 'Herb Project Security' `
            -Direction Inbound -Action Block -Protocol TCP -LocalPort $rule.Port `
            -Profile Any -Enabled True | Out-Null
    }
}

Get-NetFirewallRule -Group 'Herb Project Security' |
    Select-Object DisplayName, Enabled, Action, Direction
