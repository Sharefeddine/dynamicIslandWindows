// Minimal optional Windows launcher for a bundled JavaFX runtime. The actual
// windows, layout, and app logic are implemented in Java and FXML.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <string>
#include <vector>

int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR, int) {
    std::vector<wchar_t> path(32768);
    const DWORD length = GetModuleFileNameW(nullptr, path.data(), static_cast<DWORD>(path.size()));
    if (!length || length >= path.size()) {
        MessageBoxW(nullptr, L"Could not locate the Island application folder.", L"Island for Windows", MB_OK | MB_ICONERROR);
        return 1;
    }
    const std::wstring file(path.data(), length);
    const std::size_t slash = file.find_last_of(L"\\/");
    if (slash == std::wstring::npos) return 1;
    const std::wstring root = file.substr(0, slash + 1);
    const std::wstring java = root + L"runtime\\bin\\javaw.exe";
    if (GetFileAttributesW(java.c_str()) == INVALID_FILE_ATTRIBUTES) {
        MessageBoxW(nullptr, L"The bundled Java runtime is missing. Extract the complete ZIP before starting Island.",
                    L"Island for Windows", MB_OK | MB_ICONERROR);
        return 1;
    }
    std::wstring command = L"\"" + java + L"\" --module-path \"" + root + L"javafx\""
            + L" --add-modules javafx.controls,javafx.fxml -Djava.library.path=\"" + root + L"javafx\""
            + L" -cp \"" + root + L"app\\*\" com.islandfx.Launcher";
    STARTUPINFOW startup{};
    startup.cb = sizeof(startup);
    PROCESS_INFORMATION process{};
    if (!CreateProcessW(java.c_str(), command.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW,
                        nullptr, root.c_str(), &startup, &process)) {
        MessageBoxW(nullptr, L"Could not start the bundled Java runtime. Try Run-Island-Debug.cmd for details.",
                    L"Island for Windows", MB_OK | MB_ICONERROR);
        return 1;
    }
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return 0;
}
