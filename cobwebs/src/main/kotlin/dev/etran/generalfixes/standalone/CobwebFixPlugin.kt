package dev.etran.generalfixes.standalone

import dev.etran.generalfixes.cobwebs.HalloweenCobwebFix
import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin

/** Standalone plugin containing only the cobwebs fix. Its options sit at the root of config.yml. */
class CobwebFixPlugin : FixPlugin() {
    override val adminPermission = "bwfixes.cobwebs.admin"

    override fun createFixes(): List<Fix> = listOf(HalloweenCobwebFix(this, ""))
}
