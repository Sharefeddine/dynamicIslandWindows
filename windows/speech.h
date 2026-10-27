#pragma once

#include <string>
#include <windows.h>
#include <sapi.h>

// Uses the installed Windows SAPI recognizer; no browser or network service is
// required by this application. Access to the microphone is opt-in.
class SpeechInput {
public:
    SpeechInput() = default;
    SpeechInput(const SpeechInput&) = delete;
    SpeechInput& operator=(const SpeechInput&) = delete;
    ~SpeechInput();

    bool start(HWND callback_window, UINT message, std::wstring& error);
    void stop();
    bool is_listening() const { return listening_; }
    // Call from callback_window's message handler. Empty if no final result;
    // stops listening when the audio stream or event source unexpectedly ends.
    std::wstring take_result();

private:
    ISpRecognizer* recognizer_ = nullptr;
    ISpAudio* audio_ = nullptr;
    ISpRecoContext* context_ = nullptr;
    ISpRecoGrammar* grammar_ = nullptr;
    bool listening_ = false;
};
