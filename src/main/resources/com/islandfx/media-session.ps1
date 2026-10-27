# Read the current Windows media session locally. No browser history or audio is read.
$ErrorActionPreference = 'Stop'

function Encode-Field([string]$value) {
    if ([string]::IsNullOrEmpty($value)) { return '' }
    if ($value.Length -gt 1000) { $value = $value.Substring(0, 1000) }
    return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($value))
}

function Send-Frame([string]$frame) {
    [Console]::WriteLine($frame)
    [Console]::Out.Flush()
}

$parent = $null
try {
    $parent = [System.Diagnostics.Process]::GetProcessById([int]$islandParentPid)
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    if (-not $asTask) { throw 'Windows Runtime async support is unavailable.' }

    function Await-Operation($operation, [Type]$resultType) {
        $task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
        if (-not $task.Wait(4000)) { throw 'Windows media request timed out.' }
        return $task.Result
    }

    $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
    $propertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]
    $manager = Await-Operation ($managerType::RequestAsync()) $managerType
    if (-not $manager) { throw 'Windows media session manager is unavailable.' }
    $last = ''
    while (-not $parent.HasExited) {
        try {
            # Prefer Windows' current playing session. If it is paused while another
            # session is playing (for example, YouTube), use that playing session.
            $session = $manager.GetCurrentSession()
            if (-not $session -or $session.GetPlaybackInfo().PlaybackStatus.ToString() -ne 'Playing') {
                foreach ($candidate in $manager.GetSessions()) {
                    if ($candidate.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing') {
                        $session = $candidate
                        break
                    }
                }
            }
            if (-not $session) {
                $frame = 'ISLAND_MEDIA|NONE'
            } else {
                $properties = Await-Operation ($session.TryGetMediaPropertiesAsync()) $propertiesType
                $playback = $session.GetPlaybackInfo().PlaybackStatus.ToString()
                $title = if ($properties) { $properties.Title } else { '' }
                $artist = if ($properties) { $properties.Artist } else { '' }
                $frame = 'ISLAND_MEDIA|SESSION|' + $playback + '|' + (Encode-Field $title) + '|' +
                         (Encode-Field $artist) + '|' + (Encode-Field $session.SourceAppUserModelId)
            }
        } catch {
            # Sessions may disappear while a browser changes videos; retry on the next poll.
            $reason = [string]($_.Exception.GetBaseException().Message)
            if (-not $reason) { $reason = 'Windows media information is unavailable.' }
            $frame = 'ISLAND_MEDIA|UNAVAILABLE|' + (Encode-Field $reason)
        }
        if ($frame -ne $last) { Send-Frame $frame; $last = $frame }
        Start-Sleep -Milliseconds 1500
    }
} catch {
    $reason = [string]($_.Exception.GetBaseException().Message)
    if (-not $reason) { $reason = 'Windows media information is unavailable.' }
    Send-Frame ('ISLAND_MEDIA|UNAVAILABLE|' + (Encode-Field $reason))
} finally {
    if ($parent) { $parent.Dispose() }
}
