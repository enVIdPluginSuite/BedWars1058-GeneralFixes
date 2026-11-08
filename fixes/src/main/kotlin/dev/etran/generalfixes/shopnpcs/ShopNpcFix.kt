package dev.etran.generalfixes.shopnpcs

import com.andrei1058.bedwars.api.BedWars
import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent
import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.scheduler.BukkitTask

/**
 * Fix 5: shop / team-upgrade villagers can be punched to death and never come back.
 *
 * Cause: BedWars1058 spawns each shop villager once when the game starts and only cancels the *interaction* with it,
 * not damage. On top of that nothing ever respawns a villager that died (or was removed by another plugin / a
 * command), so the team loses its shop for the rest of the game.
 *
 * Fix, two parts:
 *  1. All damage to a shop villager is cancelled.
 *  2. If a shop villager is missing anyway, it is respawned at its original spot. This uses BedWars1058's own spawn
 *     routine without any players, so the villager is created exactly like the original on every server version and
 *     the hologram that is still there is not duplicated.
 */
class ShopNpcFix(plugin: FixPlugin, section: String) : Fix(plugin, section) {

    private class Settings(cfg: FileConfiguration, p: String) {
        val enabled = cfg.getBoolean(p + "enabled", true)
        val protect = cfg.getBoolean(p + "protect-from-damage", true)
        val respawn = cfg.getBoolean(p + "respawn", true)
        val respawnDelay = cfg.getInt(p + "respawn-delay-ticks", 20).coerceAtLeast(1).toLong()
        val checkInterval = cfg.getInt(p + "check-interval-ticks", 100).coerceAtLeast(0).toLong()
        val radius = cfg.getDouble(p + "search-radius", 1.5).coerceIn(0.5, 8.0)
    }

    @Volatile
    private var settings = Settings(plugin.cfg, prefix)

    /** world name -> shop spots (block coordinates) where a villager has been seen in the current game. */
    private val known = HashMap<String, MutableSet<String>>()

    private var task: BukkitTask? = null

    override fun load(cfg: FileConfiguration) {
        settings = Settings(cfg, prefix)
    }

    override fun onStarted() {
        task?.cancel()
        task = null
        val s = settings
        if (s.enabled && s.respawn && s.checkInterval > 0) {
            task = plugin.server.scheduler.runTaskTimer(
                plugin, Runnable { scanAllArenas() }, s.checkInterval, s.checkInterval
            )
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 1) protection
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDamage(event: EntityDamageEvent) {
        val s = settings
        if (!s.enabled || !s.protect) return
        if (!isVillager(event.entity)) return
        if (findShop(event.entity, s) != null) event.isCancelled = true
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 2) respawn
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: EntityDeathEvent) {
        val s = settings
        if (!s.enabled || !s.respawn) return
        if (!isVillager(event.entity)) return
        val (arena, spot) = findShop(event.entity, s) ?: return

        known.getOrPut(arena.worldName) { HashSet() }.add(key(spot))
        plugin.debug("Shop villager died in ${arena.worldName}, respawning in ${s.respawnDelay} ticks")
        plugin.server.scheduler.runTaskLater(plugin, Runnable { respawnIfMissing(arena, spot) }, s.respawnDelay)
    }

    @EventHandler
    fun onGameState(event: GameStateChangeEvent) {
        val arena = event.arena ?: return
        when (event.newState) {
            GameState.playing -> {
                known.remove(arena.worldName)
                // BedWars1058 spawns the shops 1 second after the start; remember them once they exist.
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (arena.status == GameState.playing) scanArena(arena, settings)
                }, 50L)
            }
            GameState.waiting, GameState.restarting -> known.remove(arena.worldName)
            else -> Unit
        }
    }

    private fun scanAllArenas() {
        val s = settings
        if (!s.enabled || !s.respawn) return
        val api = bedWars() ?: return
        for (arena in ArrayList(api.arenaUtil.arenas)) {
            if (arena.status == GameState.playing) scanArena(arena, s)
        }
    }

    /** Remembers the villagers that exist and respawns the remembered ones that vanished. */
    private fun scanArena(arena: IArena, s: Settings) {
        val seen = known.getOrPut(arena.worldName) { HashSet() }
        for (spot in shopSpots(arena)) {
            val world = spot.world ?: continue
            if (!world.isChunkLoaded(spot.blockX shr 4, spot.blockZ shr 4)) continue
            if (hasVillager(spot, s)) {
                seen.add(key(spot))
            } else if (seen.contains(key(spot))) {
                respawn(arena, spot)
            }
        }
    }

    private fun respawnIfMissing(arena: IArena, spot: Location) {
        if (arena.status != GameState.playing) return
        val world = spot.world ?: return
        if (!world.isChunkLoaded(spot.blockX shr 4, spot.blockZ shr 4)) return
        if (!hasVillager(spot, settings)) respawn(arena, spot)
    }

    private fun respawn(arena: IArena, spot: Location) {
        val support = bedWars()?.versionSupport ?: return
        try {
            // No players -> only the villager is created. The holograms from the first spawn are still in place.
            support.spawnShop(spot.clone(), "", emptyList<Player>(), arena)
            plugin.debug("Respawned shop villager at ${spot.blockX}, ${spot.blockY}, ${spot.blockZ} in ${arena.worldName}")
        } catch (t: Throwable) {
            plugin.logger.warning("Could not respawn a shop villager: ${t.message}")
            if (plugin.debugEnabled) t.printStackTrace()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------------------------

    private fun bedWars(): BedWars? = Bukkit.getServicesManager().getRegistration(BedWars::class.java)?.provider

    /** "VILLAGER" on every version; compared by name so no version specific enum is needed. */
    private fun isVillager(entity: Entity): Boolean = entity.type.name == "VILLAGER"

    /** Shop and team-upgrade spots of every team, as configured in the arena. */
    private fun shopSpots(arena: IArena): List<Location> {
        val result = ArrayList<Location>()
        try {
            for (team in arena.teams) {
                team.shop?.let { result.add(it) }
                team.teamUpgrades?.let { result.add(it) }
            }
        } catch (_: Throwable) {
            // The arena is being destroyed right now.
        }
        return result
    }

    /** The arena and shop spot this villager belongs to, or null if it is just a regular villager. */
    private fun findShop(entity: Entity, s: Settings): Pair<IArena, Location>? {
        val world = entity.world
        val arena = bedWars()?.arenaUtil?.getArenaByIdentifier(world.name) ?: return null
        val here = entity.location
        val maxSq = s.radius * s.radius
        for (spot in shopSpots(arena)) {
            if (spot.world != world) continue
            if (spot.distanceSquared(here) <= maxSq) return Pair(arena, spot)
        }
        return null
    }

    private fun hasVillager(spot: Location, s: Settings): Boolean {
        val world: World = spot.world ?: return false
        val r = s.radius
        val minCx = (spot.x - r).toInt() shr 4
        val maxCx = (spot.x + r).toInt() shr 4
        val minCz = (spot.z - r).toInt() shr 4
        val maxCz = (spot.z + r).toInt() shr 4
        val maxSq = r * r
        for (cx in minCx..maxCx) {
            for (cz in minCz..maxCz) {
                if (!world.isChunkLoaded(cx, cz)) continue
                for (entity in world.getChunkAt(cx, cz).entities) {
                    if (isVillager(entity) && !entity.isDead && entity.location.distanceSquared(spot) <= maxSq) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun key(spot: Location) = "${spot.blockX},${spot.blockY},${spot.blockZ}"
}
