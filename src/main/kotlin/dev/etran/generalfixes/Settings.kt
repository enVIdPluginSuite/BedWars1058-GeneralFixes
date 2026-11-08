package dev.etran.generalfixes

import org.bukkit.ChatColor
import org.bukkit.configuration.file.FileConfiguration

/** Immutable snapshot of config.yml. A reload simply builds a new snapshot. */
class Settings(cfg: FileConfiguration) {

    val debug = cfg.getBoolean("debug", false)

    val prefix = color(cfg.getString("messages.prefix", ""))
    private val messages = HashMap<String, String>().apply {
        for (key in listOf("reload-success", "reload-failed", "no-permission", "usage")) {
            put(key, color(cfg.getString("messages.$key", key)))
        }
    }

    fun message(key: String): String = prefix + (messages[key] ?: key)

    // --- 1) Halloween cobwebs -------------------------------------------------------------------
    val cobwebEnabled = cfg.getBoolean("fixes.halloween-cobwebs.enabled", true)
    val cobwebOnlySpecial = cfg.getBoolean("fixes.halloween-cobwebs.only-special-webs", true)
    val cobwebRecheckDelay = cfg.getInt("fixes.halloween-cobwebs.recheck-delay-ticks", 1).coerceAtLeast(0).toLong()

    // --- 2) Invisible chests ---------------------------------------------------------------------
    private val chestPath = "fixes.invisible-chests"
    val chestEnabled = cfg.getBoolean("$chestPath.enabled", true)
    val chestOnGameStart = cfg.getBoolean("$chestPath.on-game-start", true)
    val chestOnFirstSpawn = cfg.getBoolean("$chestPath.on-first-spawn", true)
    val chestOnRespawn = cfg.getBoolean("$chestPath.on-respawn", false)
    val chestDelays: List<Long> = cfg.getLongList("$chestPath.delays-ticks")
        .map { it.coerceAtLeast(1L) }
        .ifEmpty { listOf(2L, 20L, 60L) }
    val chestChunkRadius = cfg.getInt("$chestPath.chunk-radius", 8).coerceIn(1, 32)
    val chestTypes: Set<String> = cfg.getStringList("$chestPath.block-types")
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .toSet()
        .ifEmpty { setOf("CHEST", "TRAPPED_CHEST", "ENDER_CHEST") }

    // --- 3) Sword replacement --------------------------------------------------------------------
    private val swordPath = "fixes.sword-replacement"
    val swordEnabled = cfg.getBoolean("$swordPath.enabled", true)
    val swordDelay = cfg.getInt("$swordPath.delay-ticks", 1).coerceAtLeast(1).toLong()
    val swordRemoveLower = cfg.getBoolean("$swordPath.remove-lower-tier", true)
    val swordRemoveDuplicates = cfg.getBoolean("$swordPath.remove-same-tier-duplicates", false)
    val swordKeepSlot = cfg.getBoolean("$swordPath.keep-slot", true)

    /** material prefix (e.g. "WOODEN") -> tier rank, higher is stronger. */
    val swordTiers: Map<String, Int> = parseTiers(cfg.getList("$swordPath.tiers"))

    // --- 4) Pop-up tower language ---------------------------------------------------------------
    private val towerPath = "fixes.pop-up-tower-language"
    val towerEnabled = cfg.getBoolean("$towerPath.enabled", true)
    val towerCategory: String = cfg.getString("$towerPath.category", "utility-category") ?: "utility-category"
    val towerContent: String = cfg.getString("$towerPath.content", "tower") ?: "tower"
    val towerReplacePlaceholders = cfg.getBoolean("$towerPath.replace-placeholders", true)
    val towerPlaceholders: List<String> = cfg.getStringList("$towerPath.placeholders")
        .map { strip(it).lowercase() }
        .ifEmpty { listOf("name not set", "lore not set") }

    class Text(val name: String, val lore: List<String>)

    /** language iso (lower case) -> text. Always contains "default" if configured. */
    val towerTexts: Map<String, Text> = HashMap<String, Text>().apply {
        val section = cfg.getConfigurationSection("$towerPath.translations")
        if (section != null) {
            for (key in section.getKeys(false)) {
                val name = section.getString("$key.name") ?: continue
                put(key.lowercase(), Text(name, section.getStringList("$key.lore")))
            }
        }
        if (!containsKey("default")) {
            put("default", Text("{color}Compact Pop-up Tower", listOf("&7Cost: {cost} {currency}", "", "{quick_buy}", "{buy_status}")))
        }
    }

    fun towerTextFor(iso: String): Text {
        val lower = iso.lowercase()
        return towerTexts[lower]
            ?: towerTexts[lower.substringBefore('_').substringBefore('-')]
            ?: towerTexts.getValue("default")
    }

    companion object {
        fun color(text: String?): String = ChatColor.translateAlternateColorCodes('&', text ?: "")

        fun strip(text: String): String = ChatColor.stripColor(color(text)) ?: text

        private fun parseTiers(raw: List<*>?): Map<String, Int> {
            val result = LinkedHashMap<String, Int>()
            val source: List<*> = if (raw.isNullOrEmpty()) DEFAULT_TIERS else raw
            var rank = 0
            for (entry in source) {
                val names: List<String> = when (entry) {
                    is List<*> -> entry.map { it.toString() }
                    is String -> entry.split(',')
                    else -> emptyList()
                }
                val cleaned = names.map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                if (cleaned.isEmpty()) continue
                cleaned.forEach { result[it] = rank }
                rank++
            }
            return result
        }

        private val DEFAULT_TIERS = listOf(
            listOf("WOOD", "WOODEN"), listOf("GOLD", "GOLDEN"), listOf("STONE"),
            listOf("COPPER"), listOf("IRON"), listOf("DIAMOND"), listOf("NETHERITE")
        )
    }
}
