$ErrorActionPreference = 'Stop'

$requiredVariables = @(
    'LOOKTUBE_GIANTBOMB_FEED_URL',
    'LOOKTUBE_GIANTBOMB_USERNAME',
    'LOOKTUBE_GIANTBOMB_PASSWORD'
)

$missing = $requiredVariables | Where-Object { -not $env:$_ }
if ($missing.Count -gt 0) {
    throw "Missing required environment variables: $($missing -join ', ')"
}

$pair = '{0}:{1}' -f $env:LOOKTUBE_GIANTBOMB_USERNAME, $env:LOOKTUBE_GIANTBOMB_PASSWORD
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))

$response = Invoke-WebRequest `
    -Uri $env:LOOKTUBE_GIANTBOMB_FEED_URL `
    -Headers @{
        Authorization = "Basic $encoded"
        'User-Agent' = 'LookTube/0.1 feed probe'
        Accept = 'application/rss+xml, application/xml, text/xml'
    }

$contentPreview = ($response.Content -split '\r?\n' | Select-Object -First 20) -join [Environment]::NewLine
$itemCount = ([regex]::Matches($response.Content, '<item\b')).Count

Write-Host "Feed probe succeeded."
Write-Host "Status code: $($response.StatusCode)"
Write-Host "Content type: $($response.Headers['Content-Type'])"
Write-Host "Item count: $itemCount"
Write-Host "Preview:"
Write-Host $contentPreview
