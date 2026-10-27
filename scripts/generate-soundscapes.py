"""Generate the short, original ambient loops bundled with Island.

Requires numpy and ffmpeg (or the imageio-ffmpeg Python package). Run from the
repository root. The finished MP3s are committed, so this is not a runtime
requirement.
"""
from pathlib import Path
import subprocess
import tempfile
import wave

import numpy as np

try:
    import imageio_ffmpeg
    FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
except ImportError:
    FFMPEG = "ffmpeg"

RATE = 22050
LENGTH = 48
DEST = Path("public/audio")
DEST.mkdir(parents=True, exist_ok=True)

# MIDI pitches. Each piece is a few gentle chords and sparse, bell-like notes.
PIECES = [
    ("drift", [[50, 57, 62, 66], [47, 54, 59, 62], [43, 50, 55, 59], [45, 52, 57, 61]],
     [74, 78, 81, 78, 76, 74, 71, 74, 79, 78, 74, 73, 76, 73, 69, 71], 3),
    ("blue-hour", [[48, 55, 60, 64], [45, 52, 57, 60], [41, 48, 53, 57], [43, 50, 55, 59]],
     [72, 76, 79, 76, 72, 69, 72, 76, 77, 72, 69, 72, 74, 71, 67, 71], 11),
    ("still", [[45, 52, 57, 60], [41, 48, 53, 57], [48, 55, 60, 64], [43, 50, 55, 59]],
     [69, 72, 76, 72, 69, 72, 77, 74, 72, 76, 79, 76, 71, 74, 79, 74], 19),
]


def hz(note):
    return 440.0 * 2 ** ((note - 69) / 12)


def render(name, chords, melody, seed):
    total = int(RATE * LENGTH)
    signal = np.zeros(total, dtype=np.float64)
    rng = np.random.default_rng(seed)

    # Overlapping, lightly detuned sine pads. Each chord blooms and recedes.
    for i, chord in enumerate(chords):
        start = max(0, int((i * 12 - 1.2) * RATE))
        end = min(total, int((i * 12 + 13.2) * RATE))
        t = np.arange(end - start) / RATE
        duration = (end - start) / RATE
        envelope = np.minimum(1, t / 2.8) * np.minimum(1, (duration - t) / 3.0)
        envelope = np.maximum(envelope, 0) ** 0.8
        for j, note in enumerate(chord):
            f = hz(note)
            wobble = 1 + 0.06 * np.sin(2 * np.pi * (0.08 + j * 0.017) * t + j)
            tone = (np.sin(2 * np.pi * f * t) * .60
                    + np.sin(2 * np.pi * f * 1.0025 * t + .7) * .25
                    + np.sin(2 * np.pi * f * 2 * t) * .10)
            signal[start:end] += tone * envelope * wobble * .035

    # Soft electric-piano-like notes, with delayed reflections.
    for i, note in enumerate(melody):
        at = 1.3 + i * 2.82 + (i % 3) * .12
        dur = 5.5
        count = int(RATE * dur)
        t = np.arange(count) / RATE
        f = hz(note)
        env = (1 - np.exp(-t * 22)) * np.exp(-t * 1.1)
        bell = (np.sin(2 * np.pi * f * t)
                + .25 * np.sin(2 * np.pi * f * 2.01 * t) * np.exp(-t * 1.2)
                + .08 * np.sin(2 * np.pi * f * 3.98 * t) * np.exp(-t * 2.8)) * env
        for delay, gain in ((0, .075), (.34, .025), (.69, .012)):
            start = int((at + delay) * RATE)
            end = min(start + count, total)
            if start < total:
                signal[start:end] += bell[:end - start] * gain

    # A barely audible bed of air and clean fades at the loop boundaries.
    noise = rng.normal(0, 1, total)
    smoothed = np.convolve(noise, np.ones(32) / 32, mode="same")
    signal += smoothed * .0016
    fade = np.minimum(1, np.arange(total) / (RATE * 2))
    fade *= np.minimum(1, (total - 1 - np.arange(total)) / (RATE * 2))
    signal *= fade
    signal = np.clip(signal * .8, -1, 1)
    pcm = (signal * 32767).astype('<i2')
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "source.wav"
        with wave.open(str(wav), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(pcm.tobytes())
        subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav),
                        "-codec:a", "libmp3lame", "-b:a", "80k", str(DEST / (name + ".mp3"))], check=True)


for piece in PIECES:
    render(*piece)
    print("Generated", piece[0])
