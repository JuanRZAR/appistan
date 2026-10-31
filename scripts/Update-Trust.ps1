$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$catalogPath = Join-Path $taskRoot 'app/src/main/assets/catalog.json'
$trustPath = Join-Path $taskRoot 'app/src/main/assets/trust.json'
$catalog = @(Get-Content -Raw -LiteralPath $catalogPath | ConvertFrom-Json)
$catalog += [PSCustomObject]@{ id='com.google.android.gms'; domain='www.google.com' }
$domains = @($catalog.domain | Sort-Object -Unique)
$domains += @('www.google.com','www.facebook.com','www.youtube.com','g.co','www.office.com','www.microsoft.com','acrobat.adobe.com','discord.com','t.me')
$pins = @{}
if (Test-Path -LiteralPath $trustPath) {
    $existing = Get-Content -Raw -LiteralPath $trustPath | ConvertFrom-Json
    foreach ($property in $existing.PSObject.Properties) { $pins[$property.Name] = $property.Value }
}
foreach ($domain in ($domains | Sort-Object -Unique)) {
    $url = 'https://' + $domain + '/.well-known/assetlinks.json'
    try {
        $links = Invoke-RestMethod -Uri $url -TimeoutSec 12
        foreach ($link in $links) {
            $id = $link.target.package_name
            if ($link.target.namespace -ne 'android_app' -or $id -notin $catalog.id) { continue }
            $fingerprints = @($link.target.sha256_cert_fingerprints | ForEach-Object { $_.Replace(':','').ToUpperInvariant() } | Where-Object { $_ -match '^[0-9A-F]{64}$' })
            if ($fingerprints.Count -eq 0) { continue }
            $previous = $pins[$id]
            $pins[$id] = [ordered]@{
                sha256 = @(@($previous.sha256) + $fingerprints | Where-Object { $_ } | Sort-Object -Unique)
                sources = @(@($previous.sources) + $url | Where-Object { $_ } | Sort-Object -Unique)
                checkedUtc = [DateTime]::UtcNow.ToString('o')
            }
        }
    } catch { Write-Output ('Sin assetlinks: ' + $domain) }
}
$json = $pins | ConvertTo-Json -Depth 6
[IO.File]::WriteAllText($trustPath, $json, [Text.UTF8Encoding]::new($false))
Write-Output ('Firmas corroboradas para ' + $pins.Count + ' paquetes')
