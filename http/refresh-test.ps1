# Checks refresh token rotation, the 30 second grace window, stolen token detection and logout
# against a running backend on http://localhost:8080.
#
# Needs: the backend running, and the user arjun@example.com / password123 registered
# (request 1 in http/auth.http).
#
# Run from the finledger root folder:
#   powershell -ExecutionPolicy Bypass -File .\http\refresh-test.ps1

$base = "http://localhost:8080"
$loginBody = '{"email":"arjun@example.com","password":"password123"}'
$script:failures = 0

# Sends a POST with an optional refresh cookie and optional JSON body.
# Returns the HTTP status and the new refresh token from Set-Cookie ("" means the cookie was cleared, $null means no Set-Cookie).
function Invoke-Auth([string]$path, [string]$token, [string]$jsonBody) {
    $curlArgs = @("-s", "-i", "-X", "POST", "$base$path")
    $tmp = $null

    if ($token) {
        $curlArgs += @("-H", "Cookie: finledger_refresh=$token")
    }
    if ($jsonBody) {
        $tmp = [System.IO.Path]::GetTempFileName()
        Set-Content -Path $tmp -Value $jsonBody -Encoding ascii -NoNewline
        $curlArgs += @("-H", "Content-Type: application/json", "--data-binary", "@$tmp")
    }

    $lines = & curl.exe @curlArgs
    if ($tmp) { Remove-Item $tmp }

    $status = [int]($lines[0].Split(' ')[1])
    $newToken = $null
    foreach ($line in $lines) {
        if ($line -match '^Set-Cookie:\s*finledger_refresh=([^;]*)') {
            $newToken = $Matches[1]
            break
        }
    }

    return @{ Status = $status; Token = $newToken }
}

function Check([string]$label, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "PASS  $label  ($detail)" -ForegroundColor Green
    } else {
        Write-Host "FAIL  $label  ($detail)" -ForegroundColor Red
        $script:failures++
    }
}

Write-Host ""
Write-Host "Rotation and stolen token detection" -ForegroundColor Cyan

$r = Invoke-Auth "/api/auth/login" $null $loginBody
$A = $r.Token
Check "1. Log in" ($r.Status -eq 200 -and $A) "status $($r.Status), token $A"

$r = Invoke-Auth "/api/auth/refresh" $A $null
$B = $r.Token
Check "2. Refresh with A gives a new token B" ($r.Status -eq 200 -and $B -and $B -ne $A) "status $($r.Status)"

$r = Invoke-Auth "/api/auth/refresh" $A $null
Check "3. Refresh with A again within 30s (second tab) is allowed, no new cookie" ($r.Status -eq 200 -and $null -eq $r.Token) "status $($r.Status)"

$r = Invoke-Auth "/api/auth/refresh" $B $null
$C = $r.Token
Check "4. Refresh with B gives C" ($r.Status -eq 200 -and $C -and $C -ne $B) "status $($r.Status)"

$r = Invoke-Auth "/api/auth/refresh" $A $null
Check "5. Replay of old token A is caught and rejected" ($r.Status -eq 401) "status $($r.Status)"

$r = Invoke-Auth "/api/auth/refresh" $C $null
Check "6. Newest token C is now dead too (whole session revoked)" ($r.Status -eq 401) "status $($r.Status)"

Write-Host ""
Write-Host "Logout" -ForegroundColor Cyan

$r = Invoke-Auth "/api/auth/login" $null $loginBody
$D = $r.Token
Check "7. Log in again" ($r.Status -eq 200 -and $D) "status $($r.Status)"

$r = Invoke-Auth "/api/auth/logout" $D $null
Check "8. Log out returns 204 and clears the cookie" ($r.Status -eq 204 -and $r.Token -eq "") "status $($r.Status)"

$r = Invoke-Auth "/api/auth/refresh" $D $null
Check "9. Refresh after logout is rejected" ($r.Status -eq 401) "status $($r.Status)"

$r = Invoke-Auth "/api/auth/logout" $null $null
Check "10. Log out with no cookie is harmless" ($r.Status -eq 204) "status $($r.Status)"

$r = Invoke-Auth "/api/auth/refresh" "garbage" $null
Check "11. Refresh with a garbage token is rejected" ($r.Status -eq 401) "status $($r.Status)"

$r = Invoke-Auth "/api/auth/refresh" $null $null
Check "12. Refresh with no cookie is rejected" ($r.Status -eq 401) "status $($r.Status)"

Write-Host ""
if ($script:failures -eq 0) {
    Write-Host "All checks passed" -ForegroundColor Green
} else {
    Write-Host "$($script:failures) check(s) failed" -ForegroundColor Red
}
