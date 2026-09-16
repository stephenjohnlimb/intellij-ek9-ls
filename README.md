# EK9 for IntelliJ (LSP + DAP via LSP4IJ)

An IntelliJ plugin that brings EK9 language features and debugging to JetBrains IDEs, mirroring the
`vscode-ek9-ls` extension — but reusing the *same* compiler entry points:

- **Language features** — the EK9 language server (`ek9 -ls`), via LSP4IJ's LSP client → diagnostics + hover.
- **Debugging** — the EK9 debug adapter (`ek9 -dap`), via LSP4IJ's DAP client → breakpoints in `.ek9`
  files, call stack, locals, stepping. This is the exact adapter that powers VS Code.

**Why LSP4IJ:** IntelliJ is not DAP-native, and its built-in LSP API is Ultimate-only. [LSP4IJ]
(https://github.com/redhat-developer/lsp4ij) is a free, open-source LSP **and** DAP client that works
in **Community Edition** — so both halves run on Community. Syntax colouring already comes from the EK9
TextMate bundle (works in IntelliJ today); the `fileNamePatternMapping` for `*.ek9` deliberately
preserves it.

## Prerequisites

- IntelliJ IDEA **Community** 2024.2+.
- The **LSP4IJ** plugin installed (Settings → Plugins → Marketplace → "LSP4IJ").
- A built EK9 compiler jar (`compiler-cli/target/ek9c-jar-with-dependencies.jar`) and a Java 25.

## Build / run

> **Gradle JVM must be JDK 21, not 25.** Gradle 8.13 (what IntelliJ uses by default) does **not** run
> on JDK 25 — you'll get "JDK 25.0.2 isn't compatible with Gradle 8.13. Please fix JAVA_HOME". Point
> Gradle at JDK 21: IntelliJ → Settings → Build, Execution, Deployment → Build Tools → Gradle →
> **Gradle JVM → 21** (GraalVM 21). This overrides `JAVA_HOME` for Gradle only; your global JDK 25 for
> the EK9 compiler is untouched. (CLI equivalent: run gradle with `JAVA_HOME` pointing at a JDK 21, or
> add `gradle/gradle-daemon-jvm.properties` with `toolchainVersion=21`.) The plugin still targets/runs
> against the chosen IntelliJ SDK regardless.

```bash
cd ~/IdeaProjects/intellij-ek9-ls
gradle wrapper            # once, to generate ./gradlew (or open the project in IntelliJ, which does it)
./gradlew runIde         # launches a sandbox IntelliJ with the plugin loaded
```

In the sandbox IDE, set the EK9 paths (until a Settings UI lands, edit the stored `ek9.xml`, or set
them in `Ek9Settings` defaults): `javaCommand` (e.g. `/usr/bin/java`) and `compilerPath`
(the `ek9c-jar-with-dependencies.jar`). Open a `.ek9` file — LSP4IJ should start `ek9 -ls` and show
diagnostics/hover.

## Status

- ✅ **LSP half** — `Ek9LanguageServerFactory` spawns `ek9 -ls`, mapped to `*.ek9` (colouring preserved). Builds and loads.
- 🔲 **DAP half** — the `<debugAdapterServer>` registration is commented out in `plugin.xml` until
  `Ek9DapDescriptorFactory` is completed against the LSP4IJ 0.20.1 DAP API. **First verify DAP works via
  the manual run configuration below** (no plugin code needed), then wire the factory and re-enable.

## Verify DAP works (no plugin code — this is the key step)

LSP4IJ ships a **"Debug Adapter Protocol" run/debug configuration type**. Use it to drive `ek9 -dap`
directly, proving the whole path in IntelliJ Community before we bake it into the plugin:

1. Build a debuggee with debug info, e.g. `~/IdeaProjects/samples/debug-spike/.ek9/s7demo.jar`
   (`ek9 -Cg s7demo.ek9`).
2. Run → Edit Configurations → **+** → **Debug Adapter Protocol**.
3. **Server / command** — how to start the adapter:
   `java --add-modules jdk.jdi -jar <…>/compiler-cli/target/ek9c-jar-with-dependencies.jar -dap`
   (this is exactly what the VS Code extension spawns).
4. **File mappings** — associate `*.ek9` so breakpoints are allowed in EK9 files.
5. **Launch parameters** (the DAP `launch` request body) — JSON:
   `{ "program": "<…>/samples/debug-spike/.ek9/s7demo.jar" }`
6. Set a breakpoint in `s7demo.ek9` (line 18), run the config. Expect: it stops, shows the EK9 call
   stack + locals, program output in the console — the VS Code experience, in IntelliJ.

(Exact field names are in LSP4IJ's [DAP User Guide](https://github.com/redhat-developer/lsp4ij/blob/main/docs/dap/UserGuide.md).)

If this works, Route A is confirmed and the plugin's job is just to register that configuration so
users don't hand-craft it.

## Completing the DAP factory (Route A)

Once verified, implement `org.ek9lang.intellij.dap.Ek9DapDescriptorFactory` and re-enable the
`<debugAdapterServer>` in `plugin.xml`. Skeleton (verify signatures against LSP4IJ 0.20.1 —
`com.redhat.devtools.lsp4ij.dap.*`):

```kotlin
package org.ek9lang.intellij.dap

// class Ek9DapDescriptorFactory : DebugAdapterDescriptorFactory() {
//   override fun createDebugAdapterDescriptor(options, environment): DebugAdapterDescriptor =
//     Ek9DapDescriptor(options, environment, serverDefinition)
// }
//
// class Ek9DapDescriptor(...) : DebugAdapterDescriptor(...) {
//   override fun startServer(): ProcessHandler {
//     val s = Ek9Settings.getInstance()
//     val cmd = GeneralCommandLine(s.javaCommand, "--add-modules", "jdk.jdi", "-jar", s.compilerPath, "-dap")
//     return OSProcessHandler(cmd)
//   }
//   override fun getFileType(): FileType = /* the EK9 / TextMate file type, for breakpoint association */
//   // getDapParameters(): the launch/attach body (program jar, cwd)
//   // getServerReadyConfig(): the adapter speaks DAP over stdio immediately
// }
```

If LSP4IJ's DAP proves too limiting, the fallback is a native IntelliJ `PositionManager` (the route
Kotlin/Scala/Groovy use) for the debug half; the LSP half stays on LSP4IJ regardless.

## Layout

```
build.gradle.kts / settings.gradle.kts / gradle.properties   IntelliJ Platform Gradle Plugin 2.16 + LSP4IJ 0.20.1
src/main/resources/META-INF/plugin.xml                        depends lsp4ij; server + fileNamePatternMapping (+ debugAdapterServer, commented)
src/main/kotlin/org/ek9lang/intellij/Ek9Settings.kt           java + compiler-jar paths
src/main/kotlin/org/ek9lang/intellij/lsp/Ek9LanguageServerFactory.kt   spawns `ek9 -ls`
```
