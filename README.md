# Island for Windows — JavaFX & FXML

A Windows desktop companion with a top-centered Dynamic Island and a separate JARVIS core. The **primary implementation is JavaFX**: its layouts are written in **FXML**, its behavior is Java, and its styling is JavaFX CSS. It is not a website and does not require Node.js or Electron to run.

> **Status:** The JavaFX views, command logic, and simulated interactions have been tested in a headless Linux environment. The Windows build and its battery, media, tray, hotkey, and microphone integration still need testing on Windows. The Windows preview build is unsigned.

## What it looks like

| Compact island | Expanded media controls |
| --- | --- |
| ![Compact JavaFX island and JARVIS](docs/previews/island-compact.png) | ![Expanded JavaFX island and listening core](docs/previews/island-expanded.png) |

These images are rendered from the actual FXML views on a simulated desktop. **72% and “Listening...” are sample preview states**; they are not readings from a real device or microphone.

## Features

- A transparent, always-on-top island centered in the primary display's work area.
- Windows battery percentage via `GetSystemPowerStatus`; `--` means no reading is available.
- Expandable previous, play/pause, and next buttons that send Windows media keys to the active media player.
- A separate draggable JARVIS card, which displays **Listening...** when voice input is active.
- An opt-in microphone, system-tray menu, optional `Ctrl+Alt+J` hotkey, and single-instance guard.
- JavaFX and FXML UI, with Java Native Access (JNA) used for small Windows API calls.

## Requirements

- 64-bit Windows 10 or 11.
- A compatible media application to respond to Windows media keys.
- For voice: a microphone, Windows desktop-app microphone permission, and a compatible installed Windows speech recognizer. The supported command phrases are in English.
- To **build from source**: JDK 17 or newer, Maven 3.8 or newer, and network access to Maven Central. A packaged app image includes its own Java runtime.

## Run or build on Windows

A cross-built JavaFX preview ZIP may be supplied separately with this project. Extract its entire folder and run `Island.exe`; keep the `app`, `javafx`, and `runtime` folders next to it. It has not yet been tested on Windows and is unsigned, so Windows may display a reputation warning. Its JavaFX SDK files came from community mirrors and have not been independently verified; see [preview provenance](docs/javafx-preview-provenance.md). For a more reproducible package, build from source on Windows using trusted dependencies.

To run directly from source, open PowerShell in the repository root:

```powershell
mvn -f javafx/pom.xml clean verify
mvn -f javafx/pom.xml javafx:run
```

To create a portable Windows app image with `Island.exe` and its own Java runtime, use a full JDK with `jpackage`:

```powershell
.\javafx\build-windows.ps1 -Package
```

The result is `javafx\target\package\Island\Island.exe`. Keep the whole `Island` folder together when moving it. This packaging script has not yet been run on Windows. `mvn clean verify` runs the JUnit logic tests; the JavaFX UI smoke harnesses in `javafx/src/test/` were also run headlessly in the development environment.

**Important:** `downloads/Island-Windows-x64.zip` is the earlier C++/Win32 build, not this JavaFX implementation. It is retained only as a legacy reference; do not use it to evaluate the JavaFX version.

## Controls

| Action | Control |
| --- | --- |
| Expand or collapse media controls | Click the top island |
| Previous, play/pause, next | Click the corresponding control in the expanded island |
| Start or stop listening | Click the JARVIS orb or mic button, use the tray menu, or press `Ctrl+Alt+J` if available |
| Move JARVIS | Drag its header |
| Hide JARVIS | Click its **×** or the tray icon. Hiding it stops listening. |
| Show JARVIS | Use the tray icon or right-click the island |
| Exit | Right-click the island or tray icon and select **Exit Island** |

## Voice commands and privacy

The microphone is **off by default**. On request, the Java app starts a local Windows PowerShell worker using `System.Speech`; the worker exits when listening stops, JARVIS is hidden, or the app exits. The app does not itself send audio to a cloud service.

| Example phrase | Action |
| --- | --- |
| `play music` or `pause music` | Send the play/pause media key |
| `next track` / `previous track` | Send the corresponding media key |
| `what time is it` | Show local time |
| `battery` | Show the Windows battery reading |
| `help` | Show command examples |
| `stop listening` | Stop microphone input |

JARVIS is a **small local command interface, not general-purpose AI**. The media controls do not retrieve track information or verify playback state. Both “play” and “pause” send the same Windows play/pause **toggle**.

## Troubleshooting

- **Voice is unavailable:** Check the default microphone, Windows microphone permissions, and the installed Windows speech recognizer. Click the core to retry.
- **Battery shows `--`:** Windows could not provide a reading, or the device reports no battery.
- **Media controls do nothing:** Open a player that responds to Windows media keys; Windows chooses which app receives them.
- **Shortcut does nothing:** Another application may have registered `Ctrl+Alt+J`; use the core or tray menu.
- **The app does not launch:** Check the output from `mvn javafx:run`, or use `Run-Island-Debug.cmd` if it is included in your preview ZIP.

## Repository layout

| Path | Purpose |
| --- | --- |
| `javafx/src/main/resources/com/islandfx/*.fxml` | Island and JARVIS FXML views |
| `javafx/src/main/resources/com/islandfx/island.css` | JavaFX styling |
| `javafx/src/main/java/com/islandfx/` | Controllers, app lifecycle, command logic, and Windows integration |
| `javafx/src/test/` | JUnit logic tests and UI/preview harnesses |
| `javafx/build-windows.ps1` | Maven build and optional `jpackage` app-image script |
| `javafx/launcher/` | Small optional native launcher for a bundled Java runtime; the interface remains JavaFX/FXML |
| `docs/previews/` | JavaFX-rendered preview images with simulated values |
| `windows/`, `downloads/` | Earlier C++/Win32 implementation and its legacy executable |
| `src/`, `desktop/`, `public/` | Earlier optional browser/Electron prototype, not the Windows deliverable |
