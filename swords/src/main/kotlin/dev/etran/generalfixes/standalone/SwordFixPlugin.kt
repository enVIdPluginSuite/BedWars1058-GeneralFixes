package dev.etran.generalfixes.standalone

import dev.etran.generalfixes.swords.SwordReplacementFix
import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin

/** Standalone plugin containing only the swords fix. Its options sit at the root of config.yml. */
class SwordFixPlugin : FixPlugin() {
    override val adminPermission = "bwfixes.swords.admin"

    override fun createFixes(): List<Fix> = listOf(SwordReplacementFix(this, ""))
}
