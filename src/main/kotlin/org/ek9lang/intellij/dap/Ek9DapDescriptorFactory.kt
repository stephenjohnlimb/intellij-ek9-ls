package org.ek9lang.intellij.dap

import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory

/**
 * Registers the EK9 debug adapter (`ek9 -dap`) with LSP4IJ's DAP client — see the
 * `<debugAdapterServer>` registration in plugin.xml. Once registered, a user creates a generic
 * "Debug Adapter Protocol" run configuration, selects **EK9** as the server, points at the compiled
 * program and runs; the command line, DAP `launch` body and `.ek9` breakpoint association are all
 * supplied by [Ek9DapDescriptor], so none of it is hand-typed. This is the IntelliJ counterpart to
 * the VS Code extension's debugger contribution — the same adapter, the same experience, on Community.
 */
class Ek9DapDescriptorFactory : DebugAdapterDescriptorFactory() {

  override fun createDebugAdapterDescriptor(
    options: RunConfigurationOptions,
    environment: ExecutionEnvironment
  ): DebugAdapterDescriptor = Ek9DapDescriptor(options, environment, serverDefinition)

  /** `.ek9` files are debuggable by this server, so breakpoints are offered without a Mappings tab. */
  override fun isDebuggableFile(file: VirtualFile, project: Project): Boolean =
    "ek9".equals(file.extension, ignoreCase = true)
}
