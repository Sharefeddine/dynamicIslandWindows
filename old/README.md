# Legacy implementations

This folder preserves the earlier experiments. They are **not** the current JavaFX/FXML application; open the repository root in IntelliJ IDEA for that project.

- `windows/` and `tests/native/`: the old C++/Win32 app and its tests.
- `downloads/Island-Windows-x64.zip`: the old C++ build, not JavaFX. It is unsigned and has not been tested on Windows.
- `src/`, `desktop/`, `public/`, `scripts/`, and the Node/Vite files: the old browser/Electron prototype.

To test or run the browser prototype, work inside this folder:

```sh
npm ci
npm test
npm run build
```

To build the old C++ project on Windows, use CMake with the Visual Studio C++ tools and Windows SDK:

```powershell
.\windows\build.ps1
```

The example CI configuration in `windows/ci/` is a template, not an active GitHub Actions workflow.
