package dev.etran.generalfixes.standalone

import dev.etran.generalfixes.tower.PopUpTowerLanguageFix
import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin

/** Standalone plugin containing only the tower fix. Its options sit at the root of config.yml. */
class TowerFixPlugin : FixPlugin() {
    override val adminPermission = "bwfixes.tower.admin"

    override fun createFixes(): List<Fix> = listOf(PopUpTowerLanguageFix(this, ""))
}
