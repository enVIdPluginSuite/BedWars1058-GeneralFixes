package dev.etran.generalfixes

import dev.etran.generalfixes.fix.ChestVisibilityFix
import dev.etran.generalfixes.fix.HalloweenCobwebFix
import dev.etran.generalfixes.fix.PopUpTowerLanguageFix
import dev.etran.generalfixes.fix.SwordReplacementFix
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class GeneralFixesPlugin : JavaPlugin() {

    /** Current settings. Replaced as a whole on reload, listeners always read the latest one. */
    @Volatile
    lateinit var settings: Settings
        private set

    private lateinit var towerFix: PopUpTowerLanguageFix

    override fun onEnable() {
        val bedWars = server.pluginManager.getPlugin("BedWars1058")
        if (bedWars == null || !bedWars.isEnabled) {
            logger.severe("BedWars1058 is not enabled, disabling.")
            server.pluginManager.disablePlugin(this)
            return
        }

        settings = Settings(loadConfiguration())

        towerFix = PopUpTowerLanguageFix(this)
        val pm = server.pluginManager
        pm.registerEvents(HalloweenCobwebFix(this), this)
        pm.registerEvents(ChestVisibilityFix(this), this)
        pm.registerEvents(SwordReplacementFix(this), this)

        // Languages are final once every plugin finished enabling.
        server.scheduler.runTask(this, Runnable { towerFix.apply() })

        logger.info("Enabled. Use /bwfixes reload after editing config.yml.")
    }

    override fun onDisable() {
        server.scheduler.cancelTasks(this)
    }

    /** Re-reads config.yml and re-applies everything that is applied once. @return false if loading failed. */
    fun reloadSettings(): Boolean {
        return try {
            val fresh = Settings(loadConfiguration())
            settings = fresh
            towerFix.apply()
            true
        } catch (t: Throwable) {
            logger.severe("Could not reload config.yml: ${t.message}")
            if (settings.debug) t.printStackTrace()
            false
        }
    }

    /**
     * Loads config.yml as UTF-8 (the Bukkit default is the platform charset on old versions, which would garble the
     * translations) and falls back to the defaults inside the jar for any missing option.
     */
    private fun loadConfiguration(): YamlConfiguration {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        val file = File(dataFolder, "config.yml")
        if (!file.exists()) saveResource("config.yml", false)

        val config = YamlConfiguration()
        InputStreamReader(file.inputStream(), StandardCharsets.UTF_8).use { config.load(it) }

        getResource("config.yml")?.let { stream ->
            InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                config.setDefaults(YamlConfiguration.loadConfiguration(reader))
            }
        }
        return config
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val s = settings
        if (!sender.hasPermission("bwfixes.admin")) {
            sender.sendMessage(s.message("no-permission"))
            return true
        }
        if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
            sender.sendMessage(s.message(if (reloadSettings()) "reload-success" else "reload-failed"))
        } else {
            sender.sendMessage(s.message("usage").replace("%label%", label))
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<out String>
    ): List<String> {
        if (args.size == 1 && sender.hasPermission("bwfixes.admin") && "reload".startsWith(args[0].lowercase())) {
            return listOf("reload")
        }
        return emptyList()
    }

    fun debug(message: String) {
        if (settings.debug) logger.info("[debug] $message")
    }
}
