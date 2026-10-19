package org.ek9lang.intellij.dap

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.dap.client.LaunchUtils
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor
import org.ek9lang.intellij.Ek9Settings

/**
 * Drives the EK9 debug adapter (`ek9 -dap`) for LSP4IJ's DAP client. This reproduces the manual
 * "Debug Adapter Protocol" run configuration that is already proven to work in IntelliJ Community
 * (breakpoints in `.ek9`, EK9 call stack, typed locals, stepping) — but the command line, the DAP
 * `launch` body and the `.ek9` breakpoint association are supplied HERE, so a user no longer
 * hand-crafts any of them.
 *
 * Transport note: LSP4IJ speaks DAP to this adapter over **stdio** (the `-dap` process speaks DAP on
 * its own stdin/stdout). The JDWP transport between the adapter and the debuggee JVM is internal to
 * the adapter and invisible to LSP4IJ — so no socket "server ready" pattern is required, and the
 * inherited [getServerReadyConfig] (immediate, for a command-based stdio server) is correct.
 */
class Ek9DapDescriptor(
  options: RunConfigurationOptions,
  environment: ExecutionEnvironment,
  serverDefinition: DebugAdapterServerDefinition?
) : DebugAdapterDescriptor(options, environment, serverDefinition) {

  /**
   * Launch `ek9 -dap`, reusing the same Java + compiler-jar (or native executable) paths as the LSP
   * server. `--add-modules jdk.jdi` is required because the adapter attaches to the debuggee via JDI.
   */
  @Throws(ExecutionException::class)
  override fun startServer(): ProcessHandler {
    val settings = Ek9Settings.getInstance()
    val command = if (settings.useNative) {
      "${settings.compilerPath} -dap"
    } else {
      "${settings.javaCommand} --add-modules jdk.jdi -jar ${settings.compilerPath} -dap"
    }
    return startServer(createStartServerCommandLine(command))
  }

  /**
   * The DAP `launch` request body. `${'$'}{file}` is the program chosen in the run configuration's
   * Configuration tab (the compiled EK9 `.jar`); `${'$'}{workspaceFolder}` is its working directory.
   * Both are substituted by [LaunchUtils.getDapParameters].
   *
   * The run configuration's own launch JSON (its Parameters tab) is laid over that, so a person can
   * give the adapter's other launch attributes - `"seed": 42` repeats a run's random draws exactly:
   * each EK9 debug launch says its seed in the Debug Console, and that one repeats the run.
   */
  override fun getDapParameters(): MutableMap<String, Any> {
    val opts = options as DAPRunConfigurationOptions
    val context = LaunchUtils.LaunchContext(opts.file, opts.workingDirectory)
    val parameters = LaunchUtils.getDapParameters(LAUNCH_TEMPLATE, context)
    val own = opts.launchConfiguration
    if (!own.isNullOrBlank()) {
      LaunchUtils.getDapParameters(own, context)
        .filterKeys { it != "type" && it != "request" }
        .forEach { (name, value) -> parameters[name] = value }
    }
    return parameters
  }

  /** Breakpoints are valid in `.ek9` source — this replaces the manual Mappings-tab association. */
  override fun isDebuggableFile(file: VirtualFile, project: Project): Boolean =
    "ek9".equals(file.extension, ignoreCase = true)

  /** The EK9 file type (TextMate-registered) — used for breakpoint / debugger-editor association. */
  override fun getFileType(): FileType? =
    FileTypeManager.getInstance().getFileTypeByExtension("ek9")

  override fun getServerName(): String = "EK9"

  private companion object {
    val LAUNCH_TEMPLATE = """
      {
        "type": "ek9",
        "request": "launch",
        "program": "${'$'}{file}",
        "cwd": "${'$'}{workspaceFolder}"
      }
    """.trimIndent()
  }
}
