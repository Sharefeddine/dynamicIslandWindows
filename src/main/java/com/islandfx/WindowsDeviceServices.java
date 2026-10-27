package com.islandfx;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.WORD;
import com.sun.jna.platform.win32.WinUser.INPUT;
import com.sun.jna.platform.win32.WinUser.KEYBDINPUT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Uses standard Windows APIs through JNA; no track or playback state is inferred. */
public final class WindowsDeviceServices implements DeviceServices {
    private final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    private final PowerShellVoice speech = new PowerShellVoice();

    @Structure.FieldOrder({"acLineStatus", "batteryFlag", "batteryLifePercent", "systemStatusFlag", "batteryLifeTime", "batteryFullLifeTime"})
    public static final class PowerStatus extends Structure {
        public byte acLineStatus;
        public byte batteryFlag;
        public byte batteryLifePercent;
        public byte systemStatusFlag;
        public int batteryLifeTime;
        public int batteryFullLifeTime;
    }

    public interface PowerKernel extends StdCallLibrary {
        boolean GetSystemPowerStatus(PowerStatus status);
    }
    private static final class PowerHolder {
        static final PowerKernel INSTANCE = Native.load("kernel32", PowerKernel.class, W32APIOptions.DEFAULT_OPTIONS);
    }

    @Override public OptionalInt batteryPercent() {
        if (!windows) return OptionalInt.empty();
        PowerStatus status = new PowerStatus();
        if (!PowerHolder.INSTANCE.GetSystemPowerStatus(status)) return OptionalInt.empty();
        return decodeBattery(Byte.toUnsignedInt(status.batteryFlag), Byte.toUnsignedInt(status.batteryLifePercent));
    }

    static OptionalInt decodeBattery(int flags, int percent) {
        return flags == 255 || (flags & 128) != 0 || percent < 0 || percent > 100
                ? OptionalInt.empty() : OptionalInt.of(percent);
    }

    @Override public boolean sendMediaKey(MediaKey key) {
        if (!windows) return false;
        INPUT[] inputs = (INPUT[]) new INPUT().toArray(2);
        for (int i = 0; i < inputs.length; i++) {
            inputs[i].type = new DWORD(INPUT.INPUT_KEYBOARD);
            inputs[i].input.setType(KEYBDINPUT.class);
            inputs[i].input.ki = new KEYBDINPUT();
            inputs[i].input.ki.wVk = new WORD(key.virtualKey);
            inputs[i].input.ki.wScan = new WORD(0);
            inputs[i].input.ki.dwFlags = new DWORD(i == 1 ? KEYBDINPUT.KEYEVENTF_KEYUP : 0);
            inputs[i].input.ki.time = new DWORD(0);
            inputs[i].input.ki.dwExtraInfo = new ULONG_PTR(0);
            inputs[i].write();
        }
        return User32.INSTANCE.SendInput(new DWORD(inputs.length), inputs, inputs[0].size()).intValue() == inputs.length;
    }

    @Override public void startVoice(Consumer<String> phrase, BiConsumer<VoiceEvent, String> state) {
        if (!windows) {
            state.accept(VoiceEvent.ERROR, "Windows speech is unavailable here");
            return;
        }
        speech.start(phrase, state);
    }
    @Override public void stopVoice() { speech.stop(); }
    @Override public void close() { speech.close(); }
}
