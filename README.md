# Dynamic Island for Windows

A small, **native Windows desktop app** with a top-centered Dynamic Island and a separate JARVIS core. The island shows Windows battery status and expands into media controls; JARVIS provides a few opt-in, local voice commands.

The Windows app uses Win32, GDI+, and Windows Speech API (SAPI). It does **not** need a browser, Node.js, an Electron runtime, a server, or an account to run.

> **Project status:** Early development. The app has been cross-compiled and its command and placement logic have been tested. The downloadable cross-compiled executable has not yet been runtime-tested on Windows; microphone, battery, and media integration should be checked on your own device.

## Features

- An always-on-top, expandable island at the top of the primary display's work area.
- Real battery percentage from Windows, or `--` when no battery reading is available.
- Previous, play/pause, and next controls that send Windows media keys to the active media player.
- A separate, draggable JARVIS core, with a clear **Listening...** indicator while voice input is active.
- Microphone input that is off by default and starts only when requested.
- A system-tray menu, an optional `Ctrl+Alt+J` shortcut, per-monitor DPI handling, and a single-instance guard.
- A portable executable; no installer or administrator privileges are required.

## Requirements

- 64-bit Windows 10 or Windows 11.
- A media application that responds to Windows media keys, for music controls.
- For voice commands: a microphone, Windows desktop-app microphone permission, and a compatible installed Windows SAPI speech recognizer. The command phrases supported by this app are in English.

The executable is **unsigned**, so Windows may display a reputation warning. It is built from the source in this repository.

## Get and run the app

### Download a build

1. Open [Actions → Windows desktop](https://github.com/Sharefeddine/dynamicIslandWindows/actions/workflows/windows.yml).
2. Choose a **successful run for the branch you want** and download its `Island-Windows-x64` artifact. You may need to sign in to GitHub to download artifacts. Artifacts are available only after a successful run and subject to GitHub's retention settings.
3. Extract the downloaded ZIP and run `Island.exe`.

You can also build the executable yourself using the instructions below. Source-code downloads do not include a built executable.

### Controls

| Action | Control |
| --- | --- |
| Expand or collapse media controls | Click the island |
| Previous, play/pause, next | Click the corresponding button in the expanded island |
| Start or stop listening | Click the JARVIS core, use its tray menu, or press `Ctrl+Alt+J` if the shortcut is available |
| Move JARVIS | Drag the top of its card |
| Hide JARVIS | Click its **×**, or click the tray icon. Hiding it stops listening. |
| Show JARVIS again | Click the tray icon or use the tray menu |
| Exit | Right-click the island or tray icon and select **Exit Island** |

The app stays above ordinary windows. Only one instance can run per user session. If it is already running, use the system-tray icon to find or exit it.

## JARVIS commands

Click the core and wait for **Listening...**, then try a phrase such as:

| Say | Result |
| --- | --- |
| `play music` or `pause music` | Send the Windows play/pause media key |
| `next track` | Send the next-track media key |
| `previous track` | Send the previous-track media key |
| `what time is it` | Show the local time in the JARVIS card |
| `battery` | Show the current Windows battery reading |
| `help` | Show example commands |
| `stop listening` | Turn off the microphone |

JARVIS is a **small local command interface, not a general-purpose AI**. It cannot answer arbitrary questions. Recognition uses the Windows speech engine and default microphone. The app does not itself send audio to a cloud service. Hiding JARVIS or exiting the app stops listening.

Media actions send system keys; they do not launch a specific player, retrieve track information, or confirm playback state. In particular, both “play” and “pause” send the same Windows play/pause **toggle**, so the result depends on the player's current state. The battery reading comes from `GetSystemPowerStatus`; the app does not simulate a charge level.

## Build from source on Windows

Install Visual Studio 2022 or Visual Studio Build Tools with **Desktop development with C++**, the Windows SDK, and CMake. From the repository root in PowerShell:

```powershell
.\windows\build.ps1
```

The script configures an x64 build, builds Release, and runs the native command and layout tests. The output is:

```text
windows\build\Release\Island.exe
```

Alternatively, run the commands yourself:

```powershell
cmake -S windows -B windows/build -A x64
cmake --build windows/build --config Release
ctest --test-dir windows/build -C Release --output-on-failure
```

To run the Windows GUI smoke test manually, first exit any running Island instance, then run:

```powershell
.\tests\native\smoke.ps1 -ExePath .\windows\build\Release\Island.exe
```

The smoke test checks that both native windows appear, that the island expands, and that the app exits. It does not exercise the microphone, real battery hardware, or media playback. The [Windows desktop workflow](.github/workflows/windows.yml) builds and runs these checks on Windows, then uploads a portable artifact when the job succeeds.

For the platform-independent command and layout tests on a machine with a C++17 compiler:

```sh
c++ -std=c++17 -O2 -DNDEBUG tests/native/logic_test.cpp windows/commands.cpp windows/layout.cpp -o island-logic-test
./island-logic-test
```

## Troubleshooting

- **JARVIS says voice is unavailable:** Check Windows microphone permissions, the default microphone, and whether a compatible SAPI recognizer is installed and configured. Click the core to retry. Media controls do not require speech recognition.
- **The battery shows `--`:** Windows could not provide a battery percentage, or the device reports no battery. This is normal on many desktop PCs.
- **Media buttons do nothing or affect the wrong player:** Open a compatible media player and try again. Windows decides which application receives the media keys.
- **The shortcut does nothing:** Another app may already use `Ctrl+Alt+J`. Use the JARVIS core or tray menu instead.
- **A second copy will not open:** Island may already be running. Find its system-tray icon, show JARVIS, or exit the existing instance.

## Repository layout

| Path | Purpose |
| --- | --- |
| `windows/` | Primary native app, resources, CMake project, build script, and inactive CI template |
| `tests/native/` | Portable command/layout tests and a Windows GUI smoke test |
| `downloads/` | Portable Windows ZIP for users who do not want to build from source |
| `src/`, `desktop/`, `public/` | Earlier optional browser/Electron design prototype; not required by the Windows app |

Generated build directories are ignored. The older prototype can be developed separately with Node.js, but it is not the Windows deliverable.
