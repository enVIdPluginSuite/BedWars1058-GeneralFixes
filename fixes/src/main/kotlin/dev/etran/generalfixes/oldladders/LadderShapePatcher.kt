package dev.etran.generalfixes.oldladders

import dev.etran.generalfixes.common.FixPlugin
import org.bukkit.Bukkit
import org.bukkit.Material
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/**
 * Makes the ladder's collision box as thin as it was in 1.8.
 *
 * Since 1.9 a ladder is 3/16 of a block thick, in 1.8 it was 2/16. A 1.8 client therefore thinks it can stand where
 * the server says there is a ladder, and keeps getting pushed back while climbing. Thinning the server's ladder to
 * the 1.8 thickness makes both agree. Newer clients keep their thicker box, so they just can't use the extra 1/16
 * sliver, which is harmless.
 *
 * The shapes are edited in place (they are shared by the game and by Paper's collision caches, so replacing them
 * would not reach every user) and the original numbers are kept so [restore] can undo everything.
 */
class LadderShapePatcher(private val plugin: FixPlugin) {

    private class Patched(val access: ShapeAccess, val original: DoubleArray)

    private val patched = ArrayList<Patched>()

    val isApplied: Boolean get() = patched.isNotEmpty()

    /** True if the running server can have this problem at all (1.9 and newer). */
    fun isNeeded(): Boolean = serverMinor() >= 9

    /** @return how many ladder shapes were changed; 0 means nothing could be done. */
    fun apply(thickness: Double): Int {
        restore()
        val shapes = findLadderShapes()
        plugin.debug("Found ${shapes.size} ladder shape object(s)")

        for (shape in shapes) {
            val access = ShapeAccess.of(shape)
            if (access == null) {
                plugin.debug("Cannot edit shape of type ${shape.javaClass.name}")
                continue
            }
            try {
                val original = access.read()
                val thinner = LadderMath.thin(original, thickness) ?: continue
                access.write(thinner)
                patched.add(Patched(access, original))
            } catch (t: Throwable) {
                plugin.debug("Could not edit a ladder shape: $t")
            }
        }
        return patched.size
    }

    /** Puts every changed shape back to its original numbers. */
    fun restore() {
        for (entry in patched) {
            try {
                entry.access.write(entry.original)
            } catch (t: Throwable) {
                plugin.logger.warning("Could not restore a ladder shape: $t")
            }
        }
        patched.clear()
    }

    // -----------------------------------------------------------------------------------------------------------
    // finding the shapes
    // -----------------------------------------------------------------------------------------------------------

    private fun findLadderShapes(): List<Any> {
        val found = IdentityHashMap<Any, Boolean>()
        try {
            collectFromBlockClass(found)
        } catch (t: Throwable) {
            plugin.debug("Static scan failed: $t")
        }
        try {
            collectFromBlockStates(found)
        } catch (t: Throwable) {
            plugin.debug("Block state scan failed: $t")
        }
        return found.keys.toList()
    }

    /** Server version number after "1.": 12 for 1.12.2. Releases from 26.1 on count as very new. */
    private fun serverMinor(): Int {
        val parts = Bukkit.getBukkitVersion().substringBefore('-').split('.')
        val first = parts.getOrNull(0)?.toIntOrNull() ?: return 0
        if (first >= 2) return 100 + first
        return parts.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun craftBukkitPackage(): String = Bukkit.getServer().javaClass.`package`.name

    /** The server's internal ladder block (`BlockLadder` / `LadderBlock`). */
    private fun ladderBlock(): Any? {
        val magic = Class.forName(craftBukkitPackage() + ".util.CraftMagicNumbers")
        val method = magic.getMethod("getBlock", Material::class.java)
        return method.invoke(null, Material.LADDER)
    }

    /**
     * 1.9 - 1.12 keep the ladder boxes in static fields of the block class, newer versions often too (directly or
     * in a map of facing -> shape).
     */
    private fun collectFromBlockClass(found: IdentityHashMap<Any, Boolean>) {
        val block = ladderBlock() ?: return
        var type: Class<*>? = block.javaClass
        while (type != null && type != Any::class.java && type.simpleName != "Block") {
            for (field in type.declaredFields) {
                if (!Modifier.isStatic(field.modifiers)) continue
                field.isAccessible = true
                val value = field.get(null) ?: continue
                addShapes(value, found)
            }
            type = type.superclass
        }
    }

    private fun addShapes(value: Any, found: IdentityHashMap<Any, Boolean>) {
        when {
            ShapeAccess.isShape(value) -> found[value] = true
            value is Map<*, *> -> value.values.forEach { v -> if (v != null && ShapeAccess.isShape(v)) found[v] = true }
            value is Collection<*> -> value.forEach { v -> if (v != null && ShapeAccess.isShape(v)) found[v] = true }
            value.javaClass.isArray && !value.javaClass.componentType.isPrimitive ->
                (value as Array<*>).forEach { v -> if (v != null && ShapeAccess.isShape(v)) found[v] = true }
        }
    }

    /**
     * Newer versions build the shapes inside the block and hand them out per block state. Asking real ladder states
     * for their shape finds the objects the game really uses, wherever they are stored.
     */
    private fun collectFromBlockStates(found: IdentityHashMap<Any, Boolean>) {
        val world = Bukkit.getWorlds().firstOrNull() ?: return
        val level = world.javaClass.getMethod("getHandle").invoke(world) ?: return
        val createBlockData: Method = Bukkit::class.java.getMethod("createBlockData", String::class.java) // 1.13+

        for (facing in listOf("north", "south", "east", "west")) {
            val data = createBlockData.invoke(null, "minecraft:ladder[facing=$facing]") ?: continue
            val state = data.javaClass.getMethod("getState").invoke(data) ?: continue

            for (method in state.javaClass.methods) {
                if (method.parameterCount != 2 || Modifier.isStatic(method.modifiers)) continue
                if (!ShapeAccess.isShapeType(method.returnType)) continue
                val levelType = method.parameterTypes[0]
                val posType = method.parameterTypes[1]
                if (!levelType.isInstance(level)) continue
                val pos = originPosition(posType) ?: continue
                try {
                    method.isAccessible = true
                    val shape = method.invoke(state, level, pos) ?: continue
                    if (ShapeAccess.isShape(shape)) found[shape] = true
                } catch (_: Throwable) {
                    // some shape getters need a loaded chunk or context, ignore those
                }
            }
        }
    }

    /** The block position (0, 0, 0): a static field of the position class that holds an instance of itself. */
    private fun originPosition(posType: Class<*>): Any? {
        for (f in posType.declaredFields) {
            if (Modifier.isStatic(f.modifiers) && f.type == posType) {
                try {
                    f.isAccessible = true
                    f.get(null)?.let { return it }
                } catch (_: Throwable) {
                    // next
                }
            }
        }
        return null
    }
}
