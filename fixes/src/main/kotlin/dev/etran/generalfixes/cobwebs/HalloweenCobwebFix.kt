package dev.etran.generalfixes.cobwebs

import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent
import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority

/**
 * Fix 1: cobwebs appearing when a player dies.
 *
 * Cause: BedWars1058 ships a Halloween special (active around 31 October) whose listener places a cobweb one block
 * above the victim on every kill and tags it with the metadata key "give-bw-exp".
 *
 * Fix: after that listener ran (MONITOR priority) the web is removed again, together with its XP marker and its
 * entry in the arena's placed-block list. Webs placed by players never carry the marker and are left alone.
 */
class HalloweenCobwebFix(plugin: FixPlugin, section: String) : Fix(plugin, section) {

    private class Settings(cfg: FileConfiguration, p: String) {
        val enabled = cfg.getBoolean(p + "enabled", true)
        val onlySpecialWebs = cfg.getBoolean(p + "only-special-webs", true)
        val recheckDelay = cfg.getInt(p + "recheck-delay-ticks", 1).coerceAtLeast(0).toLong()
    }

    @Volatile
    private var settings = Settings(plugin.cfg, prefix)

    override fun load(cfg: FileConfiguration) {
        settings = Settings(cfg, prefix)
    }

    private companion object {
        const val SPECIAL_MARKER = "give-bw-exp"
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onKill(event: PlayerKillEvent) {
        val s = settings
        if (!s.enabled) return

        val victim = event.victim ?: return
        // Same spot the special uses: one block above the victim.
        val block = victim.location.clone().add(0.0, 1.0, 0.0).block
        val arena: IArena? = event.arena

        removeIfSpecialWeb(block, arena)

        if (s.recheckDelay > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { removeIfSpecialWeb(block, arena) }, s.recheckDelay)
        }
    }

    private fun removeIfSpecialWeb(block: Block, arena: IArena?) {
        if (!isWeb(block.type)) return
        val marked = block.hasMetadata(SPECIAL_MARKER)
        if (settings.onlySpecialWebs && !marked) return

        block.setType(Material.AIR)
        if (marked) {
            val bedWars = Bukkit.getPluginManager().getPlugin("BedWars1058")
            if (bedWars != null) block.removeMetadata(SPECIAL_MARKER, bedWars)
        }
        try {
            arena?.removePlacedBlock(block)
        } catch (t: Throwable) {
            plugin.debug("Could not unregister placed block: ${t.message}")
        }
        plugin.debug("Removed death cobweb at ${block.x}, ${block.y}, ${block.z}")
    }

    /** "WEB" on 1.8 - 1.12, "COBWEB" on 1.13+. */
    private fun isWeb(type: Material): Boolean {
        val name = type.name
        return name == "WEB" || name == "COBWEB"
    }
}
