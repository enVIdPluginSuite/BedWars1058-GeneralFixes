package dev.etran.generalfixes.standalone

import dev.etran.generalfixes.chests.ChestVisibilityFix
import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin

/** Standalone plugin containing only the chests fix. Its options sit at the root of config.yml. */
class ChestFixPlugin : FixPlugin() {
    override val adminPermission = "bwfixes.chests.admin"

    override fun createFixes(): List<Fix> = listOf(ChestVisibilityFix(this, ""))
}
