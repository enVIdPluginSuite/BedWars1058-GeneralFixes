package dev.etran.generalfixes.fix

import com.andrei1058.bedwars.api.events.shop.ShopBuyEvent
import dev.etran.generalfixes.GeneralFixesPlugin
import dev.etran.generalfixes.Settings
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

/**
 * Fix 3: bought swords that do not replace the old one.
 *
 * BedWars1058 only removes an older sword when it carries its internal "DEFAULT_ITEM" tag and its damage value is not
 * higher. Swords that lost the tag or are compared wrongly stay in the inventory. This runs right after every shop
 * purchase and removes every sword that is weaker than the best sword the player holds.
 */
class SwordReplacementFix(private val plugin: GeneralFixesPlugin) : Listener {

    private companion object {
        /** Main inventory + hotbar. Armor and off-hand are never touched. */
        const val STORAGE_SLOTS = 36
        const val SWORD_SUFFIX = "_SWORD"
    }

    private class Entry(val slot: Int, val tier: Int, val item: ItemStack)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShopBuy(event: ShopBuyEvent) {
        val settings = plugin.settings
        if (!settings.swordEnabled) return
        val id = event.buyer.uniqueId
        // BedWars1058 hands out the item right after this event, so clean up on a later tick.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val player = plugin.server.getPlayer(id)
            if (player != null && player.isOnline) clean(player, plugin.settings)
        }, settings.swordDelay)
    }

    private fun clean(player: Player, settings: Settings) {
        val inventory = player.inventory

        val swords = ArrayList<Entry>()
        for (slot in 0 until STORAGE_SLOTS) {
            val item = inventory.getItem(slot) ?: continue
            val tier = tierOf(item, settings)
            if (tier >= 0) swords.add(Entry(slot, tier, item))
        }
        if (swords.size < 2) return

        val best = swords.maxOf { it.tier }
        val remove = ArrayList<Entry>()

        if (settings.swordRemoveLower) {
            swords.filterTo(remove) { it.tier < best }
        }

        // Prefer the most enchanted sword of the best tier, then the one in the lowest slot.
        val topTier = swords.filter { it.tier == best }
        val kept = topTier.sortedWith(compareByDescending<Entry> { it.item.enchantments.size }.thenBy { it.slot }).first()
        if (settings.swordRemoveDuplicates) {
            topTier.filterTo(remove) { it !== kept }
        }
        if (remove.isEmpty()) return

        for (entry in remove) inventory.setItem(entry.slot, null)

        if (settings.swordKeepSlot) {
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
    private fun tierOf(item: ItemStack, settings: Settings): Int {
        val name = item.type.name
        if (!name.endsWith(SWORD_SUFFIX)) return -1
        return settings.swordTiers[name.removeSuffix(SWORD_SUFFIX)] ?: -1
    }
}
