#include "commands.h"

#include <cwctype>
#include <string>

namespace island {
namespace {
bool has_word(const std::wstring& text, const std::wstring& word) {
    std::size_t position = 0;
    while ((position = text.find(word, position)) != std::wstring::npos) {
        const bool left = position == 0 || !std::iswalnum(text[position - 1]);
        const std::size_t end = position + word.size();
        const bool right = end == text.size() || !std::iswalnum(text[end]);
        if (left && right) return true;
        position = end;
    }
    return false;
}
}

Intent classify_command(const std::wstring& phrase) {
    std::wstring text;
    text.reserve(phrase.size());
    for (wchar_t character : phrase) text.push_back(static_cast<wchar_t>(std::towlower(character)));

    const auto word = [&text](const wchar_t* value) { return has_word(text, value); };
    const bool sound = word(L"music") || word(L"song") || word(L"track") || word(L"audio") || word(L"media");
    if ((word(L"stop") || word(L"cancel")) && (word(L"listening") || word(L"microphone"))) return Intent::StopListening;
    if ((word(L"next") || word(L"skip")) && (sound || text == L"next" || text == L"skip")) return Intent::NextTrack;
    if ((word(L"previous") || word(L"back")) && (sound || text == L"previous" || text == L"back")) return Intent::PreviousTrack;
    if ((word(L"play") || word(L"pause") || word(L"resume")) && (sound || text == L"play" || text == L"pause" || text == L"resume")) return Intent::ToggleMusic;
    if (word(L"time") || word(L"clock")) return Intent::Time;
    if (word(L"battery") || word(L"charge")) return Intent::Battery;
    if (word(L"help") || text.find(L"what can you do") != std::wstring::npos) return Intent::Help;
    return Intent::Unknown;
}
}
