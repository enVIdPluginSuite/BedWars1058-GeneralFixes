package dev.etran.generalfixes.standalone

import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin
import dev.etran.generalfixes.oldladders.OldLadderFix

/** Standalone plugin containing only the old-client ladder fix. Its options sit at the root of config.yml. */
class OldLadderFixPlugin : FixPlugin() {
    override val adminPermission = "bwfixes.oldladders.admin"

    override fun createFixes(): List<Fix> = listOf(OldLadderFix(this, ""))
}
