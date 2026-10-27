param(
    [Parameter(Mandatory = $true)]
    [string] $ExePath
)
$ErrorActionPreference = 'Stop'

# Checks actual Win32 window creation and island expansion on a Windows desktop.
# It does not activate the microphone or send media keys.
Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;

public static class IslandSmoke {
    private delegate bool EnumProc(IntPtr hwnd, IntPtr data);
    [StructLayout(LayoutKind.Sequential)] private struct Rect { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumProc callback, IntPtr data);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] private static extern int GetClassName(IntPtr hwnd, StringBuilder name, int length);
    [DllImport("user32.dll")] private static extern bool GetWindowRect(IntPtr hwnd, out Rect rect);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hwnd);
    [DllImport("user32.dll")] private static extern bool PostMessage(IntPtr hwnd, uint msg, IntPtr wp, IntPtr lp);

    public static IntPtr Find(uint processId, string className) {
        IntPtr found = IntPtr.Zero;
        EnumWindows(delegate(IntPtr hwnd, IntPtr unused) {
            uint owner;
            GetWindowThreadProcessId(hwnd, out owner);
            if (owner == processId && IsWindowVisible(hwnd)) {
                StringBuilder name = new StringBuilder(128);
                GetClassName(hwnd, name, name.Capacity);
                if (name.ToString() == className) { found = hwnd; return false; }
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }
    public static int Height(IntPtr hwnd) {
        Rect rect;
        if (!GetWindowRect(hwnd, out rect)) return 0;
        return rect.Bottom - rect.Top;
    }
    public static void ClickIsland(IntPtr hwnd) {
        PostMessage(hwnd, 0x0202, IntPtr.Zero, new IntPtr((20 << 16) | 20));
    }
    public static void Close(IntPtr hwnd) {
        PostMessage(hwnd, 0x0010, IntPtr.Zero, IntPtr.Zero);
    }
}
'@

$exe = (Resolve-Path $ExePath).Path
$process = Start-Process -FilePath $exe -PassThru
try {
    $island = [IntPtr]::Zero
    $core = [IntPtr]::Zero
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Milliseconds 250
        $process.Refresh()
        if ($process.HasExited) { throw "Island exited early with code $($process.ExitCode)." }
        $island = [IslandSmoke]::Find([uint32]$process.Id, 'IslandNativeWindow')
        $core = [IslandSmoke]::Find([uint32]$process.Id, 'JarvisNativeWindow')
        if ($island -ne [IntPtr]::Zero -and $core -ne [IntPtr]::Zero) { break }
    }
    if ($island -eq [IntPtr]::Zero -or $core -eq [IntPtr]::Zero) { throw 'Both native overlay windows did not appear.' }
    $collapsed = [IslandSmoke]::Height($island)
    [IslandSmoke]::ClickIsland($island)
    Start-Sleep -Milliseconds 500
    if ([IslandSmoke]::Height($island) -le $collapsed) { throw 'The island did not expand.' }
    [IslandSmoke]::Close($island)
    if (-not $process.WaitForExit(5000)) { throw 'Island did not exit after WM_CLOSE.' }
    if ($process.ExitCode -ne 0) { throw "Island exited with code $($process.ExitCode)." }
    Write-Host 'Native windows appeared, the island expanded, and the app exited cleanly.'
} finally {
    $process.Refresh()
    if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
}
