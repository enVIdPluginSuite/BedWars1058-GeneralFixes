package dev.etran.generalfixes.standalone

import dev.etran.generalfixes.common.Fix
import dev.etran.generalfixes.common.FixPlugin
import dev.etran.generalfixes.shopnpcs.ShopNpcFix

/** Standalone plugin containing only the shop villager fix. Its options sit at the root of config.yml. */
class ShopNpcFixPlugin : FixPlugin() {
    override val adminPermission = "bwfixes.shopnpcs.admin"

    override fun createFixes(): List<Fix> = listOf(ShopNpcFix(this, ""))
}
