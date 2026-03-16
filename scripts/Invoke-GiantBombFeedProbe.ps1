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
function New-ProbeHeaders([bool]$UseBasicAuth) {
    $headers = @{
        'User-Agent' = 'LookTube/0.2 feed probe'
        Accept = 'application/rss+xml, application/xml, text/xml'
    }

    if ($UseBasicAuth) {
        $pair = '{0}:{1}' -f $env:LOOKTUBE_GIANTBOMB_USERNAME, $env:LOOKTUBE_GIANTBOMB_PASSWORD
        $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
        $headers.Authorization = "Basic $encoded"
    }

    return $headers
    Accept = 'application/rss+xml, application/xml, text/xml'
}

function New-ProbeSummary([string]$Content) {
    return [pscustomobject]@{
        ItemCount = ([regex]::Matches($Content, '<item\b')).Count
        MediaContentCount = ([regex]::Matches($Content, '<media:content\b')).Count
        EnclosureCount = ([regex]::Matches($Content, '<enclosure\b')).Count
        ItemLinkCount = ([regex]::Matches($Content, '<item\b[\s\S]*?<link>')).Count
    }
}

function Invoke-ProbeMode([string]$ModeName, [bool]$UseBasicAuth) {
    try {
        $response = Invoke-WebRequest `
            -Uri $env:LOOKTUBE_GIANTBOMB_FEED_URL `
            -Headers (New-ProbeHeaders -UseBasicAuth $UseBasicAuth)

        $summary = New-ProbeSummary -Content $response.Content

        return [pscustomobject]@{
            Mode = $ModeName
            UsedBasicAuth = $UseBasicAuth
            Succeeded = $true
            StatusCode = $response.StatusCode
            ContentType = $response.Headers['Content-Type']
            ItemCount = $summary.ItemCount
            MediaContentCount = $summary.MediaContentCount
            EnclosureCount = $summary.EnclosureCount
            ItemLinkCount = $summary.ItemLinkCount
            ErrorMessage = $null
        }
    } catch {
        $statusCode = $null
        $contentType = $null
        if ($_.Exception.Response) {
            $statusCode = $_.Exception.Response.StatusCode.value__
            $contentType = $_.Exception.Response.Headers['Content-Type']
        }

        return [pscustomobject]@{
            Mode = $ModeName
            UsedBasicAuth = $UseBasicAuth
            Succeeded = $false
            StatusCode = $statusCode
            ContentType = $contentType
            ItemCount = $null
            MediaContentCount = $null
            EnclosureCount = $null
            ItemLinkCount = $null
            ErrorMessage = $_.Exception.Message
        }
    }
}

$probeResults = @()
$probeResults += Invoke-ProbeMode -ModeName 'feed-url-only' -UseBasicAuth $false
if ($hasUsername -and $hasPassword) {
    $probeResults += Invoke-ProbeMode -ModeName 'direct-feed-basic-auth-fallback' -UseBasicAuth $true
}

foreach ($result in $probeResults) {
    Write-Host "Probe mode: $($result.Mode)"
    Write-Host "  Succeeded: $($result.Succeeded)"
    Write-Host "  Used Basic auth fallback: $($result.UsedBasicAuth)"
    Write-Host "  Status code: $($result.StatusCode)"
    Write-Host "  Content type: $($result.ContentType)"
    if ($result.Succeeded) {
        Write-Host "  Item count: $($result.ItemCount)"
        Write-Host "  media:content tags: $($result.MediaContentCount)"
        Write-Host "  enclosure tags: $($result.EnclosureCount)"
        Write-Host "  item links: $($result.ItemLinkCount)"
    } else {
        Write-Host "  Error: $($result.ErrorMessage)"
    }
}

$successfulResults = $probeResults | Where-Object { $_.Succeeded }
if ($successfulResults.Count -eq 0) {
    throw 'All attempted Giant Bomb probe modes failed.'
}

if ($probeResults.Count -eq 2 -and $probeResults[0].Succeeded -and $probeResults[1].Succeeded) {
    $sameShape = (
        $probeResults[0].ItemCount -eq $probeResults[1].ItemCount -and
        $probeResults[0].MediaContentCount -eq $probeResults[1].MediaContentCount -and
        $probeResults[0].EnclosureCount -eq $probeResults[1].EnclosureCount -and
        $probeResults[0].ItemLinkCount -eq $probeResults[1].ItemLinkCount
    )
    Write-Host "Comparison summary: both probe modes succeeded.$(if ($sameShape) { ' Structural counts matched.' } else { ' Structural counts differed.' })"
} elseif ($probeResults.Count -eq 2) {
    Write-Host 'Comparison summary: only one of the attempted probe modes succeeded.'
} else {
    Write-Host 'Comparison summary: only feed-url-only mode was attempted.'
}

Write-Host 'Recorded only structural metadata; no raw authenticated payload preview emitted.'
