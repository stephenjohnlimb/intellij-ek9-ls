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

> **Gradle 9.x is required** (pinned to 9.1.0 in `gradle/wrapper/gradle-wrapper.properties`). The
> IntelliJ Platform Gradle Plugin 2.x needs Gradle **9.0+**, so IntelliJ's default `8.13` fails with
> "Cannot use connection to Gradle distribution gradle-8.13". This is the wrapper's job — NOT an
> IntelliJ-version problem, so **you do not need to update IntelliJ**. On first sync make sure
> Settings → Build, Execution, Deployment → Build Tools → Gradle → **Use Gradle from: 'gradle-wrapper.properties'**.
>
> **Build JVM must be JDK 21.** Although Gradle 9.1 *runs* on JDK 25, the **Kotlin compiler** bundled
> with the Kotlin Gradle plugin does not — it throws `IllegalArgumentException: 25.0.2` in
> `JavaVersion.parse`. So set Settings → … → Gradle → **Gradle JVM → 21** (GraalVM 21). That runs
> Gradle and the Kotlin daemon on JDK 21; your global JDK 25 (for the EK9 compiler) is untouched. The
> plugin still targets the chosen IntelliJ SDK regardless of the build JVM.

```bash
cd ~/IdeaProjects/intellij-ek9-ls
./gradlew buildPlugin     # produces build/distributions/intellij-ek9-ls-<version>.zip (install-from-disk)
./gradlew runIde          # launches a sandbox IntelliJ with the plugin loaded
```

The Gradle wrapper is committed, so `./gradlew` works on a fresh clone (it downloads Gradle 9.1.0 on
first run). Remember the build JVM must be JDK 21 — if your default `java` is 25, pass it explicitly,
e.g. `./gradlew -Dorg.gradle.java.home="$(/usr/libexec/java_home -v 21)" buildPlugin`.

In the sandbox IDE, set the EK9 paths (until a Settings UI lands, edit the stored `ek9.xml`, or set
them in `Ek9Settings` defaults): `javaCommand` (e.g. `/usr/bin/java`) and `compilerPath`
(the `ek9c-jar-with-dependencies.jar`). Open a `.ek9` file — LSP4IJ should start `ek9 -ls` and show
diagnostics/hover.

## Status

- ✅ **Settings** — `Ek9Configurable` (Settings → Tools → **EK9**): Java command, EK9 compiler jar,
  "use native" toggle, backed by `Ek9Settings`. Both `-ls` and `-dap` read these.
- ✅ **LSP half** — `Ek9LanguageServerFactory` spawns `ek9 -ls`, mapped to `*.ek9` (colouring preserved). Builds and loads.
- ✅ **DAP — Route A proven (2026-09-16)** — `ek9 -dap` driven by LSP4IJ's **built-in "Debug Adapter
  Protocol" run configuration** works in IntelliJ **Community**, with *zero plugin code*: breakpoint in
  `s7demo.ek9` binds and hits, execution stops, the call stack shows the EK9 frame (`_main:17,
  s7demo.ek9`) and Locals shows types + values (`base = {Integer} 21`, `label = "answer"`,
  `doubled = {Integer} 121`). This is the VS Code experience, in IntelliJ Community. The two things that
  gate it: the **Mappings** tab must declare `.ek9` debuggable (File type `ek9`, or File-name-pattern
  `*.ek9`), and the breakpoint must be in the source that compiled into the **launched** jar.
- ✅ **DAP half (plugin)** — `Ek9DapDescriptorFactory` + `Ek9DapDescriptor` are implemented against the
  LSP4IJ 0.20.1 DAP API and the `<debugAdapterServer name="EK9">` registration is live in `plugin.xml`.
  Compiles and assembles (`buildPlugin` green). Now a user picks **EK9** as the server in a "Debug
  Adapter Protocol" run configuration and points at the program — the command line
  (`java --add-modules jdk.jdi -jar … -dap`), the DAP `launch` body (`{"program": "${file}"}`) and the
  `.ek9` breakpoint association (via `isDebuggableFile`) are all supplied by the descriptor, so none of
  the three manual steps below are needed. Verify in a sandbox with `runIde` (see *Using the plugin's
  EK9 server*).

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

This confirmed Route A (2026-09-16). The plugin now registers that configuration so users don't
hand-craft it — see below.

## Using the plugin's EK9 server (Route A, implemented)

`Ek9DapDescriptorFactory` (`createDebugAdapterDescriptor` + `isDebuggableFile`) and `Ek9DapDescriptor`
(`startServer` builds `ek9 -dap` from `Ek9Settings`; `getDapParameters` supplies `{"program": "${file}"}`;
`isDebuggableFile`/`getFileType` associate `.ek9`) are implemented against LSP4IJ 0.20.1
(`com.redhat.devtools.lsp4ij.dap.*`) and registered via `<debugAdapterServer name="EK9">` in plugin.xml.

To use it in a sandbox IDE:

1. `./gradlew runIde` (build JVM = JDK 21, see the build note above). Set the EK9 paths in `Ek9Settings`.
2. Run → Edit Configurations → **+** → **Debug Adapter Protocol**.
3. **Server** tab — select the registered **EK9** server (no command to type: `startServer()` supplies it).
4. **Configuration** tab — set the program to the compiled jar, e.g.
   `~/IdeaProjects/samples/debug-spike/.ek9/s7demo.jar`. (No Mappings tab and no launch JSON needed —
   the descriptor supplies both.)
5. Set a breakpoint in `s7demo.ek9` (line 18) and Debug. Expect the stop, the EK9 call stack and typed
   locals — exactly as the manual config, now one-click.

If LSP4IJ's DAP ever proves too limiting, the fallback is a native IntelliJ `PositionManager` (the
route Kotlin/Scala/Groovy use) for the debug half; the LSP half stays on LSP4IJ regardless.

## Layout

```
build.gradle.kts / settings.gradle.kts / gradle.properties   IntelliJ Platform Gradle Plugin 2.16 + LSP4IJ 0.20.1
src/main/resources/META-INF/plugin.xml                        depends lsp4ij; server + fileNamePatternMapping + debugAdapterServer
src/main/kotlin/org/ek9lang/intellij/Ek9Settings.kt           java + compiler-jar paths
src/main/kotlin/org/ek9lang/intellij/lsp/Ek9LanguageServerFactory.kt   spawns `ek9 -ls`
src/main/kotlin/org/ek9lang/intellij/dap/Ek9DapDescriptorFactory.kt    registers the EK9 DAP server
src/main/kotlin/org/ek9lang/intellij/dap/Ek9DapDescriptor.kt          spawns `ek9 -dap`; launch body + `.ek9` association
```
