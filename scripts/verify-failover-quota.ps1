# Differentiating proof: the shared Redis bucket survives an instance death.
# See verify-failover-quota.sh for the full rationale.
#
# Usage: .\scripts\verify-failover-quota.ps1 [nginx_base_url] [kill_after] [total]
param(
    [string] $BaseUrl = "http://localhost:8080",
    [int] $KillAfter = 20,
    [int] $Total = 80,
    [string] $InstanceToKill = "app-instance-2",
    [int] $Capacity = 50,
    [int] $Refill = 10
)

$ErrorActionPreference = "Stop"
$clientKey = "failover-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())-$([guid]::NewGuid().ToString('N').Substring(0, 6))"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "docker is required (to kill $InstanceToKill)."
}
docker inspect $InstanceToKill 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "$InstanceToKill is not running. Start the stack first: docker compose up --build"
}

$allowed = 0
$denied = 0
$proxyErrors = 0
$killed = $false
$hitsBefore = @{}
$hitsAfter = @{}
$start = Get-Date

function Invoke-LimitedRequest {
    param([bool] $AfterKill)
    $raw = & curl.exe -s -S --max-time 3 -w "`n%{http_code}" -H "X-API-Key: $clientKey" "$BaseUrl/api/v1/resource" 2>$null
    if (-not $raw) {
        $script:proxyErrors++
        return
    }
    $lines = $raw -split "`n"
    $status = $lines[-1].Trim()
    $body = ($lines[0..([Math]::Max(0, $lines.Length - 2))] -join "`n")
    if ($status -eq "200") {
        $script:allowed++
        $instance = "unknown"
        if ($body -match '"servedByInstance":"([^"]*)"') {
            $instance = $Matches[1]
        }
        if ($AfterKill) {
            $hitsAfter[$instance] = 1 + $(if ($hitsAfter.ContainsKey($instance)) { $hitsAfter[$instance] } else { 0 })
        } else {
            $hitsBefore[$instance] = 1 + $(if ($hitsBefore.ContainsKey($instance)) { $hitsBefore[$instance] } else { 0 })
        }
    } elseif ($status -eq "429") {
        $script:denied++
    } else {
        $script:proxyErrors++
    }
}

Write-Host "Target:           $BaseUrl/api/v1/resource"
Write-Host "Client key:       $clientKey"
Write-Host "Kill after:       $KillAfter requests  ($InstanceToKill)"
Write-Host "Total requests:   $Total"
Write-Host "Expected cap:     $Capacity burst + $Refill/s refill"
Write-Host ""

for ($i = 1; $i -le $Total; $i++) {
    if ($i -eq ($KillAfter + 1)) {
        Write-Host "--- docker kill $InstanceToKill (after $KillAfter requests) ---"
        docker kill $InstanceToKill | Out-Null
        $killed = $true
        Write-Host ""
    }
    Invoke-LimitedRequest -AfterKill $killed
}

$elapsed = [Math]::Max(1, [int]((Get-Date) - $start).TotalSeconds)
$maxAllowed = $Capacity + ($Refill * $elapsed) + 5

Write-Host ""
Write-Host "Results (${elapsed}s elapsed):"
Write-Host "  allowed (200):     $allowed"
Write-Host "  denied  (429):     $denied"
Write-Host "  proxy errors:      $proxyErrors  (502/timeout after the kill are expected)"
Write-Host "  hard ceiling:      $maxAllowed  ($Capacity + $Refill/s x ${elapsed}s + slack)"
Write-Host ""
Write-Host "200s before kill:"
if ($hitsBefore.Count -eq 0) { Write-Host "  (none)" } else {
    $hitsBefore.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host "  $($_.Name): $($_.Value)" }
}
Write-Host "200s after kill:"
if ($hitsAfter.Count -eq 0) { Write-Host "  (none)" } else {
    $hitsAfter.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host "  $($_.Name): $($_.Value)" }
}
Write-Host ""

$pass = $true
if ($denied -eq 0) {
    Write-Host "FAIL: never hit 429 — quota did not exhaust."
    $pass = $false
}
if ($allowed -gt $maxAllowed) {
    Write-Host "FAIL: allowed $allowed > $maxAllowed. A per-instance in-memory bucket"
    Write-Host "      would look like this after a crash (fresh quota on remaining nodes)."
    $pass = $false
}
$afterKilled = $(if ($hitsAfter.ContainsKey($InstanceToKill)) { $hitsAfter[$InstanceToKill] } else { 0 })
if ($afterKilled -gt 0) {
    Write-Host "FAIL: $InstanceToKill still served $afterKilled request(s) after docker kill."
    $pass = $false
}

Write-Host "Restarting $InstanceToKill..."
docker start $InstanceToKill | Out-Null

if ($pass) {
    Write-Host ""
    Write-Host "PASS: killing $InstanceToKill did not reset the bucket. Remaining instances"
    Write-Host "      kept consuming the same Redis hash; the client still exhausted at ~capacity,"
    Write-Host "      not N× capacity."
    exit 0
}
exit 1
