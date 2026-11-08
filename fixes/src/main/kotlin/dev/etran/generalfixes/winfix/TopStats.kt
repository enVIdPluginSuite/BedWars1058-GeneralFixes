package dev.etran.generalfixes.winfix

import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.stats.PlayerGameStats
import com.andrei1058.bedwars.api.arena.team.ITeam
import com.andrei1058.bedwars.api.language.Language
import org.bukkit.Bukkit
import java.util.UUID

/** One row of the "Top" list, independent of where the numbers came from. */
interface TopEntry {
    val uuid: UUID
    val username: String
    val displayName: String

    /** Team the player played for in this match (also valid after elimination / disconnect). */
    val team: ITeam?

    /** Raw numeric value of the ranked statistic, used for "hide zero values". */
    val numeric: Long

    /** Display value of any statistic id (kills, finalKills, deaths, bedsDestroyed, ...). */
    fun value(statId: String, language: Language?): String
}

/** Row backed by BedWars1058's own per-game statistics holder. */
private class HolderEntry(
    private val stats: PlayerGameStats,
    private val orderBy: String,
    arena: IArena,
) : TopEntry {
    override val uuid: UUID = stats.player
    override val username: String = stats.username
    override val displayName: String = stats.displayPlayer
    override val team: ITeam? = resolveTeam(arena, uuid)

    override val numeric: Long
        get() {
            val raw = stats.getStatistic(orderBy).orElse(null)?.value
            return (raw as? Number)?.toLong() ?: 1L
        }

    override fun value(statId: String, language: Language?): String {
        val statistic = stats.getStatistic(statId).orElse(null)
        return statistic?.getDisplayValue(language) ?: "0"
    }
}

/** Row backed by this addon's own kill counter (fallback when the stats holder is unusable). */
private class TrackedEntry(
    override val uuid: UUID,
    override val username: String,
    override val displayName: String,
    private val kills: Int,
    arena: IArena,
) : TopEntry {
    override val team: ITeam? = resolveTeam(arena, uuid)
    override val numeric: Long = kills.toLong()

    override fun value(statId: String, language: Language?): String =
        if (statId.equals("kills", ignoreCase = true)) kills.toString() else "0"
}

private fun resolveTeam(arena: IArena, uuid: UUID): ITeam? {
    val online = Bukkit.getPlayer(uuid)
    return (if (online != null) arena.getTeam(online) else null) ?: arena.getExTeam(uuid)
}

/**
 * Counts kills per arena from PlayerKillEvent. Only used when BedWars1058's own statistics
 * holder is missing, empty or from an older API that does not have it.
 */
class KillTracker {
    private class Tracked(var name: String, var display: String, var kills: Int)

    private val perArena = HashMap<String, LinkedHashMap<UUID, Tracked>>()

    fun reset(arena: IArena) {
        perArena.remove(arena.arenaName)
    }

    fun addKill(arena: IArena, killer: org.bukkit.entity.Player) {
        val map = perArena.getOrPut(arena.arenaName) { LinkedHashMap() }
        val tracked = map.getOrPut(killer.uniqueId) { Tracked(killer.name, killer.displayName, 0) }
        tracked.name = killer.name
        tracked.display = killer.displayName
        tracked.kills++
    }

    @Suppress("DEPRECATION")
    fun entries(arena: IArena): List<TopEntry> {
        val map = perArena[arena.arenaName] ?: emptyMap<UUID, Tracked>()
        val rows = LinkedHashMap<UUID, TrackedEntry>()

        // Everybody who played, even with 0 kills.
        for (team in arena.teams) {
            for (member in team.membersCache) {
                val kills = map[member.uniqueId]?.kills ?: 0
                rows[member.uniqueId] = TrackedEntry(member.uniqueId, member.name, member.displayName, kills, arena)
            }
        }
        // Players that left but did get kills.
        for ((id, tracked) in map) {
            if (!rows.containsKey(id)) {
                rows[id] = TrackedEntry(id, tracked.name, tracked.display, tracked.kills, arena)
            }
        }
        return rows.values.sortedByDescending { it.numeric }
    }
}

/** Resolves the ordered "Top" list for an arena. */
class TopStatsSource(private val tracker: KillTracker) {

    fun entries(arena: IArena, orderBy: String): List<TopEntry> {
        val fromHolder = try {
            val holder = arena.statsHolder
            if (holder != null && holder.hasStatistic(orderBy)) {
                holder.getOrderedBy(orderBy)
                    .mapNotNull { it.orElse(null) }
                    .map<PlayerGameStats, TopEntry> { HolderEntry(it, orderBy, arena) }
            } else {
                emptyList()
            }
        } catch (ignored: Throwable) {
            // Old BedWars1058 without the statistics API, or a holder that is not ready.
            emptyList()
        }
        if (fromHolder.isNotEmpty()) return fromHolder
        return tracker.entries(arena)
    }
}
