# JavaFX preview provenance

## Images in this repository

`docs/previews/` contains images of the real FXML views rendered by `src/test/java/com/islandfx/PreviewRenderer.java` on a synthetic desktop. Media, 72% battery, files, tasks, commands and microphone state are deliberately **simulated**. The focus, water, shelf, tasks and music images are crops of the rendered desktop; the compact and expanded images also show the separate JARVIS view. `AnimationPreviewRenderer` captures the same FXML opening, changing pages and closing; its PNG frames were assembled into `island-motion.gif` with ImageMagick. Frame timing is approximate.

The images were rendered on Linux with JavaFX 17.0.2 and a temporarily patched [TestFX Monocle](https://github.com/TestFX/Monocle) `jdk-17` headless backend. The sandbox used Linux JavaFX native libraries from a community Maven cache and Pango libraries from a [Chromium sysroot mirror](https://github.com/hanpfei/chromium-net/tree/master/build/linux/debian_wheezy_amd64-sysroot/usr/lib/x86_64-linux-gnu), neither independently verified against an official release. These temporary testing dependencies are **not bundled in the application**. The desktop and fonts are not Windows, and the renders do not verify Windows APIs, animations on a real desktop, media sessions, speech or notifications.

## Earlier cross-built ZIP

An earlier optional `Island-JavaFX-Windows-x64.zip` was supplied separately from the repository in a previous iteration. It predates the current Nusa-inspired sections and is not a current release; it was unsigned and never tested on Windows. For the current version, build from source on Windows with trusted JDK and Maven dependencies as described in the root README.

For that historical ZIP, the Windows x64 runtime was Temurin 21.0.8+9 from [jdk4py 21.0.8.2](https://pypi.org/project/jdk4py/21.0.8.2/) (wheel SHA-256 `1c8e37d26315dfbb603503153d3dcdd90021fda71d64ab0b6309f06f83c0d503`). Its OpenJFX 17.0.2 SDK JARs and notices came from a [community mirror](https://github.com/durritosXD/smart-resume-screening/tree/87611f4b726a1abf5f1aa268bda553b440388ae1/javafx-sdk-17.0.2), and Windows DLLs from [another mirror](https://github.com/Somzee5/Suspectra/tree/c2861302e17fa16490a7f925cf049758c029ca6e/Suspectra%20v2/javafx-sdk-17.0.2/bin). Those SDK files were not independently verified. JNA and JNA Platform 5.14.0 came from the [official JNA repository](https://github.com/java-native-access/jna/tree/5.14.0).
