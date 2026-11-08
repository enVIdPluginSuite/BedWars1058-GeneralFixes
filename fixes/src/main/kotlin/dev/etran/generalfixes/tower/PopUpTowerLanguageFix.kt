package dev.etran.generalfixes.tower

import com.andrei1058.bedwars.api.language.Language
import com.andrei1058.bedwars.api.language.Messages
import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin
import dev.etran.generalfixes.common.Text
import org.bukkit.configuration.file.FileConfiguration

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
class PopUpTowerLanguageFix(plugin: FixPlugin, section: String) : Fix(plugin, section) {

    private class Entry(val name: String, val lore: List<String>)

    private class Settings(cfg: FileConfiguration, p: String) {
        val enabled = cfg.getBoolean(p + "enabled", true)
        val category: String = cfg.getString(p + "category", "utility-category") ?: "utility-category"
        val content: String = cfg.getString(p + "content", "tower") ?: "tower"
        val replacePlaceholders = cfg.getBoolean(p + "replace-placeholders", true)
        val placeholders: List<String> = cfg.getStringList(p + "placeholders")
            .map { Text.strip(it).trim().lowercase() }
            .ifEmpty { listOf("name not set", "lore not set") }

        /** language iso (lower case) -> text. Always contains "default". */
        val texts: Map<String, Entry> = HashMap<String, Entry>().apply {
            val section = cfg.getConfigurationSection(p + "translations")
            if (section != null) {
                for (key in section.getKeys(false)) {
                    val name = section.getString("$key.name") ?: continue
                    put(key.lowercase(), Entry(name, section.getStringList("$key.lore")))
                }
            }
            if (!containsKey("default")) {
                put("default", Entry("{color}Compact Pop-up Tower", listOf("&7Cost: {cost} {currency}", "", "{quick_buy}", "{buy_status}")))
            }
        }

        fun textFor(iso: String): Entry {
            val lower = iso.lowercase()
            return texts[lower]
                ?: texts[lower.substringBefore('_').substringBefore('-')]
                ?: texts.getValue("default")
        }
    }

    @Volatile
    private var settings = Settings(plugin.cfg, prefix)

    override fun load(cfg: FileConfiguration) {
        settings = Settings(cfg, prefix)
    }

    override fun onStarted() = applyLanguages(settings)

    private fun applyLanguages(s: Settings) {
        if (!s.enabled) return

        val namePath = Messages.SHOP_CONTENT_TIER_ITEM_NAME
            .replace("%category%", s.category).replace("%content%", s.content)
        val lorePath = Messages.SHOP_CONTENT_TIER_ITEM_LORE
            .replace("%category%", s.category).replace("%content%", s.content)

        var languages = 0
        var changedFiles = 0
        for (language in Language.getLanguages()) {
            val yml = language?.yml ?: continue
            languages++
            val text = s.textFor(language.iso)
            var changed = false

            if (needsValue(yml.getString(namePath), s)) {
                yml.set(namePath, text.name)
                changed = true
            }
            if (text.lore.isNotEmpty() && needsLore(yml.getStringList(lorePath), s)) {
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

    private fun isPlaceholder(line: String, s: Settings): Boolean {
        if (!s.replacePlaceholders) return false
        return Text.strip(line).trim().lowercase() in s.placeholders
    }

    private fun needsValue(current: String?, s: Settings): Boolean =
        current == null || current.isBlank() || isPlaceholder(current, s)

    private fun needsLore(current: List<String>, s: Settings): Boolean =
        current.isEmpty() || current.all { it.isBlank() || isPlaceholder(it, s) }
}
