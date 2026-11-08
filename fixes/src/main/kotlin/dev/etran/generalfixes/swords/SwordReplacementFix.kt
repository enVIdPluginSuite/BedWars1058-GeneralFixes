package dev.etran.generalfixes.swords

import com.andrei1058.bedwars.api.events.shop.ShopBuyEvent
import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.inventory.ItemStack

/**
 * Fix 3: bought swords that do not replace the old one.
 *
 * BedWars1058 only removes an older sword when it carries its internal "DEFAULT_ITEM" tag and its damage value is not
 * higher. Swords that lost the tag or are compared wrongly stay in the inventory. This runs right after every shop
 * purchase and removes every sword that is weaker than the best sword the player holds.
 */
class SwordReplacementFix(plugin: FixPlugin, section: String) : Fix(plugin, section) {

    private class Settings(cfg: FileConfiguration, p: String) {
        val enabled = cfg.getBoolean(p + "enabled", true)
        val delay = cfg.getInt(p + "delay-ticks", 1).coerceAtLeast(1).toLong()
        val removeLower = cfg.getBoolean(p + "remove-lower-tier", true)
        val removeDuplicates = cfg.getBoolean(p + "remove-same-tier-duplicates", false)
        val keepSlot = cfg.getBoolean(p + "keep-slot", true)

        /** material prefix (e.g. "WOODEN") -> tier rank, higher is stronger. */
        val tiers: Map<String, Int> = parseTiers(cfg.getList(p + "tiers"))
    }

    @Volatile
    private var settings = Settings(plugin.cfg, prefix)

    override fun load(cfg: FileConfiguration) {
        settings = Settings(cfg, prefix)
    }

    private companion object {
        /** Main inventory + hotbar. Armor and off-hand are never touched. */
        const val STORAGE_SLOTS = 36
        const val SWORD_SUFFIX = "_SWORD"

        val DEFAULT_TIERS = listOf(
            listOf("WOOD", "WOODEN"), listOf("GOLD", "GOLDEN"), listOf("STONE"),
            listOf("COPPER"), listOf("IRON"), listOf("DIAMOND"), listOf("NETHERITE")
        )

        fun parseTiers(raw: List<*>?): Map<String, Int> {
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
    }

    private class Entry(val slot: Int, val tier: Int, val item: ItemStack)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShopBuy(event: ShopBuyEvent) {
        val s = settings
        if (!s.enabled) return
        val id = event.buyer.uniqueId
        // BedWars1058 hands out the item right after this event, so clean up on a later tick.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val player = plugin.server.getPlayer(id)
            if (player != null && player.isOnline) clean(player, settings)
        }, s.delay)
    }

    private fun clean(player: Player, s: Settings) {
        val inventory = player.inventory

        val swords = ArrayList<Entry>()
        for (slot in 0 until STORAGE_SLOTS) {
            val item = inventory.getItem(slot) ?: continue
            val tier = tierOf(item, s)
            if (tier >= 0) swords.add(Entry(slot, tier, item))
        }
        if (swords.size < 2) return

        val best = swords.maxOf { it.tier }
        val remove = ArrayList<Entry>()

        if (s.removeLower) {
            swords.filterTo(remove) { it.tier < best }
        }

        // Prefer the most enchanted sword of the best tier, then the one in the lowest slot.
        val topTier = swords.filter { it.tier == best }
        val kept = topTier.sortedWith(compareByDescending<Entry> { it.item.enchantments.size }.thenBy { it.slot }).first()
        if (s.removeDuplicates) {
            topTier.filterTo(remove) { it !== kept }
        }
        if (remove.isEmpty()) return

        for (entry in remove) inventory.setItem(entry.slot, null)

        if (s.keepSlot) {
            val target = remove.minOf { it.slot }
            if (target != kept.slot && inventory.getItem(target) == null) {
                inventory.setItem(target, kept.item)
                inventory.setItem(kept.slot, null)
            }
        }

        @Suppress("DEPRECATION")
        player.updateInventory()
        plugin.debug("Removed ${remove.size} old sword(s) from ${player.name}")
    }

    /** Tier rank of a sword, or -1 if the item is not a known sword. */
    private fun tierOf(item: ItemStack, s: Settings): Int {
        val name = item.type.name
        if (!name.endsWith(SWORD_SUFFIX)) return -1
        return s.tiers[name.removeSuffix(SWORD_SUFFIX)] ?: -1
    }
}
