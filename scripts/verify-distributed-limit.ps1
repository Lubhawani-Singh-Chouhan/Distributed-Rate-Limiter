# Shared-quota proof for the Compose topology (nginx + 3 app instances + Redis).
# Same ceiling as verify-distributed-limit.sh:
#   allowed <= capacity + refill_per_second * elapsed_seconds  (+ 1 token)
#
# Usage:
#   .\scripts\verify-distributed-limit.ps1 [nginx_base_url] [request_count]
#   $env:CAPACITY = 50; $env:REFILL = 10; .\scripts\verify-distributed-limit.ps1 http://localhost:8080 120
param(
    [string] $BaseUrl = "http://localhost:8080",
    [int] $RequestCount = 120,
    [int] $Parallel = 30
)

$ErrorActionPreference = "Stop"
$capacity = if ($env:CAPACITY) { [int]$env:CAPACITY } else { 50 }
$refill = if ($env:REFILL) { [int]$env:REFILL } else { 10 }
$clientKey = "verify-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())-$([guid]::NewGuid().ToString('N').Substring(0, 6))"
$url = "$BaseUrl/api/v1/resource"

if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
    Write-Error "curl.exe is required."
}

$tmpdir = Join-Path ([System.IO.Path]::GetTempPath()) ("verify-limit-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmpdir | Out-Null

Write-Host "Target:       $url"
Write-Host "Client key:   $clientKey"
Write-Host "Requests:     $RequestCount  (parallelism $Parallel)"
Write-Host "Bucket:       capacity=$capacity  refill=$refill/s"
Write-Host ""

$start = [DateTimeOffset]::UtcNow
try {
    for ($offset = 0; $offset -lt $RequestCount; $offset += $Parallel) {
        $batch = @()
        $last = [Math]::Min($offset + $Parallel, $RequestCount)
        for ($i = $offset + 1; $i -le $last; $i++) {
            $psi = New-Object System.Diagnostics.ProcessStartInfo
            $psi.FileName = "curl.exe"
            $psi.Arguments = "-sS --max-time 5 -o `"$tmpdir\$i.body`" -w `"%{http_code}`" -H `"X-API-Key: $clientKey`" `"$url`""
            $psi.RedirectStandardOutput = $true
            $psi.UseShellExecute = $false
            $psi.CreateNoWindow = $true
            $proc = [System.Diagnostics.Process]::Start($psi)
            $batch += [pscustomobject]@{ Proc = $proc; Index = $i }
        }
        foreach ($item in $batch) {
            $code = $item.Proc.StandardOutput.ReadToEnd().Trim()
            $item.Proc.WaitForExit()
            if (-not $code) { $code = "000" }
            Set-Content -Path (Join-Path $tmpdir "$($item.Index).code") -Value $code -NoNewline
        }
    }
}
finally {
    $elapsedMs = [Math]::Max(1, [int]([DateTimeOffset]::UtcNow - $start).TotalMilliseconds)
}

$allowed = 0
$denied = 0
$errors = 0
$instanceHits = @{}
for ($i = 1; $i -le $RequestCount; $i++) {
    $codePath = Join-Path $tmpdir "$i.code"
    $status = if (Test-Path $codePath) { (Get-Content $codePath -Raw).Trim() } else { "000" }
    if ($status -eq "200") {
        $allowed++
        $bodyPath = Join-Path $tmpdir "$i.body"
        $instance = "unknown"
        if (Test-Path $bodyPath) {
            $body = Get-Content $bodyPath -Raw
            if ($body -match '"servedByInstance":"([^"]*)"') {
                $instance = $Matches[1]
            }
        }
        if (-not $instanceHits.ContainsKey($instance)) { $instanceHits[$instance] = 0 }
        $instanceHits[$instance]++
    }
    elseif ($status -eq "429") {
        $denied++
    }
    else {
        $errors++
    }
}

Remove-Item -Recurse -Force $tmpdir

$earned = [Math]::Floor($refill * $elapsedMs / 1000)
$maxAllowed = $capacity + $earned + 1

Write-Host "Results ($elapsedMs ms):"
Write-Host "  allowed (200):  $allowed"
Write-Host "  denied (429):   $denied"
Write-Host "  other:          $errors"
Write-Host "  ceiling:        $maxAllowed  (= $capacity + $refill/s * ${elapsedMs}ms + 1)"
Write-Host ""
Write-Host "200s per instance:"
if ($instanceHits.Count -eq 0) {
    Write-Host "  (none)"
}
else {
    foreach ($name in ($instanceHits.Keys | Sort-Object)) {
        Write-Host "  ${name}: $($instanceHits[$name])"
    }
}
Write-Host ""

$pass = $true
if ($errors -gt 0) {
    Write-Host "FAIL: $errors response(s) were not 200 or 429. The ceiling is only meaningful when the stack answered."
    $pass = $false
}
if ($allowed -gt $maxAllowed) {
    Write-Host "FAIL: allowed $allowed > $maxAllowed."
    Write-Host "      One shared bucket cannot grant more than capacity + refill*time."
    Write-Host "      N independent buckets (one per app instance) can."
    $pass = $false
}
if ($instanceHits.Count -lt 2 -and $allowed -ge 2) {
    Write-Host "WARN: 200s came from fewer than 2 instances. Point this at nginx (:8080), not a single app port."
}

if ($pass) {
    Write-Host "PASS: allowed $allowed <= $maxAllowed over ${elapsedMs}ms for one client."
    exit 0
}
exit 1
