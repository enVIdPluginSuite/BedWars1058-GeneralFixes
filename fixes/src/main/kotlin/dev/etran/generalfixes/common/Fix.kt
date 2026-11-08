package dev.etran.generalfixes.common

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.Listener

/**
 * One self-contained fix. The same class is used by the standalone plugins (options at the root of config.yml,
 * [section] = "") and by the combined plugin (options under e.g. "fixes.invisible-chests").
 */
abstract class Fix(protected val plugin: FixPlugin, section: String) : Listener {

    /** Config path prefix, either "" or ending with a dot. */
    protected val prefix: String = if (section.isEmpty()) "" else "$section."

    /** (Re)reads this fix's options. Called on enable and on every reload. */
    abstract fun load(cfg: FileConfiguration)

    /**
     * Runs once after the server finished enabling all plugins and again after every reload.
     * For work that is applied once instead of per event.
     */
    open fun onStarted() {}

    /** Runs when the plugin is disabled. For fixes that changed server state and must undo it. */
    open fun onStop() {}
}
