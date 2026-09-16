package org.ek9lang.intellij

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Settings page (Settings/Preferences → Tools → EK9) for the Java command and the EK9 compiler jar
 * (or native executable). Both the language server (`ek9 -ls`, [org.ek9lang.intellij.lsp.Ek9LanguageServerFactory])
 * and the debug adapter (`ek9 -dap`, [org.ek9lang.intellij.dap.Ek9DapDescriptor]) are spawned from
 * these, so without them set both halves fail to start. Backed by [Ek9Settings].
 *
 * Note: changing the compiler path takes effect for a new debug session immediately (the descriptor
 * reads it on each launch), but a running language server must be restarted (LSP4IJ's "Restart" action,
 * or restart the IDE) to pick up the new path.
 */
class Ek9Configurable : Configurable {

  private var dialogPanel: DialogPanel? = null

  override fun getDisplayName(): String = "EK9"

  override fun createComponent(): JComponent {
    val settings = Ek9Settings.getInstance()
    val built = panel {
      row("Java command:") {
        textField()
          .bindText(settings::javaCommand)
          .align(AlignX.FILL)
          .comment("Path to a Java 25 executable, or just <code>java</code> if it is on the PATH. Used when 'Use native executable' is off.")
      }
      row("EK9 compiler jar:") {
        textField()
          .bindText(settings::compilerPath)
          .align(AlignX.FILL)
          .comment("Full path to <code>ek9c-jar-with-dependencies.jar</code>, or to the native <code>ek9</code> executable when 'Use native executable' is on.")
      }
      row {
        checkBox("Use native EK9 executable (instead of java -jar)")
          .bindSelected(settings::useNative)
      }
    }
    dialogPanel = built
    return built
  }

  override fun isModified(): Boolean = dialogPanel?.isModified() ?: false

  override fun apply() {
    dialogPanel?.apply()
  }

  override fun reset() {
    dialogPanel?.reset()
  }

  override fun disposeUIResources() {
    dialogPanel = null
  }
}
