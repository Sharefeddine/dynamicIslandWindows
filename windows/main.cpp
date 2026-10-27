#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <windowsx.h>
#include <shellapi.h>
#include <objbase.h>
#include <gdiplus.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <cwchar>
#include <string>

#include "commands.h"
#include "layout.h"
#include "speech.h"

using namespace Gdiplus;

namespace {
constexpr float kIslandWidth = 380.0f;
constexpr float kIslandCollapsedHeight = 64.0f;
constexpr float kIslandExpandedHeight = 212.0f;
constexpr float kCoreWidth = 310.0f;
constexpr float kCoreHeight = 335.0f;
constexpr UINT kTrayMessage = WM_APP + 1;
constexpr UINT kSpeechMessage = WM_APP + 2;
constexpr UINT_PTR kIslandTimer = 1;
constexpr UINT_PTR kCoreTimer = 2;
constexpr int kHotkeyId = 3;
constexpr UINT kTrayId = 1;
constexpr UINT kMenuCore = 100;
constexpr UINT kMenuVoice = 101;
constexpr UINT kMenuMedia = 102;
constexpr UINT kMenuNext = 103;
constexpr UINT kMenuPrevious = 104;
constexpr UINT kMenuExit = 199;

struct AppState {
    HINSTANCE instance = nullptr;
    HWND island = nullptr;
    HWND core = nullptr;
    HICON tray_icon = nullptr;
    UINT taskbar_created = 0;
    ULONG_PTR gdiplus_token = 0;
    bool com_ready = false;
    bool expanded = false;
    bool core_visible = true;
    bool voice_error = false;
    bool tray_added = false;
    bool hotkey_registered = false;
    bool dragging = false;
    POINT drag_cursor{};
    POINT drag_window{};
    double island_scale = 1.0;
    double core_scale = 1.0;
    SYSTEM_POWER_STATUS battery{};
    bool battery_available = false;
    ULONGLONG last_battery_check = 0;
    ULONGLONG message_until = 0;
    std::wstring message = L"Click the core to start listening";
    SpeechInput speech;
};

AppState* g_app = nullptr;

int px(float dip, double scale) { return static_cast<int>(std::lround(dip * scale)); }

void configure_dpi_awareness() {
    HMODULE user32 = GetModuleHandleW(L"user32.dll");
    using SetContext = BOOL (WINAPI*)(HANDLE);
    auto set_context = reinterpret_cast<SetContext>(GetProcAddress(user32, "SetProcessDpiAwarenessContext"));
    // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 is (HANDLE)-4.
    if (!set_context || !set_context(reinterpret_cast<HANDLE>(static_cast<INT_PTR>(-4)))) {
        SetProcessDPIAware();
    }
}

double window_scale(HWND window) {
    HMODULE user32 = GetModuleHandleW(L"user32.dll");
    using GetDpi = UINT (WINAPI*)(HWND);
    auto get_dpi = reinterpret_cast<GetDpi>(GetProcAddress(user32, "GetDpiForWindow"));
    if (get_dpi && window) {
        const UINT dpi = get_dpi(window);
        if (dpi) return static_cast<double>(dpi) / 96.0;
    }
    HDC dc = GetDC(nullptr);
    const int dpi = dc ? GetDeviceCaps(dc, LOGPIXELSX) : 96;
    if (dc) ReleaseDC(nullptr, dc);
    return dpi > 0 ? static_cast<double>(dpi) / 96.0 : 1.0;
}

void rounded_path(GraphicsPath& path, float x, float y, float width, float height, float radius) {
    const float d = radius * 2.0f;
    path.AddArc(x, y, d, d, 180, 90);
    path.AddArc(x + width - d, y, d, d, 270, 90);
    path.AddArc(x + width - d, y + height - d, d, d, 0, 90);
    path.AddArc(x, y + height - d, d, d, 90, 90);
    path.CloseFigure();
}

void fill_round(Graphics& g, float x, float y, float width, float height, float radius, Color color) {
    GraphicsPath path;
    rounded_path(path, x, y, width, height, radius);
    SolidBrush brush(color);
    g.FillPath(&brush, &path);
}

void outline_round(Graphics& g, float x, float y, float width, float height, float radius, Color color, float thickness = 1.0f) {
    GraphicsPath path;
    rounded_path(path, x, y, width, height, radius);
    Pen pen(color, thickness);
    g.DrawPath(&pen, &path);
}

void label(Graphics& g, const std::wstring& text, float x, float y, float width, float height,
           float size, Color color, bool bold = false, bool centered = false) {
    FontFamily family(L"Segoe UI");
    Font font(&family, size, bold ? FontStyleBold : FontStyleRegular, UnitPixel);
    SolidBrush brush(color);
    StringFormat format;
    format.SetAlignment(centered ? StringAlignmentCenter : StringAlignmentNear);
    format.SetLineAlignment(StringAlignmentCenter);
    format.SetTrimming(StringTrimmingEllipsisCharacter);
    format.SetFormatFlags(StringFormatFlagsNoWrap);
    g.DrawString(text.c_str(), static_cast<INT>(text.size()), &font, RectF(x, y, width, height), &format, &brush);
}

void draw_star(Graphics& g, float x, float y, float radius, Color color) {
    const PointF points[] = {
        {x, y - radius}, {x + radius * .23f, y - radius * .23f},
        {x + radius, y}, {x + radius * .23f, y + radius * .23f},
        {x, y + radius}, {x - radius * .23f, y + radius * .23f},
        {x - radius, y}, {x - radius * .23f, y - radius * .23f},
    };
    SolidBrush brush(color);
    g.FillPolygon(&brush, points, 8);
}

void draw_music_note(Graphics& g, float x, float y, Color color) {
    Pen pen(color, 1.7f);
    pen.SetStartCap(LineCapRound);
    pen.SetEndCap(LineCapRound);
    SolidBrush brush(color);
    g.DrawLine(&pen, x + 8, y + 3, x + 8, y + 15);
    g.DrawLine(&pen, x + 8, y + 3, x + 15, y + 1);
    g.DrawLine(&pen, x + 15, y + 1, x + 15, y + 13);
    g.FillEllipse(&brush, x + 3, y + 13, 6.5f, 4.3f);
    g.FillEllipse(&brush, x + 10, y + 11, 6.5f, 4.3f);
}

void refresh_battery() {
    g_app->battery_available = GetSystemPowerStatus(&g_app->battery) != 0 &&
        g_app->battery.BatteryLifePercent != 255 && (g_app->battery.BatteryFlag & 128) == 0;
    g_app->last_battery_check = GetTickCount64();
}

std::wstring battery_text() {
    if (!g_app->battery_available) return L"--";
    return std::to_wstring(g_app->battery.BatteryLifePercent) + L"%";
}

void draw_battery(Graphics& g, float x, float y) {
    const bool low = g_app->battery_available && g_app->battery.BatteryLifePercent <= 20;
    const Color color = low ? Color(255, 232, 177, 156) : Color(255, 166, 220, 203);
    outline_round(g, x, y, 22, 12, 2.5f, Color(190, color.GetR(), color.GetG(), color.GetB()), 1.1f);
    SolidBrush terminal(Color(160, color.GetR(), color.GetG(), color.GetB()));
    g.FillRectangle(&terminal, x + 23, y + 3.7f, 2.0f, 4.5f);
    if (g_app->battery_available) {
        const float width = std::max(0.0f, std::min(1.0f, g_app->battery.BatteryLifePercent / 100.0f)) * 16.0f;
        if (width > 0) fill_round(g, x + 3, y + 3, width, 6, 1.0f, color);
    }
}

void draw_chevron(Graphics& g, float x, float y, bool up) {
    Pen pen(Color(180, 169, 193, 202), 1.35f);
    pen.SetStartCap(LineCapRound);
    pen.SetEndCap(LineCapRound);
    if (up) {
        g.DrawLine(&pen, x, y + 4, x + 4, y);
        g.DrawLine(&pen, x + 4, y, x + 8, y + 4);
    } else {
        g.DrawLine(&pen, x, y, x + 4, y + 4);
        g.DrawLine(&pen, x + 4, y + 4, x + 8, y);
    }
}

void draw_transport_icon(Graphics& g, float x, float y, int direction) {
    SolidBrush brush(Color(255, 205, 228, 234));
    if (direction < 0) {
        PointF triangle[] = {{x + 11, y}, {x + 2, y + 6}, {x + 11, y + 12}};
        g.FillPolygon(&brush, triangle, 3);
        g.FillRectangle(&brush, x, y, 2.0f, 12.0f);
    } else {
        PointF triangle[] = {{x, y}, {x + 9, y + 6}, {x, y + 12}};
        g.FillPolygon(&brush, triangle, 3);
        g.FillRectangle(&brush, x + 10, y, 2.0f, 12.0f);
    }
}

void draw_island(Graphics& g) {
    const float height = g_app->expanded ? kIslandExpandedHeight : kIslandCollapsedHeight;
    // A mostly opaque native overlay, with per-pixel transparent corners.
    fill_round(g, 2, 3, 376, height - 7, g_app->expanded ? 25.0f : 29.0f, Color(80, 0, 0, 0));
    if (g_app->expanded) {
        fill_round(g, 4, 16, 372, height - 22, 24, Color(248, 8, 19, 27));
        outline_round(g, 4.5f, 16.5f, 371, height - 23, 23, Color(90, 158, 202, 215));
    }
    fill_round(g, 4, 4, 372, 56, 28, Color(252, 4, 11, 16));
    outline_round(g, 4.5f, 4.5f, 371, 55, 27.5f, Color(110, 154, 198, 211));

    SolidBrush icon_bg(Color(255, 25, 51, 63));
    g.FillEllipse(&icon_bg, 14, 13, 38, 38);
    Pen icon_border(Color(110, 165, 219, 234), 1);
    g.DrawEllipse(&icon_border, 14.5f, 13.5f, 37.0f, 37.0f);
    draw_music_note(g, 24, 23, Color(255, 190, 238, 245));
    label(g, L"Music", 64, 12, 100, 21, 13, Color(255, 235, 247, 249), true);
    label(g, L"WINDOWS MEDIA", 64, 34, 120, 13, 8.5f, Color(255, 133, 158, 168));

    Pen wave(Color(220, 151, 221, 230), 1.7f);
    wave.SetStartCap(LineCapRound);
    wave.SetEndCap(LineCapRound);
    const float heights[] = {6, 12, 17, 11, 6};
    for (int i = 0; i < 5; ++i) g.DrawLine(&wave, 207.0f + i * 4.0f, 32 - heights[i] / 2, 207.0f + i * 4.0f, 32 + heights[i] / 2);
    Pen divider(Color(90, 150, 181, 191), 1);
    g.DrawLine(&divider, 236, 20, 236, 44);
    draw_battery(g, 249, 26);
    label(g, battery_text(), 280, 19, 52, 25, 12, Color(255, 226, 242, 244), true);
    draw_chevron(g, 350, 30, g_app->expanded);

    if (!g_app->expanded) return;
    label(g, L"WINDOWS MEDIA", 24, 75, 180, 14, 9, Color(255, 144, 179, 191), true);
    label(g, L"Control your current media player", 24, 91, 315, 17, 11, Color(255, 176, 197, 204));
    Pen line(Color(65, 170, 202, 211), 1);
    g.DrawLine(&line, 24, 116, 356, 116);
    draw_transport_icon(g, 121, 145, -1);
    SolidBrush play_bg(Color(255, 204, 237, 242));
    g.FillEllipse(&play_bg, 170, 128, 40, 40);
    SolidBrush dark(Color(255, 15, 39, 47));
    PointF triangle[] = {{181, 140}, {181, 156}, {191, 148}};
    g.FillPolygon(&dark, triangle, 3);
    g.FillRectangle(&dark, 195.0f, 140.0f, 2.4f, 16.0f);
    g.FillRectangle(&dark, 200.0f, 140.0f, 2.4f, 16.0f);
    draw_transport_icon(g, 246, 145, 1);
    label(g, L"PREVIOUS", 89, 174, 78, 13, 8, Color(255, 126, 152, 162), false, true);
    label(g, L"PLAY / PAUSE", 150, 174, 80, 13, 8, Color(255, 142, 170, 179), false, true);
    label(g, L"NEXT", 222, 174, 63, 13, 8, Color(255, 126, 152, 162), false, true);
    label(g, L"Media keys are sent to Windows", 24, 193, 260, 12, 8.5f, Color(255, 117, 145, 155));
}

void draw_core(Graphics& g) {
    // Soft halo behind the glass card.
    fill_round(g, 2, 3, 306, 274, 24, Color(75, 0, 0, 0));
    GraphicsPath card;
    rounded_path(card, 4, 4, 302, 270, 23);
    LinearGradientBrush card_brush(RectF(4, 4, 302, 270), Color(246, 24, 46, 60), Color(247, 7, 22, 32), LinearGradientModeVertical);
    g.FillPath(&card_brush, &card);
    Pen border(Color(110, 164, 212, 225), 1);
    g.DrawPath(&border, &card);
    draw_star(g, 23, 22, 5.0f, Color(255, 189, 235, 243));
    label(g, L"AI CORE", 36, 13, 90, 19, 9.0f, Color(255, 161, 193, 202), true);
    SolidBrush active_dot(g_app->speech.is_listening() ? Color(255, 167, 236, 219) : Color(230, 150, 194, 183));
    g.FillEllipse(&active_dot, 228, 20, 5, 5);
    label(g, g_app->speech.is_listening() ? L"ACTIVE" : L"STANDBY", 238, 13, 54, 18, 8.0f, Color(255, 150, 183, 184));
    Pen close_pen(Color(180, 171, 199, 206), 1.3f);
    g.DrawLine(&close_pen, 289, 18, 296, 25);
    g.DrawLine(&close_pen, 296, 18, 289, 25);

    const float cx = 155.0f;
    const float cy = 133.0f;
    // Multiple faint circles create a restrained glow without an image asset.
    for (int i = 5; i > 0; --i) {
        const float radius = 54.0f + i * 7.0f;
        SolidBrush halo(Color(static_cast<BYTE>(3 + (6 - i) * 2), 92, 191, 216));
        g.FillEllipse(&halo, cx - radius, cy - radius, radius * 2, radius * 2);
    }
    Pen outer(Color(50, 161, 216, 231), 1);
    g.DrawEllipse(&outer, cx - 88, cy - 88, 176.0f, 176.0f);
    Pen dots(Color(70, 178, 226, 237), 1);
    dots.SetDashStyle(DashStyleDot);
    g.DrawEllipse(&dots, cx - 78, cy - 78, 156.0f, 156.0f);
    Pen ring(Color(110, 172, 221, 234), 1);
    g.DrawEllipse(&ring, cx - 68, cy - 68, 136.0f, 136.0f);
    const float angle = static_cast<float>((GetTickCount64() / 35) % 360);
    Pen arc(Color(g_app->speech.is_listening() ? 220 : 130, 185, 239, 249), 1.4f);
    g.DrawArc(&arc, cx - 68, cy - 68, 136.0f, 136.0f, angle, 35.0f);
    g.DrawArc(&arc, cx - 68, cy - 68, 136.0f, 136.0f, angle + 160, 24.0f);
    GraphicsPath orb;
    orb.AddEllipse(cx - 55, cy - 55, 110.0f, 110.0f);
    PathGradientBrush orb_brush(&orb);
    orb_brush.SetCenterPoint(PointF(cx - 18, cy - 24));
    orb_brush.SetCenterColor(Color(145, 152, 224, 241));
    Color edge(220, 8, 37, 53);
    INT edge_count = 1;
    orb_brush.SetSurroundColors(&edge, &edge_count);
    g.FillPath(&orb_brush, &orb);
    Pen orb_line(Color(100, 170, 226, 238), 1);
    g.DrawEllipse(&orb_line, cx - 55, cy - 55, 110.0f, 110.0f);
    Pen inner(Color(65, 185, 229, 238), 1);
    g.DrawEllipse(&inner, cx - 40, cy - 40, 80.0f, 80.0f);
    g.DrawEllipse(&inner, cx - 25, cy - 25, 50.0f, 50.0f);
    for (int i = 4; i > 0; --i) draw_star(g, cx, cy, 19.0f + i * 2, Color(static_cast<BYTE>(5 * (5 - i)), 164, 238, 250));
    draw_star(g, cx, cy, 19, Color(255, 222, 251, 255));
    Pen orbit_dot(Color(180, 178, 229, 241), 1);
    g.DrawEllipse(&orbit_dot, cx - 83, cy - 3, 6.0f, 6.0f);
    g.DrawEllipse(&orbit_dot, cx + 77, cy - 3, 6.0f, 6.0f);
    SolidBrush mini(Color(220, 196, 244, 249));
    g.FillEllipse(&mini, cx - 81, cy - 1, 2.0f, 2.0f);
    g.FillEllipse(&mini, cx + 79, cy - 1, 2.0f, 2.0f);

    label(g, L"J A R V I S", 54, 224, 202, 21, 15, Color(255, 235, 250, 252), true, true);
    label(g, L"YOUR PERSONAL ASSISTANT", 55, 247, 200, 12, 8, Color(255, 139, 174, 185), false, true);

    SolidBrush dot_bg(Color(70, 109, 183, 192));
    g.FillEllipse(&dot_bg, 8, 291, 30, 30);
    Pen dot_ring(Color(95, 155, 220, 225), 1);
    g.DrawEllipse(&dot_ring, 8.5f, 291.5f, 29.0f, 29.0f);
    SolidBrush dot(Color(255, 175, 231, 218));
    g.FillEllipse(&dot, 20, 303, 6, 6);
    const std::wstring state = g_app->speech.is_listening() ? L"Listening..." : (g_app->voice_error ? L"Voice unavailable" : L"Ready to listen");
    label(g, state, 47, 288, 247, 18, 12.5f, Color(255, 229, 246, 248), true);
    label(g, g_app->message, 47, 308, 250, 17, 9.5f, Color(255, 155, 183, 192));
}

void present(HWND window, float logical_width, float logical_height, double scale, void (*draw)(Graphics&)) {
    if (!window) return;
    const int width = std::max(1, px(logical_width, scale));
    const int height = std::max(1, px(logical_height, scale));
    Bitmap bitmap(width, height, PixelFormat32bppPARGB);
    if (bitmap.GetLastStatus() != Ok) return;
    {
        Graphics graphics(&bitmap);
        graphics.SetSmoothingMode(SmoothingModeAntiAlias);
        graphics.SetPixelOffsetMode(PixelOffsetModeHighQuality);
        graphics.SetTextRenderingHint(TextRenderingHintAntiAliasGridFit);
        graphics.Clear(Color(0, 0, 0, 0));
        graphics.ScaleTransform(static_cast<REAL>(scale), static_cast<REAL>(scale));
        draw(graphics);
    }
    HDC screen = GetDC(nullptr);
    if (!screen) return;
    HDC memory = CreateCompatibleDC(screen);
    if (!memory) { ReleaseDC(nullptr, screen); return; }
    BITMAPINFO info{};
    info.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    info.bmiHeader.biWidth = width;
    info.bmiHeader.biHeight = -height;
    info.bmiHeader.biPlanes = 1;
    info.bmiHeader.biBitCount = 32;
    info.bmiHeader.biCompression = BI_RGB;
    void* pixels = nullptr;
    HBITMAP dib = CreateDIBSection(screen, &info, DIB_RGB_COLORS, &pixels, nullptr, 0);
    if (!dib || !pixels) {
        if (dib) DeleteObject(dib);
        DeleteDC(memory);
        ReleaseDC(nullptr, screen);
        return;
    }
    BitmapData data{};
    Rect rectangle(0, 0, width, height);
    if (bitmap.LockBits(&rectangle, ImageLockModeRead, PixelFormat32bppPARGB, &data) == Ok) {
        for (int row = 0; row < height; ++row) {
            const auto* source = static_cast<const BYTE*>(data.Scan0) + static_cast<std::ptrdiff_t>(row) * data.Stride;
            auto* destination = static_cast<BYTE*>(pixels) + static_cast<std::size_t>(row) * width * 4;
            std::memcpy(destination, source, static_cast<std::size_t>(width) * 4);
        }
        bitmap.UnlockBits(&data);
        HGDIOBJ old = SelectObject(memory, dib);
        RECT position{};
        GetWindowRect(window, &position);
        POINT destination{position.left, position.top};
        POINT source{0, 0};
        SIZE size{width, height};
        BLENDFUNCTION blend{AC_SRC_OVER, 0, 255, AC_SRC_ALPHA};
        UpdateLayeredWindow(window, screen, &destination, &size, memory, &source, 0, &blend, ULW_ALPHA);
        SelectObject(memory, old);
    }
    DeleteObject(dib);
    DeleteDC(memory);
    ReleaseDC(nullptr, screen);
}

void render_island() {
    present(g_app->island, kIslandWidth, g_app->expanded ? kIslandExpandedHeight : kIslandCollapsedHeight,
            g_app->island_scale, draw_island);
}
void render_core() {
    if (g_app->core_visible) present(g_app->core, kCoreWidth, kCoreHeight, g_app->core_scale, draw_core);
}

void position_windows() {
    RECT area{};
    SystemParametersInfoW(SPI_GETWORKAREA, 0, &area, 0);
    g_app->island_scale = window_scale(g_app->island);
    g_app->core_scale = window_scale(g_app->core);
    const int island_width = px(kIslandWidth, g_app->island_scale);
    const int core_width = px(kCoreWidth, g_app->core_scale);
    const auto layout = island::position_overlays({area.left, area.top, area.right, area.bottom}, island_width,
                                                   core_width, px(8, g_app->island_scale),
                                                   px(14, g_app->island_scale), px(84, g_app->island_scale));
    SetWindowPos(g_app->island, HWND_TOPMOST, layout.island.x, layout.island.y, island_width,
                 px(g_app->expanded ? kIslandExpandedHeight : kIslandCollapsedHeight, g_app->island_scale), SWP_NOACTIVATE);
    SetWindowPos(g_app->core, HWND_TOPMOST, layout.core.x, layout.core.y, core_width,
                 px(kCoreHeight, g_app->core_scale), SWP_NOACTIVATE);
    render_island();
    render_core();
}

void set_message(const std::wstring& text, bool temporary = true) {
    g_app->message = text;
    g_app->message_until = temporary ? GetTickCount64() + 7000 : 0;
    render_core();
}

bool send_media_key(WORD key) {
    INPUT input[2]{};
    input[0].type = INPUT_KEYBOARD;
    input[0].ki.wVk = key;
    input[1].type = INPUT_KEYBOARD;
    input[1].ki.wVk = key;
    input[1].ki.dwFlags = KEYEVENTF_KEYUP;
    return SendInput(2, input, sizeof(INPUT)) == 2;
}

void media_command(WORD key, const wchar_t* description) {
    if (send_media_key(key)) set_message(description);
    else set_message(L"Windows could not send the media key");
}

void toggle_listening() {
    if (g_app->speech.is_listening()) {
        g_app->speech.stop();
        g_app->voice_error = false;
        set_message(L"Microphone off", true);
        return;
    }
    if (!g_app->com_ready) {
        g_app->voice_error = true;
        set_message(L"Windows speech could not initialize", false);
        return;
    }
    std::wstring error;
    if (g_app->speech.start(g_app->core, kSpeechMessage, error)) {
        g_app->voice_error = false;
        set_message(L"Say play music, time, or battery", false);
    } else {
        g_app->voice_error = true;
        set_message(error, false);
    }
}

void handle_speech() {
    const bool was_listening = g_app->speech.is_listening();
    const std::wstring phrase = g_app->speech.take_result();
    if (was_listening && !g_app->speech.is_listening()) {
        g_app->voice_error = true;
        set_message(L"Speech input stopped. Click to retry.", false);
        return;
    }
    if (phrase.empty()) return;
    switch (island::classify_command(phrase)) {
        case island::Intent::ToggleMusic: media_command(VK_MEDIA_PLAY_PAUSE, L"Play / pause sent to Windows"); break;
        case island::Intent::NextTrack: media_command(VK_MEDIA_NEXT_TRACK, L"Next track sent to Windows"); break;
        case island::Intent::PreviousTrack: media_command(VK_MEDIA_PREV_TRACK, L"Previous track sent to Windows"); break;
        case island::Intent::Time: {
            wchar_t time[80]{};
            GetTimeFormatEx(LOCALE_NAME_USER_DEFAULT, TIME_NOSECONDS, nullptr, nullptr, time, 80);
            set_message(std::wstring(L"It’s ") + time);
            break;
        }
        case island::Intent::Battery:
            refresh_battery();
            render_island();
            set_message(g_app->battery_available ? std::wstring(L"Battery: ") + battery_text() : L"No battery reading available");
            break;
        case island::Intent::Help: set_message(L"Try music, next track, time, or battery"); break;
        case island::Intent::StopListening:
            g_app->speech.stop();
            g_app->voice_error = false;
            set_message(L"Microphone off");
            break;
        default: set_message(L"Try music, next track, time, or battery"); break;
    }
}

void toggle_core() {
    if (g_app->core_visible) {
        g_app->speech.stop();
        g_app->voice_error = false;
        g_app->message = L"Click the core to start listening";
        g_app->message_until = 0;
        g_app->core_visible = false;
        ShowWindow(g_app->core, SW_HIDE);
    } else {
        g_app->core_visible = true;
        render_core();
        SetWindowPos(g_app->core, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_SHOWWINDOW);
    }
}

HICON make_tray_icon() {
    Bitmap bitmap(32, 32, PixelFormat32bppPARGB);
    {
        Graphics g(&bitmap);
        g.SetSmoothingMode(SmoothingModeAntiAlias);
        g.Clear(Color(0, 0, 0, 0));
        SolidBrush bg(Color(255, 12, 33, 46));
        g.FillEllipse(&bg, 1, 1, 30, 30);
        Pen ring(Color(220, 161, 225, 237), 1.3f);
        g.DrawEllipse(&ring, 2, 2, 28, 28);
        Pen inner(Color(100, 171, 226, 237), 1);
        g.DrawEllipse(&inner, 7, 7, 18, 18);
        draw_star(g, 16, 16, 8, Color(255, 222, 251, 255));
    }
    HBITMAP color = nullptr;
    if (bitmap.GetHBITMAP(Color(0, 0, 0, 0), &color) != Ok || !color) return nullptr;
    BYTE mask_bits[128]{};
    HBITMAP mask = CreateBitmap(32, 32, 1, 1, mask_bits);
    if (!mask) { DeleteObject(color); return nullptr; }
    ICONINFO info{};
    info.fIcon = TRUE;
    info.hbmColor = color;
    info.hbmMask = mask;
    HICON icon = CreateIconIndirect(&info);
    DeleteObject(color);
    DeleteObject(mask);
    return icon;
}

void add_tray() {
    NOTIFYICONDATAW data{};
    data.cbSize = sizeof(data);
    data.hWnd = g_app->island;
    data.uID = kTrayId;
    data.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
    data.uCallbackMessage = kTrayMessage;
    data.hIcon = g_app->tray_icon ? g_app->tray_icon : LoadIconW(nullptr, IDI_APPLICATION);
    lstrcpynW(data.szTip, L"Island for Windows — right-click for options", static_cast<int>(sizeof(data.szTip) / sizeof(data.szTip[0])));
    g_app->tray_added = Shell_NotifyIconW(NIM_ADD, &data) != FALSE;
}

void remove_tray() {
    if (!g_app->tray_added) return;
    NOTIFYICONDATAW data{};
    data.cbSize = sizeof(data);
    data.hWnd = g_app->island;
    data.uID = kTrayId;
    Shell_NotifyIconW(NIM_DELETE, &data);
    g_app->tray_added = false;
}

void show_menu() {
    HMENU menu = CreatePopupMenu();
    if (!menu) return;
    AppendMenuW(menu, MF_STRING, kMenuCore, g_app->core_visible ? L"Hide JARVIS" : L"Show JARVIS");
    AppendMenuW(menu, MF_STRING, kMenuVoice, g_app->speech.is_listening() ? L"Stop listening" : L"Start listening");
    AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(menu, MF_STRING, kMenuMedia, L"Play / pause media");
    AppendMenuW(menu, MF_STRING, kMenuPrevious, L"Previous track");
    AppendMenuW(menu, MF_STRING, kMenuNext, L"Next track");
    AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(menu, MF_STRING, kMenuExit, L"Exit Island");
    POINT cursor{};
    GetCursorPos(&cursor);
    SetForegroundWindow(g_app->island);
    const UINT selected = TrackPopupMenu(menu, TPM_RETURNCMD | TPM_RIGHTBUTTON | TPM_NONOTIFY,
                                         cursor.x, cursor.y, 0, g_app->island, nullptr);
    DestroyMenu(menu);
    PostMessageW(g_app->island, WM_NULL, 0, 0);
    if (selected) SendMessageW(g_app->island, WM_COMMAND, selected, 0);
}

void handle_menu(UINT item) {
    switch (item) {
        case kMenuCore: toggle_core(); break;
        case kMenuVoice:
            if (!g_app->core_visible) toggle_core();
            toggle_listening();
            break;
        case kMenuMedia: media_command(VK_MEDIA_PLAY_PAUSE, L"Play / pause sent to Windows"); break;
        case kMenuPrevious: media_command(VK_MEDIA_PREV_TRACK, L"Previous track sent to Windows"); break;
        case kMenuNext: media_command(VK_MEDIA_NEXT_TRACK, L"Next track sent to Windows"); break;
        case kMenuExit: PostMessageW(g_app->island, WM_CLOSE, 0, 0); break;
    }
}

LRESULT CALLBACK island_proc(HWND window, UINT message, WPARAM wparam, LPARAM lparam) {
    if (!g_app) return DefWindowProcW(window, message, wparam, lparam);
    if (message == g_app->taskbar_created && message != 0) { add_tray(); return 0; }
    switch (message) {
        case WM_MOUSEACTIVATE: return MA_NOACTIVATE;
        case WM_SETCURSOR: SetCursor(LoadCursorW(nullptr, IDC_HAND)); return TRUE;
        case WM_LBUTTONUP: {
            const float x = static_cast<float>(GET_X_LPARAM(lparam) / g_app->island_scale);
            const float y = static_cast<float>(GET_Y_LPARAM(lparam) / g_app->island_scale);
            if (y < 64) {
                g_app->expanded = !g_app->expanded;
                SetWindowPos(window, HWND_TOPMOST, 0, 0, px(kIslandWidth, g_app->island_scale),
                             px(g_app->expanded ? kIslandExpandedHeight : kIslandCollapsedHeight, g_app->island_scale),
                             SWP_NOMOVE | SWP_NOACTIVATE);
                render_island();
            } else if (g_app->expanded && y >= 125 && y <= 173) {
                if (x >= 98 && x <= 154) media_command(VK_MEDIA_PREV_TRACK, L"Previous track sent to Windows");
                else if (x >= 164 && x <= 216) media_command(VK_MEDIA_PLAY_PAUSE, L"Play / pause sent to Windows");
                else if (x >= 232 && x <= 282) media_command(VK_MEDIA_NEXT_TRACK, L"Next track sent to Windows");
            }
            return 0;
        }
        case WM_RBUTTONUP: show_menu(); return 0;
        case kTrayMessage:
            if (lparam == WM_LBUTTONUP || lparam == WM_LBUTTONDBLCLK) toggle_core();
            else if (lparam == WM_RBUTTONUP || lparam == WM_CONTEXTMENU) show_menu();
            return 0;
        case WM_COMMAND: handle_menu(LOWORD(wparam)); return 0;
        case WM_HOTKEY:
            if (wparam == kHotkeyId) { if (!g_app->core_visible) toggle_core(); toggle_listening(); }
            return 0;
        case WM_TIMER:
            if (wparam == kIslandTimer && GetTickCount64() - g_app->last_battery_check >= 15000) { refresh_battery(); render_island(); }
            return 0;
        case WM_POWERBROADCAST: refresh_battery(); render_island(); return TRUE;
        case WM_DISPLAYCHANGE: position_windows(); return 0;
        case WM_DPICHANGED:
            g_app->island_scale = HIWORD(wparam) / 96.0;
            position_windows();
            return 0;
        case WM_CLOSE: DestroyWindow(window); return 0;
        case WM_DESTROY:
            g_app->speech.stop();
            remove_tray();
            if (g_app->hotkey_registered) UnregisterHotKey(window, kHotkeyId);
            if (g_app->core) { DestroyWindow(g_app->core); g_app->core = nullptr; }
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProcW(window, message, wparam, lparam);
}

LRESULT CALLBACK core_proc(HWND window, UINT message, WPARAM wparam, LPARAM lparam) {
    if (!g_app) return DefWindowProcW(window, message, wparam, lparam);
    switch (message) {
        case WM_MOUSEACTIVATE: return MA_NOACTIVATE;
        case WM_SETCURSOR: SetCursor(LoadCursorW(nullptr, IDC_HAND)); return TRUE;
        case WM_LBUTTONDOWN: {
            const float x = static_cast<float>(GET_X_LPARAM(lparam) / g_app->core_scale);
            const float y = static_cast<float>(GET_Y_LPARAM(lparam) / g_app->core_scale);
            if (y < 40 && x < 275) {
                g_app->dragging = true;
                GetCursorPos(&g_app->drag_cursor);
                RECT rect{}; GetWindowRect(window, &rect);
                g_app->drag_window = {rect.left, rect.top};
                SetCapture(window);
            }
            return 0;
        }
        case WM_MOUSEMOVE:
            if (g_app->dragging && (wparam & MK_LBUTTON)) {
                POINT cursor{}; GetCursorPos(&cursor);
                SetWindowPos(window, HWND_TOPMOST, g_app->drag_window.x + cursor.x - g_app->drag_cursor.x,
                             g_app->drag_window.y + cursor.y - g_app->drag_cursor.y, 0, 0,
                             SWP_NOSIZE | SWP_NOACTIVATE);
            }
            return 0;
        case WM_LBUTTONUP: {
            if (g_app->dragging) { g_app->dragging = false; ReleaseCapture(); return 0; }
            const float x = static_cast<float>(GET_X_LPARAM(lparam) / g_app->core_scale);
            const float y = static_cast<float>(GET_Y_LPARAM(lparam) / g_app->core_scale);
            if (x >= 280 && y < 38) toggle_core();
            else if ((y >= 40 && y <= 220) || (y >= 285 && y <= 330)) toggle_listening();
            return 0;
        }
        case WM_CAPTURECHANGED: g_app->dragging = false; return 0;
        case WM_RBUTTONUP: show_menu(); return 0;
        case kSpeechMessage: handle_speech(); return 0;
        case WM_TIMER:
            if (wparam == kCoreTimer) {
                if (g_app->message_until && GetTickCount64() >= g_app->message_until) {
                    g_app->message_until = 0;
                    g_app->message = g_app->speech.is_listening() ? L"Say play music, time, or battery" : L"Click the core to start listening";
                }
                render_core();
            }
            return 0;
        case WM_DPICHANGED: {
            g_app->core_scale = HIWORD(wparam) / 96.0;
            const RECT* suggested = reinterpret_cast<const RECT*>(lparam);
            SetWindowPos(window, HWND_TOPMOST, suggested->left, suggested->top, px(kCoreWidth, g_app->core_scale),
                         px(kCoreHeight, g_app->core_scale), SWP_NOACTIVATE);
            render_core();
            return 0;
        }
        case WM_CLOSE: toggle_core(); return 0;
    }
    return DefWindowProcW(window, message, wparam, lparam);
}

bool register_class(HINSTANCE instance, const wchar_t* name, WNDPROC procedure) {
    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.hInstance = instance;
    wc.lpfnWndProc = procedure;
    wc.lpszClassName = name;
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.hIcon = LoadIconW(instance, MAKEINTRESOURCEW(1));
    wc.hIconSm = wc.hIcon;
    return RegisterClassExW(&wc) != 0;
}
} // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int) {
    HANDLE mutex = CreateMutexW(nullptr, TRUE, L"Local\\DynamicIslandWindows.Native.Singleton");
    if (!mutex) return 1;
    if (GetLastError() == ERROR_ALREADY_EXISTS) {
        MessageBoxW(nullptr, L"Island is already running. Use its system-tray icon to open JARVIS or exit.", L"Island for Windows", MB_OK | MB_ICONINFORMATION);
        CloseHandle(mutex);
        return 0;
    }
    configure_dpi_awareness();
    AppState state;
    state.instance = instance;
    g_app = &state;
    const HRESULT com = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    state.com_ready = SUCCEEDED(com);
    GdiplusStartupInput startup;
    if (GdiplusStartup(&state.gdiplus_token, &startup, nullptr) != Ok) {
        MessageBoxW(nullptr, L"Could not initialize Windows graphics.", L"Island for Windows", MB_OK | MB_ICONERROR);
        if (state.com_ready) CoUninitialize();
        CloseHandle(mutex);
        return 1;
    }
    if (!register_class(instance, L"IslandNativeWindow", island_proc) || !register_class(instance, L"JarvisNativeWindow", core_proc)) {
        MessageBoxW(nullptr, L"Could not create the Island windows.", L"Island for Windows", MB_OK | MB_ICONERROR);
        GdiplusShutdown(state.gdiplus_token);
        if (state.com_ready) CoUninitialize();
        CloseHandle(mutex);
        return 1;
    }
    constexpr DWORD extended = WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_TOPMOST | WS_EX_NOACTIVATE;
    state.island = CreateWindowExW(extended, L"IslandNativeWindow", L"Island for Windows", WS_POPUP,
                                   0, 0, 380, 64, nullptr, nullptr, instance, nullptr);
    state.core = CreateWindowExW(extended, L"JarvisNativeWindow", L"JARVIS", WS_POPUP,
                                 0, 0, 310, 335, nullptr, nullptr, instance, nullptr);
    if (!state.island || !state.core) {
        MessageBoxW(nullptr, L"Could not create the Island windows.", L"Island for Windows", MB_OK | MB_ICONERROR);
        if (state.island) DestroyWindow(state.island);
        else if (state.core) DestroyWindow(state.core);
        GdiplusShutdown(state.gdiplus_token);
        if (state.com_ready) CoUninitialize();
        CloseHandle(mutex);
        return 1;
    }
    state.taskbar_created = RegisterWindowMessageW(L"TaskbarCreated");
    state.tray_icon = make_tray_icon();
    refresh_battery();
    position_windows();
    ShowWindow(state.island, SW_SHOWNOACTIVATE);
    ShowWindow(state.core, SW_SHOWNOACTIVATE);
    render_island();
    render_core();
    add_tray();
    SetTimer(state.island, kIslandTimer, 5000, nullptr);
    SetTimer(state.core, kCoreTimer, 200, nullptr);
    state.hotkey_registered = RegisterHotKey(state.island, kHotkeyId, MOD_CONTROL | MOD_ALT | MOD_NOREPEAT, 'J') != FALSE;

    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    state.speech.stop();
    if (state.tray_icon) DestroyIcon(state.tray_icon);
    GdiplusShutdown(state.gdiplus_token);
    if (state.com_ready) CoUninitialize();
    CloseHandle(mutex);
    g_app = nullptr;
    return static_cast<int>(message.wParam);
}
