package dev.etran.generalfixes.oldladders

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Finds out which Minecraft version a player's client uses, through ViaVersion or ProtocolSupport.
 * Both are reached by reflection so neither has to be installed. Everything returns `null` when unknown.
 */
class ClientVersions {

    /** True if a plugin that can tell the client version is installed and enabled. */
    fun isAvailable(): Boolean = isEnabled("ViaVersion") || isEnabled("ProtocolSupport")

    /** Protocol number of the player's client (e.g. 47 for 1.8, 393 for 1.13), or null if unknown. */
    fun protocolOf(player: Player): Int? {
        if (isEnabled("ViaVersion")) {
            viaProtocol(player.uniqueId)?.let { return it }
        }
        if (isEnabled("ProtocolSupport")) {
            protocolSupportProtocol(player)?.let { return it }
        }
        return null
    }

    private fun isEnabled(name: String): Boolean = Bukkit.getPluginManager().isPluginEnabled(name)

    private fun viaProtocol(id: UUID): Int? {
        return try {
            val plugin = Bukkit.getPluginManager().getPlugin("ViaVersion") ?: return null
            val loader = plugin.javaClass.classLoader
            val viaClass = loadFirst(loader, "com.viaversion.viaversion.api.Via", "us.myles.ViaVersion.api.Via") ?: return null
            val api = viaClass.getMethod("getAPI").invoke(null) ?: return null
            // Call through the public API interface, the implementation class is not public.
            val apiType = viaClass.getMethod("getAPI").returnType
            val method = try {
                apiType.getMethod("getPlayerVersion", UUID::class.java)
            } catch (_: NoSuchMethodException) {
                apiType.getMethod("getPlayerVersion", Any::class.java)
            }
            (method.invoke(api, id) as? Number)?.toInt()?.takeIf { it > 0 }
        } catch (_: Throwable) {
            null
        }
    }

    private fun protocolSupportProtocol(player: Player): Int? {
        return try {
            val plugin = Bukkit.getPluginManager().getPlugin("ProtocolSupport") ?: return null
            val apiClass = plugin.javaClass.classLoader.loadClass("protocolsupport.api.ProtocolSupportAPI")
            val version = apiClass.getMethod("getProtocolVersion", Player::class.java).invoke(null, player) ?: return null
            (version.javaClass.getMethod("getId").invoke(version) as? Number)?.toInt()?.takeIf { it > 0 }
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadFirst(loader: ClassLoader, vararg names: String): Class<*>? {
        for (name in names) {
            try {
                return loader.loadClass(name)
            } catch (_: ClassNotFoundException) {
                // try the next one
            }
        }
        return null
    }
}
