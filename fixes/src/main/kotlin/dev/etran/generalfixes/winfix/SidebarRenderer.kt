package dev.etran.generalfixes.winfix

import com.andrei1058.bedwars.api.BedWars
import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.configuration.ConfigPath
import com.andrei1058.bedwars.api.language.Language
import com.andrei1058.bedwars.api.language.Messages
import org.bukkit.entity.Player

/**
 * Rebuilds the end-of-game ("restarting") sidebar of every player in an arena with the real
 * winner and the real top list, instead of the unreplaced {winnerTeamName} / {topValue} text.
 */
class SidebarRenderer(
    private val api: BedWars,
    private val topStats: TopStatsSource,
    private val settings: () -> WinSettings,
) {
    private val topNames = listOf(
        "{topPlayerName}", "{topPlayerDisplayName}", "{topTeamColor}", "{topTeamName}", "{topValue}",
    )
    private val topValueById = Regex("\\{topValue-([^}]+)}")

    /** @return how many sidebars were rebuilt. */
    fun render(arena: IArena): Int {
        if (arena.status != GameState.restarting) return 0

        val cfg = settings()
        val orderBy = resolveOrderBy(cfg)
        val entries = topStats.entries(arena, orderBy)
            .let { list -> if (cfg.hideZeroValues) list.filter { it.numeric > 0L } else list }

        val viewers = LinkedHashSet<Player>()
        viewers.addAll(arena.players)
        viewers.addAll(arena.spectators)

        var rebuilt = 0
        for (viewer in viewers) {
            if (!viewer.isOnline) continue
            val sidebar = api.scoreboardManager.getSidebar(viewer) ?: continue

            val raw = Language.getScoreboard(viewer, sidebarPath(arena, viewer), defaultPath(arena, viewer))
            if (raw == null || raw.isEmpty()) continue

            val language = Language.getPlayerLanguage(viewer)
            val title = splitTitle(raw[0])
            val lines = renderLines(arena, language, raw.subList(1, raw.size), entries, orderBy, cfg)
            sidebar.setContent(title, lines, arena)
            rebuilt++
        }
        return rebuilt
    }

    internal fun renderLines(
        arena: IArena,
        language: Language?,
        template: List<String>,
        entries: List<TopEntry>,
        orderBy: String,
        cfg: WinSettings,
    ): List<String> {
        val out = ArrayList<String>(template.size)
        var rank = 0
        var lastTopTemplate: String? = null
        var lastTopIndex = -1

        for (templateLine in template) {
            val line = replaceWinner(templateLine, arena, language)
            if (!isTopLine(line)) {
                out.add(line)
                continue
            }
            // Fewer players than rows in the language file: drop the unused row entirely.
            if (rank >= entries.size) continue

            out.add(applyEntry(line, entries[rank++], language, orderBy))
            lastTopTemplate = line
            lastTopIndex = out.size - 1
        }

        // More players than rows in the language file: repeat the last row while there is room.
        if (cfg.expandRows && lastTopTemplate != null) {
            var insertAt = lastTopIndex + 1
            while (rank < entries.size && out.size < cfg.maxSidebarLines) {
                out.add(insertAt++, applyEntry(lastTopTemplate, entries[rank++], language, orderBy))
            }
        }
        return out
    }

    private fun replaceWinner(line: String, arena: IArena, language: Language?): String {
        if (!line.contains("{winner")) return line
        val winner = arena.winner
        return if (winner != null) {
            val name = winner.getDisplayName(language)
            val color = winner.color.chat().toString()
            line.replace("{winnerTeamName}", name)
                .replace("{winnerTeamLetter}", color + name.take(1))
                .replace("{winnerTeamColor}", color)
        } else {
            // Nobody won (e.g. everybody left): show the language's "nobody" text, no color.
            line.replace("{winnerTeamName}", language?.m(Messages.MEANING_NOBODY).orEmpty())
                .replace("{winnerTeamLetter}", "")
                .replace("{winnerTeamColor}", "")
        }
    }

    private fun isTopLine(line: String): Boolean =
        topNames.any { line.contains(it) } || topValueById.containsMatchIn(line)

    private fun applyEntry(line: String, entry: TopEntry, language: Language?, orderBy: String): String {
        val team = entry.team
        val replaced = line
            .replace("{topPlayerName}", entry.username)
            .replace("{topPlayerDisplayName}", entry.displayName)
            .replace("{topTeamColor}", team?.color?.chat()?.toString().orEmpty())
            .replace("{topTeamName}", team?.getDisplayName(language).orEmpty())
            .replace("{topValue}", entry.value(orderBy, language))
        return topValueById.replace(replaced) { match -> entry.value(match.groupValues[1], language) }
    }

    private fun resolveOrderBy(cfg: WinSettings): String {
        if (cfg.orderBy.isNotEmpty()) return cfg.orderBy
        val fromBedWars = try {
            api.configs.mainConfig.getString(ConfigPath.GENERAL_GAME_END_SB_TOP_STATISTIC)
        } catch (ignored: Throwable) {
            null
        }
        return if (fromBedWars.isNullOrBlank()) "kills" else fromBedWars
    }

    // --- Same sidebar selection logic BedWars1058 uses for the "restarting" state -------------

    private fun role(arena: IArena, viewer: Player): String {
        val team = arena.getTeam(viewer)
        val exTeam = if (team == null) arena.getExTeam(viewer.uniqueId) else null
        val winner = arena.winner
        return when {
            team == null && exTeam == null -> "spectator"
            team == null && exTeam == winner -> "winner-eliminated"
            exTeam == null && team == winner -> "winner-alive"
            else -> "loser"
        }
    }

    private fun sidebarPath(arena: IArena, viewer: Player) = "sidebar.${arena.group}.restarting.${role(arena, viewer)}"

    private fun defaultPath(arena: IArena, viewer: Player): String = when (role(arena, viewer)) {
        "spectator" -> Messages.SCOREBOARD_DEFAULT_RESTARTING_SPEC
        "winner-eliminated" -> Messages.SCOREBOARD_DEFAULT_RESTARTING_WIN2
        "winner-alive" -> Messages.SCOREBOARD_DEFAULT_RESTARTING_WIN1
        else -> Messages.SCOREBOARD_DEFAULT_RESTARTING_LOSER
    }

    /** BedWars1058 stores the (animated) title as the first line, frames separated by commas. */
    private fun splitTitle(first: String): List<String> {
        val parts = first.split(",").dropLastWhile { it.isEmpty() }
        return if (parts.isEmpty()) listOf(first) else parts
    }
}
