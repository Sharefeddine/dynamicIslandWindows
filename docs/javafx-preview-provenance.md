# Cross-built JavaFX preview provenance

This describes the optional **Island-JavaFX-Windows-x64.zip** supplied separately from the repository. It is an unsigned, Windows-untested preview, not the legacy C++ ZIP in `downloads/`. For a more reproducible distribution, build on Windows from the Maven project using a trusted JDK and dependency sources.

- The application JAR is compiled from `javafx/src/main/` (Java 17 target). `Island.exe` is the small launcher in `javafx/launcher/`; it starts the bundled JavaFX app.
- The included Windows x64 runtime is Temurin 21.0.8+9 from [jdk4py 21.0.8.2](https://pypi.org/project/jdk4py/21.0.8.2/). Its wheel SHA-256 is `1c8e37d26315dfbb603503153d3dcdd90021fda71d64ab0b6309f06f83c0d503`, matching PyPI metadata. Runtime license notices are included under `runtime/legal/`.
- OpenJFX 17.0.2 SDK JARs and notices came from a [community SDK mirror](https://github.com/durritosXD/smart-resume-screening/tree/87611f4b726a1abf5f1aa268bda553b440388ae1/javafx-sdk-17.0.2). Windows DLLs came from [another community SDK mirror](https://github.com/Somzee5/Suspectra/tree/c2861302e17fa16490a7f925cf049758c029ca6e/Suspectra%20v2/javafx-sdk-17.0.2/bin). These SDK files have **not been independently verified against an official vendor release**; do not treat this preview as a production release.
- JNA and JNA Platform 5.14.0 JARs came from the [official JNA repository](https://github.com/java-native-access/jna/tree/5.14.0). JNA and JavaFX license notices are included in `legal/`.
- The SDK's JavaFX Java classes, FXML, and application JAR were exercised on Linux using Linux native libraries and Monocle. That does **not** test the Windows JVM, DLLs, launcher, or Windows integrations.

The preview ZIP contains a `SDK-SHA256SUMS.txt` file listing hashes of the bundled JavaFX and JNA files to make the exact contents identifiable. This is provenance information, not a verification of the original third-party mirrors.
