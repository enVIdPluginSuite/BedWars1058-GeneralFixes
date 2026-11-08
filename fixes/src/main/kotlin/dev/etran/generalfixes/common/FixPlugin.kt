package dev.etran.generalfixes.common

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Base for every plugin jar of this project: config loading, `reload` command, messages and debug logging.
 * A plugin only says which fixes it contains.
 */
abstract class FixPlugin : JavaPlugin() {

    /** Permission needed for the reload command. */
    protected abstract val adminPermission: String

    protected abstract fun createFixes(): List<Fix>

    private var fixes: List<Fix> = emptyList()

    @Volatile
    lateinit var cfg: FileConfiguration
        private set

    @Volatile
    var debugEnabled = false
        private set

    @Volatile
    private var prefix = ""

    @Volatile
    private var messages: Map<String, String> = emptyMap()

    override fun onEnable() {
        val bedWars = server.pluginManager.getPlugin("BedWars1058")
        if (bedWars == null || !bedWars.isEnabled) {
            logger.severe("BedWars1058 is not enabled, disabling.")
            server.pluginManager.disablePlugin(this)
            return
        }

        cfg = loadConfiguration()
        readCommon(cfg)

        fixes = createFixes()
        for (fix in fixes) {
            fix.load(cfg)
            server.pluginManager.registerEvents(fix, this)
        }

        // Language files and the like are final once every plugin finished enabling.
        server.scheduler.runTask(this, Runnable { fixes.forEach { it.onStarted() } })

        logger.info("Enabled. Edit config.yml and run /${description.commands.keys.firstOrNull() ?: "reload"} reload to apply changes.")
    }

    override fun onDisable() {
        server.scheduler.cancelTasks(this)
        for (fix in fixes) {
            try {
                fix.onStop()
            } catch (t: Throwable) {
                logger.warning("Error while stopping ${fix.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** Re-reads config.yml and re-applies everything. @return false if loading failed (old settings stay active). */
    fun reloadAll(): Boolean {
        return try {
            val fresh = loadConfiguration()
            fixes.forEach { it.load(fresh) }
            cfg = fresh
            readCommon(fresh)
            fixes.forEach { it.onStarted() }
            true
        } catch (t: Throwable) {
            logger.severe("Could not reload config.yml: ${t.message}")
            if (debugEnabled) t.printStackTrace()
            false
        }
    }

    fun debug(message: String) {
        if (debugEnabled) logger.info("[debug] $message")
    }

    private fun readCommon(config: FileConfiguration) {
        debugEnabled = config.getBoolean("debug", false)
        prefix = Text.color(config.getString("messages.prefix", ""))
        messages = mapOf(
            "reload-success" to Text.color(config.getString("messages.reload-success", "&aConfiguration reloaded.")),
            "reload-failed" to Text.color(config.getString("messages.reload-failed", "&cReload failed, check the console.")),
            "no-permission" to Text.color(config.getString("messages.no-permission", "&cYou do not have permission to do that.")),
            "usage" to Text.color(config.getString("messages.usage", "&7Usage: &f/%label% reload"))
        )
    }

    private fun message(key: String): String = prefix + (messages[key] ?: key)

    /**
     * Loads config.yml as UTF-8 (older Bukkit versions use the platform charset, which would garble translations)
     * and falls back to the defaults inside the jar for any missing option.
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
        if (!sender.hasPermission(adminPermission)) {
            sender.sendMessage(message("no-permission"))
            return true
        }
        if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
            sender.sendMessage(message(if (reloadAll()) "reload-success" else "reload-failed"))
        } else {
            sender.sendMessage(message("usage").replace("%label%", label))
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<out String>
    ): List<String> {
        if (args.size == 1 && sender.hasPermission(adminPermission) && "reload".startsWith(args[0].lowercase())) {
            return listOf("reload")
        }
        return emptyList()
    }
}
