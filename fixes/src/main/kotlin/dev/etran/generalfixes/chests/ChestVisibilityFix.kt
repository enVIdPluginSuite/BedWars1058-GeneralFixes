package dev.etran.generalfixes.chests

import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent
import com.andrei1058.bedwars.api.events.player.PlayerFirstSpawnEvent
import com.andrei1058.bedwars.api.events.player.PlayerReSpawnEvent
import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import java.lang.reflect.Method

/**
 * Fix 2: chests that are invisible to some players when the game starts.
 *
 * The client only creates the chest's renderer when it receives a block update for it. Chests that were part of the
 * map copy are sent with the chunk and, for some players, never get that update, so they stay invisible until a
 * neighbouring block changes or the chest is opened. Re-sending the chest blocks to the player does exactly what
 * that neighbour update would do.
 */
class ChestVisibilityFix(plugin: FixPlugin, section: String) : Fix(plugin, section) {

    private class Settings(cfg: FileConfiguration, p: String) {
        val enabled = cfg.getBoolean(p + "enabled", true)
        val onGameStart = cfg.getBoolean(p + "on-game-start", true)
        val onFirstSpawn = cfg.getBoolean(p + "on-first-spawn", true)
        val onRespawn = cfg.getBoolean(p + "on-respawn", false)
        val delays: List<Long> = cfg.getLongList(p + "delays-ticks")
            .map { it.coerceAtLeast(1L) }
            .ifEmpty { listOf(2L, 20L, 60L) }
        val chunkRadius = cfg.getInt(p + "chunk-radius", 8).coerceIn(1, 32)
        val types: Set<String> = cfg.getStringList(p + "block-types")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
            .ifEmpty { setOf("CHEST", "TRAPPED_CHEST", "ENDER_CHEST") }
    }

    @Volatile
    private var settings = Settings(plugin.cfg, prefix)

    override fun load(cfg: FileConfiguration) {
        settings = Settings(cfg, prefix)
    }

    @EventHandler
    fun onGameState(event: GameStateChangeEvent) {
        val s = settings
        if (!s.enabled || !s.onGameStart) return
        if (event.newState != GameState.playing) return
        scheduleForArena(event.arena)
    }

    @EventHandler
    fun onFirstSpawn(event: PlayerFirstSpawnEvent) {
        val s = settings
        if (!s.enabled || !s.onFirstSpawn) return
        schedule(listOf(event.player))
    }

    @EventHandler
    fun onRespawn(event: PlayerReSpawnEvent) {
        val s = settings
        if (!s.enabled || !s.onRespawn) return
        schedule(listOf(event.player))
    }

    private fun scheduleForArena(arena: IArena?) {
        if (arena == null) return
        val everyone = ArrayList<Player>(arena.players)
        arena.spectators?.let { everyone.addAll(it) }
        schedule(everyone)
    }

    private fun schedule(players: Collection<Player>) {
        if (players.isEmpty()) return
        // Hold on to the UUIDs so a player who leaves in between is not kept alive.
        val ids = players.map { it.uniqueId }
        for (delay in settings.delays) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                for (id in ids) {
                    val player = plugin.server.getPlayer(id) ?: continue
                    if (player.isOnline) resend(player)
                }
            }, delay)
        }
    }

    private fun resend(player: Player) {
        val s = settings
        val world = player.world
        val center = player.location
        val cx = center.blockX shr 4
        val cz = center.blockZ shr 4
        var count = 0

        for (x in cx - s.chunkRadius..cx + s.chunkRadius) {
            for (z in cz - s.chunkRadius..cz + s.chunkRadius) {
                if (!world.isChunkLoaded(x, z)) continue
                for (tile in world.getChunkAt(x, z).tileEntities) {
                    if (tile.type.name !in s.types) continue
                    BlockSender.send(player, tile.block)
                    count++
                }
            }
        }
        plugin.debug("Re-sent $count chest block(s) to ${player.name}")
    }
}

/** Sends a block (with its current state) to one player, on every server version. */
private object BlockSender {

    private val getBlockData: Method? = try {
        Block::class.java.getMethod("getBlockData")
    } catch (_: Throwable) {
        null
    }

    private val sendModern: Method? = try {
        Player::class.java.getMethod(
            "sendBlockChange", Location::class.java, Class.forName("org.bukkit.block.data.BlockData")
        )
    } catch (_: Throwable) {
        null
    }

    fun send(player: Player, block: Block) {
        val get = getBlockData
        val send = sendModern
        if (get != null && send != null) {
            // 1.13+: BlockData keeps the chest facing / type / waterlogged state.
            send.invoke(player, block.location, get.invoke(block))
        } else {
            sendLegacy(player, block)
        }
    }

    @Suppress("DEPRECATION")
    private fun sendLegacy(player: Player, block: Block) {
        // 1.8 - 1.12: material + data byte.
        player.sendBlockChange(block.location, block.type, block.data)
    }
}
