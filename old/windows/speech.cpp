#include "speech.h"

#include <objbase.h>

namespace {
template <typename T> void release(T*& object) {
    if (object) { object->Release(); object = nullptr; }
}
void clear_speech_event(SPEVENT& event) {
    if (event.elParamType == SPET_LPARAM_IS_OBJECT || event.elParamType == SPET_LPARAM_IS_TOKEN) {
        if (event.lParam) reinterpret_cast<IUnknown*>(event.lParam)->Release();
    } else if (event.elParamType == SPET_LPARAM_IS_POINTER || event.elParamType == SPET_LPARAM_IS_STRING) {
        CoTaskMemFree(reinterpret_cast<void*>(event.lParam));
    }
    event = {};
}
}

SpeechInput::~SpeechInput() { stop(); }

bool SpeechInput::start(HWND callback_window, UINT message, std::wstring& error) {
    if (listening_) return true;
    stop();
    // SAPI uses the recognizer and microphone configured in Windows.
    HRESULT result = CoCreateInstance(CLSID_SpInprocRecognizer, nullptr, CLSCTX_INPROC_SERVER,
                                      IID_ISpRecognizer, reinterpret_cast<void**>(&recognizer_));
    if (SUCCEEDED(result)) result = recognizer_->SetRecognizer(nullptr); // Select Windows' default engine.
    if (SUCCEEDED(result)) result = CoCreateInstance(CLSID_SpMMAudioIn, nullptr, CLSCTX_INPROC_SERVER,
                                                      IID_ISpAudio, reinterpret_cast<void**>(&audio_));
    if (SUCCEEDED(result)) result = recognizer_->SetInput(audio_, TRUE);
    if (SUCCEEDED(result)) result = recognizer_->CreateRecoContext(&context_);
    if (SUCCEEDED(result)) result = context_->SetNotifyWindowMessage(callback_window, message, 0, 0);
    const ULONGLONG interest = SPFEI(SPEI_RECOGNITION) | SPFEI(SPEI_END_SR_STREAM);
    if (SUCCEEDED(result)) result = context_->SetInterest(interest, interest);
    if (SUCCEEDED(result)) result = context_->CreateGrammar(1, &grammar_);
    if (SUCCEEDED(result)) result = grammar_->LoadDictation(nullptr, SPLO_STATIC);
    if (SUCCEEDED(result)) result = grammar_->SetDictationState(SPRS_ACTIVE);
    if (SUCCEEDED(result)) result = recognizer_->SetRecoState(SPRST_ACTIVE);
    if (FAILED(result)) {
        wchar_t code[16]{};
        swprintf(code, sizeof(code) / sizeof(code[0]), L"0x%08lX", static_cast<unsigned long>(result));
        error = L"Mic or Windows speech recognition unavailable (" + std::wstring(code) + L")";
        stop();
        return false;
    }
    listening_ = true;
    error.clear();
    return true;
}

void SpeechInput::stop() {
    listening_ = false;
    if (grammar_) grammar_->SetDictationState(SPRS_INACTIVE);
    if (recognizer_) recognizer_->SetRecoState(SPRST_INACTIVE);
    release(grammar_);
    release(context_);
    release(recognizer_);
    release(audio_);
}

std::wstring SpeechInput::take_result() {
    std::wstring result;
    if (!context_) return result;
    SPEVENT event{};
    ULONG count = 0;
    HRESULT read_status = S_OK;
    bool stream_ended = false;
    while ((read_status = context_->GetEvents(1, &event, &count)) == S_OK && count == 1) {
        if (event.eEventId == SPEI_RECOGNITION && event.lParam) {
            auto* recognition = reinterpret_cast<ISpRecoResult*>(event.lParam);
            wchar_t* text = nullptr;
            if (SUCCEEDED(recognition->GetText(0, SP_GETWHOLEPHRASE, TRUE, &text, nullptr)) && text) {
                result = text;
                CoTaskMemFree(text);
            }
        } else if (event.eEventId == SPEI_END_SR_STREAM) {
            stream_ended = true;
        }
        clear_speech_event(event);
        count = 0;
    }
    // Do not keep showing "Listening..." if an input device or recognizer fails.
    if (stream_ended || FAILED(read_status)) stop();
    return result;
}
