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

$sampleCount = 3
if (-not [string]::IsNullOrWhiteSpace($env:LOOKTUBE_GIANTBOMB_PLAYBACK_SAMPLE_COUNT)) {
    $parsedSampleCount = $env:LOOKTUBE_GIANTBOMB_PLAYBACK_SAMPLE_COUNT -as [int]
    if ($null -eq $parsedSampleCount -or $parsedSampleCount -lt 1) {
        throw 'LOOKTUBE_GIANTBOMB_PLAYBACK_SAMPLE_COUNT must be a positive integer when provided.'
    }
    $sampleCount = $parsedSampleCount
}

function New-FeedHeaders {
    $headers = @{
        'User-Agent' = 'LookTube/0.2 playback probe'
        Accept = 'application/rss+xml, application/xml, text/xml'
    }

    if ($hasUsername -and $hasPassword) {
        $pair = '{0}:{1}' -f $env:LOOKTUBE_GIANTBOMB_USERNAME, $env:LOOKTUBE_GIANTBOMB_PASSWORD
        $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
        $headers.Authorization = "Basic $encoded"
    }

    return $headers
}

function Get-PlaybackTarget([System.Xml.XmlElement]$Item) {
    $mediaNode = $Item.SelectSingleNode("*[local-name()='content' and namespace-uri()='http://search.yahoo.com/mrss/']")
    if ($mediaNode -and $mediaNode.Attributes['url']) {
        return [pscustomobject]@{
            Source = 'media:content'
            Url = $mediaNode.Attributes['url'].Value
        }
    }

    $enclosureNode = $Item.SelectSingleNode('enclosure')
    if ($enclosureNode -and $enclosureNode.Attributes['url']) {
        return [pscustomobject]@{
            Source = 'enclosure'
            Url = $enclosureNode.Attributes['url'].Value
        }
    }

    $linkNode = $Item.SelectSingleNode('link')
    if ($linkNode -and -not [string]::IsNullOrWhiteSpace($linkNode.InnerText)) {
        return [pscustomobject]@{
            Source = 'link'
            Url = $linkNode.InnerText.Trim()
        }
    }

    return $null
}

function Invoke-PlaybackProbe([int]$Index, [System.Xml.XmlElement]$Item) {
    $title = $Item.SelectSingleNode('title')?.InnerText?.Trim()
    $target = Get-PlaybackTarget -Item $Item

    if ($null -eq $target -or [string]::IsNullOrWhiteSpace($target.Url)) {
        return [pscustomobject]@{
            Index = $Index
            Title = $title
            Source = $null
            Succeeded = $false
            Host = $null
            PathExtension = $null
            QueryPresent = $false
            StatusCode = $null
            ContentType = $null
            ContentRange = $null
            LooksLikeHlsManifest = $false
            ErrorMessage = 'No playback target was derived from media:content, enclosure, or link.'
        }
    }

    $targetUri = [Uri]$target.Url
    try {
        $response = Invoke-WebRequest `
            -Uri $target.Url `
            -Headers @{
                'User-Agent' = 'LookTube/0.2 playback probe'
                'Range' = 'bytes=0-511'
            }
        $body = [string]$response.Content
        return [pscustomobject]@{
            Index = $Index
            Title = $title
            Source = $target.Source
            Succeeded = $true
            Host = $targetUri.Host
            PathExtension = [IO.Path]::GetExtension($targetUri.AbsolutePath)
            QueryPresent = -not [string]::IsNullOrWhiteSpace($targetUri.Query)
            StatusCode = $response.StatusCode
            ContentType = $response.Headers['Content-Type']
            ContentRange = $response.Headers['Content-Range']
            LooksLikeHlsManifest = $body.Contains('#EXTM3U')
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
            Index = $Index
            Title = $title
            Source = $target.Source
            Succeeded = $false
            Host = $targetUri.Host
            PathExtension = [IO.Path]::GetExtension($targetUri.AbsolutePath)
            QueryPresent = -not [string]::IsNullOrWhiteSpace($targetUri.Query)
            StatusCode = $statusCode
            ContentType = $contentType
            ContentRange = $null
            LooksLikeHlsManifest = $false
            ErrorMessage = $_.Exception.Message
        }
    }
}

$feedResponse = Invoke-WebRequest `
    -Uri $env:LOOKTUBE_GIANTBOMB_FEED_URL `
    -Headers (New-FeedHeaders)
[xml]$feedXml = $feedResponse.Content
$items = @($feedXml.rss.channel.item | Where-Object { $_ } | Select-Object -First $sampleCount)

if ($items.Count -eq 0) {
    throw 'Feed contained no items.'
}

$results = @()
for ($index = 0; $index -lt $items.Count; $index += 1) {
    $results += Invoke-PlaybackProbe -Index ($index + 1) -Item $items[$index]
}

foreach ($result in $results) {
    Write-Host "Playback sample: $($result.Index)"
    Write-Host "  Title: $($result.Title)"
    Write-Host "  Derived playback target: $($result.Source)"
    Write-Host "  Succeeded: $($result.Succeeded)"
    Write-Host "  Host: $($result.Host)"
    Write-Host "  Path extension: $($result.PathExtension)"
    Write-Host "  Query present: $($result.QueryPresent)"
    Write-Host "  Status code: $($result.StatusCode)"
    Write-Host "  Content type: $($result.ContentType)"
    Write-Host "  Content range: $($result.ContentRange)"
    Write-Host "  Looks like HLS manifest: $($result.LooksLikeHlsManifest)"
    if (-not $result.Succeeded) {
        Write-Host "  Error: $($result.ErrorMessage)"
    }
}

$successfulResults = $results | Where-Object { $_.Succeeded }
if ($successfulResults.Count -eq 0) {
    throw 'All attempted playback probes failed.'
}

$uniqueHosts = $successfulResults.Host | Sort-Object -Unique
$uniqueExtensions = $successfulResults.PathExtension | Sort-Object -Unique
Write-Host "Playback summary: $($successfulResults.Count)/$($results.Count) sampled playback targets succeeded."
Write-Host "  Successful hosts: $($uniqueHosts -join ', ')"
Write-Host "  Successful extensions: $($uniqueExtensions -join ', ')"
Write-Host 'Recorded only structural playback metadata; no raw media URLs or payload previews emitted.'
