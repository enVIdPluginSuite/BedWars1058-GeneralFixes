package dev.etran.generalfixes.winfix

import org.bukkit.configuration.file.FileConfiguration

/** Typed view over the win-fix options. [p] is the config path prefix ("" or "fixes.end-game-sidebar."). */
data class WinSettings(
    val enabled: Boolean = true,
    val orderBy: String = "",
    val hideZeroValues: Boolean = false,
    val expandRows: Boolean = true,
    val maxSidebarLines: Int = 15,
    val refreshDelayTicks: Long = 10L,
) {
    companion object {
        fun from(cfg: FileConfiguration, p: String): WinSettings = WinSettings(
            enabled = cfg.getBoolean(p + "enabled", true),
            orderBy = cfg.getString(p + "order-by", "")?.trim().orEmpty(),
            hideZeroValues = cfg.getBoolean(p + "hide-zero-values", false),
            expandRows = cfg.getBoolean(p + "expand-rows", true),
            maxSidebarLines = cfg.getInt(p + "max-sidebar-lines", 15).coerceIn(1, 40),
            refreshDelayTicks = cfg.getLong(p + "refresh-delay-ticks", 10L).coerceAtLeast(1L),
        )
    }
}
