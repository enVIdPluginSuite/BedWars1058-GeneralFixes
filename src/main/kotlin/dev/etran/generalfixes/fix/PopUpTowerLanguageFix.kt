package dev.etran.generalfixes.fix

import com.andrei1058.bedwars.api.language.Language
import com.andrei1058.bedwars.api.language.Messages
import dev.etran.generalfixes.GeneralFixesPlugin
import dev.etran.generalfixes.Settings

/**
 * Fix 4: the pop-up tower has no language entry.
 *
 * Cause: the shop item is stored as "tower" in shop.yml, so BedWars1058 looks for
 * `shop-items-messages.utility-category.content-item-tower-name` / `-lore`. The built-in languages register their
 * text under the key "Compact Pop-up Tower" instead, so the lookup misses and the language files get filled with
 * "Name not set" / "Lore not set".
 *
 * Fix: write the correct keys into every loaded language (and replace the placeholders). Real translations that a
 * server owner has already put in are never overwritten.
 */
class PopUpTowerLanguageFix(private val plugin: GeneralFixesPlugin) {

    fun apply() {
        val settings = plugin.settings
        if (!settings.towerEnabled) return

        val namePath = Messages.SHOP_CONTENT_TIER_ITEM_NAME
            .replace("%category%", settings.towerCategory).replace("%content%", settings.towerContent)
        val lorePath = Messages.SHOP_CONTENT_TIER_ITEM_LORE
            .replace("%category%", settings.towerCategory).replace("%content%", settings.towerContent)

        var languages = 0
        var changedFiles = 0
        for (language in Language.getLanguages()) {
            val yml = language?.yml ?: continue
            languages++
            val text = settings.towerTextFor(language.iso)
            var changed = false

            if (needsValue(yml.getString(namePath), settings)) {
                yml.set(namePath, text.name)
                changed = true
            }
            if (text.lore.isNotEmpty() && needsLore(yml.getStringList(lorePath), settings)) {
                yml.set(lorePath, text.lore)
                changed = true
            }
            if (changed) {
                language.save()
                changedFiles++
            }
        }
        plugin.logger.info("Pop-up tower language: checked $languages language(s), updated $changedFiles.")
    }

    private fun isPlaceholder(line: String, settings: Settings): Boolean {
        if (!settings.towerReplacePlaceholders) return false
        return Settings.strip(line).trim().lowercase() in settings.towerPlaceholders
    }

    private fun needsValue(current: String?, settings: Settings): Boolean =
        current == null || current.isBlank() || isPlaceholder(current, settings)

    private fun needsLore(current: List<String>, settings: Settings): Boolean =
        current.isEmpty() || current.all { it.isBlank() || isPlaceholder(it, settings) }
}
