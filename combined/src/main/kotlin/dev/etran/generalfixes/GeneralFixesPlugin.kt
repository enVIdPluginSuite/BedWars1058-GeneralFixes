package dev.etran.generalfixes

import dev.etran.generalfixes.chests.ChestVisibilityFix
import dev.etran.generalfixes.cobwebs.HalloweenCobwebFix
import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin
import dev.etran.generalfixes.oldladders.OldLadderFix
import dev.etran.generalfixes.shopnpcs.ShopNpcFix
import dev.etran.generalfixes.swords.SwordReplacementFix
import dev.etran.generalfixes.tower.PopUpTowerLanguageFix

/** All six fixes in one jar. Each one is switched on/off in config.yml under `fixes.<name>.enabled`. */
class GeneralFixesPlugin : FixPlugin() {
    override val adminPermission = "bwfixes.admin"

    override fun createFixes(): List<Fix> = listOf(
        HalloweenCobwebFix(this, "fixes.halloween-cobwebs"),
        ChestVisibilityFix(this, "fixes.invisible-chests"),
        SwordReplacementFix(this, "fixes.sword-replacement"),
        PopUpTowerLanguageFix(this, "fixes.pop-up-tower-language"),
        ShopNpcFix(this, "fixes.shop-npcs"),
        OldLadderFix(this, "fixes.old-ladders")
    )
}
