package dev.etran.generalfixes.oldladders

import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

/**
 * Fix 6: clients older than 1.13 glitch while climbing ladders on a newer server.
 *
 * Cause: since 1.9 the server's ladder is 3/16 of a block thick, in 1.8 it was 2/16. The old client moves inside the
 * area the server considers solid, gets pushed back, and can neither climb smoothly nor leave the ladder cleanly.
 *
 * Fix: the server's ladder collision box is thinned to the 1.8 thickness. It is only active while it is needed:
 * with ViaVersion / ProtocolSupport installed the box is thinned only while a client older than [Settings.oldBelow]
 * is online, so a server with only 1.13+ players keeps the vanilla ladder. Clients 1.13 and newer can always climb.
 */
class OldLadderFix(plugin: FixPlugin, section: String) : Fix(plugin, section) {

    private enum class Mode { AUTO, ALWAYS, OLD_CLIENTS_ONLY }

    private class Settings(cfg: FileConfiguration, p: String) {
        val enabled = cfg.getBoolean(p + "enabled", true)
        val mode: Mode = try {
            Mode.valueOf((cfg.getString(p + "mode", "AUTO") ?: "AUTO").trim().uppercase())
        } catch (_: IllegalArgumentException) {
            Mode.AUTO
        }
        val oldBelow = cfg.getInt(p + "old-client-protocol", 393)
        val thickness = cfg.getDouble(p + "thickness", 0.125).coerceIn(0.0625, 0.1875)
    }

    @Volatile
    private var settings = Settings(plugin.cfg, prefix)

    private val patcher = LadderShapePatcher(plugin)
    private val clients = ClientVersions()
    private var appliedThickness = Double.NaN

    override fun load(cfg: FileConfiguration) {
        settings = Settings(cfg, prefix)
    }

    override fun onStarted() = evaluate(null)

    override fun onStop() {
        if (patcher.isApplied) {
            patcher.restore()
            plugin.logger.info("Ladder collision restored to the vanilla thickness.")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        // The client version is known from the login on, a tick later is safe for every plugin.
        plugin.server.scheduler.runTaskLater(plugin, Runnable { evaluate(null) }, 2L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val leaving = event.player.uniqueId
        plugin.server.scheduler.runTaskLater(plugin, Runnable { evaluate(leaving) }, 1L)
    }

    /** Decides whether the thin ladder should be active right now and brings the server in that state. */
    private fun evaluate(leaving: UUID?) {
        val s = settings
        val wanted = s.enabled && patcher.isNeeded() && when (s.mode) {
            Mode.ALWAYS -> true
            Mode.OLD_CLIENTS_ONLY -> anyOldClient(s, leaving)
            Mode.AUTO -> if (clients.isAvailable()) anyOldClient(s, leaving) else true
        }

        if (!wanted) {
            if (patcher.isApplied) {
                patcher.restore()
                appliedThickness = Double.NaN
                plugin.logger.info("Ladder collision back to vanilla thickness.")
            }
            return
        }

        if (patcher.isApplied && appliedThickness == s.thickness) return

        val count = patcher.apply(s.thickness)
        if (count > 0) {
            appliedThickness = s.thickness
            plugin.logger.info("Ladder collision thinned to ${s.thickness} blocks ($count shape(s)) for pre-1.13 clients.")
        } else {
            appliedThickness = Double.NaN
            plugin.logger.warning(
                "Could not find any ladder collision shape to change on this server version. " +
                    "The fix is inactive. Set debug: true in config.yml and report the console output."
            )
        }
    }

    private fun anyOldClient(s: Settings, leaving: UUID?): Boolean {
        for (player in plugin.server.onlinePlayers) {
            if (player.uniqueId == leaving) continue
            if (isOld(player, s)) return true
        }
        return false
    }

    private fun isOld(player: Player, s: Settings): Boolean {
        val protocol = clients.protocolOf(player) ?: return false
        return protocol < s.oldBelow
    }
}
