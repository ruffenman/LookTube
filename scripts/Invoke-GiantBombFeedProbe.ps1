$ErrorActionPreference = 'Stop'

$requiredVariables = @(
    'LOOKTUBE_GIANTBOMB_FEED_URL'
)

$missingRequired = $requiredVariables | Where-Object { -not [Environment]::GetEnvironmentVariable($_) }
if ($missingRequired.Count -gt 0) {
    throw "Missing required environment variables: $($missingRequired -join ', ')"
}

$hasUsername = -not [string]::IsNullOrWhiteSpace($env:LOOKTUBE_GIANTBOMB_USERNAME)
$hasPassword = -not [string]::IsNullOrWhiteSpace($env:LOOKTUBE_GIANTBOMB_PASSWORD)
if ($hasUsername -xor $hasPassword) {
    throw 'Provide both LOOKTUBE_GIANTBOMB_USERNAME and LOOKTUBE_GIANTBOMB_PASSWORD together, or omit both to probe a copied feed URL directly.'
}

$headers = @{
    'User-Agent' = 'LookTube/0.2 feed probe'
    Accept = 'application/rss+xml, application/xml, text/xml'
}

if ($hasUsername -and $hasPassword) {
    $pair = '{0}:{1}' -f $env:LOOKTUBE_GIANTBOMB_USERNAME, $env:LOOKTUBE_GIANTBOMB_PASSWORD
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
    $headers.Authorization = "Basic $encoded"
}

$response = Invoke-WebRequest `
    -Uri $env:LOOKTUBE_GIANTBOMB_FEED_URL `
    -Headers $headers

$itemCount = ([regex]::Matches($response.Content, '<item\b')).Count
$mediaContentCount = ([regex]::Matches($response.Content, '<media:content\b')).Count
$enclosureCount = ([regex]::Matches($response.Content, '<enclosure\b')).Count
$itemLinkCount = ([regex]::Matches($response.Content, '<item\b[\s\S]*?<link>')).Count

Write-Host "Feed probe succeeded."
Write-Host "Status code: $($response.StatusCode)"
Write-Host "Content type: $($response.Headers['Content-Type'])"
Write-Host "Used Basic auth fallback: $($hasUsername -and $hasPassword)"
Write-Host "Item count: $itemCount"
Write-Host "media:content tags: $mediaContentCount"
Write-Host "enclosure tags: $enclosureCount"
Write-Host "item links: $itemLinkCount"
Write-Host "Recorded only structural metadata; no raw authenticated payload preview emitted."
