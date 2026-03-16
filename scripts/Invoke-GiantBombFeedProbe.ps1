$ErrorActionPreference = 'Stop'

$requiredVariables = @(
    'LOOKTUBE_GIANTBOMB_FEED_URL'
)

$missingRequired = $requiredVariables | Where-Object { -not [Environment]::GetEnvironmentVariable($_) }
if ($missingRequired.Count -gt 0) {
    throw "Missing required environment variables: $($missingRequired -join ', ')"
}
function New-ProbeHeaders {
    $headers = @{
        'User-Agent' = 'LookTube/0.2 feed probe'
        Accept = 'application/rss+xml, application/xml, text/xml'
    }

    return $headers
}

function New-ProbeSummary([string]$Content) {
    return [pscustomobject]@{
        ItemCount = ([regex]::Matches($Content, '<item\b')).Count
        MediaContentCount = ([regex]::Matches($Content, '<media:content\b')).Count
        EnclosureCount = ([regex]::Matches($Content, '<enclosure\b')).Count
        ItemLinkCount = ([regex]::Matches($Content, '<item\b[\s\S]*?<link>')).Count
    }
}

function Invoke-Probe {
    try {
        $response = Invoke-WebRequest `
            -Uri $env:LOOKTUBE_GIANTBOMB_FEED_URL `
            -Headers (New-ProbeHeaders)

        $summary = New-ProbeSummary -Content $response.Content

        return [pscustomobject]@{
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

$result = Invoke-Probe
Write-Host "Probe mode: feed-url-only"
Write-Host "  Succeeded: $($result.Succeeded)"
Write-Host "  Status code: $($result.StatusCode)"
Write-Host "  Content type: $($result.ContentType)"
if ($result.Succeeded) {
    Write-Host "  Item count: $($result.ItemCount)"
    Write-Host "  media:content tags: $($result.MediaContentCount)"
    Write-Host "  enclosure tags: $($result.EnclosureCount)"
    Write-Host "  item links: $($result.ItemLinkCount)"
} else {
    Write-Host "  Error: $($result.ErrorMessage)"
    throw 'The Giant Bomb feed probe failed.'
}
Write-Host 'Summary: copied feed URL probe succeeded.'

Write-Host 'Recorded only structural metadata; no raw authenticated payload preview emitted.'
