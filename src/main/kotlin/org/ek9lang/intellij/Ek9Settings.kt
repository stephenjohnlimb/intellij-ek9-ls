package org.ek9lang.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Where to find Java and the EK9 compiler jar (or the native executable) — the same two things the
 * VS Code extension keeps as `ek9-ls.javaCommand` / `ek9-ls.compilerPath`. Both the LSP server
 * (`ek9 -ls`) and the debug adapter (`ek9 -dap`) are spawned from these.
 *
 * NOTE (follow-up): add a Settings UI (a `Configurable`) so these can be edited in-IDE. For now edit
 * them here or in the stored `ek9.xml`.
 */
@State(name = "Ek9Settings", storages = [Storage("ek9.xml")])
class Ek9Settings : PersistentStateComponent<Ek9Settings> {

    /** Path to a Java 25 executable (used when [useNative] is false). */
    var javaCommand: String = "java"

    /** Path to `ek9c-jar-with-dependencies.jar`, or to the native `ek9` executable when [useNative]. */
    var compilerPath: String = ""

    /** Use the native EK9 compiler executable instead of `java -jar`. */
    var useNative: Boolean = false

    override fun getState(): Ek9Settings = this

    override fun loadState(state: Ek9Settings) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        fun getInstance(): Ek9Settings =
            ApplicationManager.getApplication().getService(Ek9Settings::class.java)
    }
}
