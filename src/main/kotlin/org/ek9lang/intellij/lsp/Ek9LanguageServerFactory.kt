package org.ek9lang.intellij.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import org.ek9lang.intellij.Ek9Settings

/**
 * Registers the EK9 language server with LSP4IJ by spawning `ek9 -ls` (the EK9 compiler in
 * language-server mode) as an external process, reusing the Java + compiler-jar paths from
 * [Ek9Settings]. LSP4IJ handles the LSP lifecycle and routes `*.ek9` files (mapped by pattern in
 * plugin.xml, which preserves the TextMate colouring) to it.
 */
class Ek9LanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        val settings = Ek9Settings.getInstance()
        // -lsh = language server WITH EK9 hover help; use -ls for the server without hover help.
        val commandLine = if (settings.useNative) {
            GeneralCommandLine(settings.compilerPath, "-lsh")
        } else {
            GeneralCommandLine(settings.javaCommand, "-jar", settings.compilerPath, "-lsh")
        }
        return object : OSProcessStreamConnectionProvider() {
            init {
                super.setCommandLine(commandLine)
            }
        }
    }
}
