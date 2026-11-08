package dev.etran.generalfixes.standalone

import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin
import dev.etran.generalfixes.winfix.WinFix

/** Standalone plugin containing only the end-of-game sidebar fix. Its options sit at the root of config.yml. */
class WinFixPlugin : FixPlugin() {
    override val adminPermission = "bwfixes.winfix.admin"

    override fun createFixes(): List<Fix> = listOf(WinFix(this, ""))
}
