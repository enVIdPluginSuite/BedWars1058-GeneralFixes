package dev.etran.generalfixes.oldladders

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Reads and writes the box of a Minecraft collision shape through reflection.
 *
 * Nothing here uses version specific names. Shapes are recognised by the simple name of their class
 * (`AxisAlignedBB` / `AABB` on 1.9 - 1.12, `VoxelShape` on 1.13+) and their numbers by field type, because Spigot's
 * obfuscated field names change with every version.
 *
 * A box is always exposed as `[minX, minY, minZ, maxX, maxY, maxZ]` in the order the shape stores its axes.
 */
interface ShapeAccess {
    fun read(): DoubleArray

    /** Writes the box into the shape (in place) and drops every cache that was derived from the old numbers. */
    fun write(values: DoubleArray)

    companion object {
        /** An accessor for this object, or null if it is not a shape this class can edit. */
        @JvmStatic
        fun of(shape: Any): ShapeAccess? {
            return try {
                val names = hierarchyNames(shape.javaClass)
                when {
                    "AxisAlignedBB" in names || "AABB" in names -> AabbShape.create(shape)
                    "VoxelShape" in names -> ArrayVoxelShape.create(shape)
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }
        }

        /** True if the object is a collision shape of any kind (also those that cannot be edited). */
        @JvmStatic
        fun isShape(obj: Any): Boolean {
            val names = hierarchyNames(obj.javaClass)
            return "AxisAlignedBB" in names || "AABB" in names || "VoxelShape" in names
        }

        /** Like [isShape] but for a declared type (e.g. a method's return type). */
        @JvmStatic
        fun isShapeType(type: Class<*>): Boolean {
            val names = hierarchyNames(type)
            return "AxisAlignedBB" in names || "AABB" in names || "VoxelShape" in names
        }

        internal fun hierarchyNames(type: Class<*>): Set<String> {
            val result = HashSet<String>()
            var c: Class<*>? = type
            while (c != null) {
                result.add(c.simpleName)
                c = c.superclass
            }
            return result
        }
    }
}

/** 1.9 - 1.12: a plain object with six double fields. */
private class AabbShape(private val target: Any, private val fields: List<Field>) : ShapeAccess {

    override fun read(): DoubleArray = DoubleArray(6) { fields[it].getDouble(target) }

    override fun write(values: DoubleArray) {
        for (i in 0 until 6) fields[i].setDouble(target, values[i])
    }

    companion object {
        fun create(shape: Any): ShapeAccess? {
            val doubles = shape.javaClass.declaredFields.filter { it.type == java.lang.Double.TYPE && !Modifier.isStatic(it.modifiers) }
            if (doubles.size < 6) return null
            val six = doubles.take(6)
            six.forEach { it.isAccessible = true }
            return AabbShape(shape, six)
        }
    }
}

/**
 * 1.13+: a VoxelShape whose box is kept in three coordinate lists (fastutil `DoubleList`) of two numbers each.
 * Only shapes with exactly one box are supported, which is what ladders are.
 */
private class ArrayVoxelShape(
    private val target: Any,
    private val lists: List<Any>,
    private val getter: List<Method>,
    private val setter: List<Method>
) : ShapeAccess {

    override fun read(): DoubleArray {
        val out = DoubleArray(6)
        for (axis in 0 until 3) {
            out[axis] = (getter[axis].invoke(lists[axis], 0) as Number).toDouble()
            out[axis + 3] = (getter[axis].invoke(lists[axis], 1) as Number).toDouble()
        }
        return out
    }

    override fun write(values: DoubleArray) {
        for (axis in 0 until 3) {
            setter[axis].invoke(lists[axis], 0, values[axis])
            setter[axis].invoke(lists[axis], 1, values[axis + 3])
        }
        resetCaches(target)
    }

    companion object {
        /** Cache fields Paper adds to VoxelShape that are derived from the coordinates. */
        private val CACHE_FIELDS = setOf("cachedToAABBs", "mergedORCache", "occludesFullBlock", "isFullBlock", "faceShapeClampedCache")

        fun create(shape: Any): ShapeAccess? {
            val listFields = ArrayList<Field>()
            var c: Class<*>? = shape.javaClass
            while (c != null && listFields.size < 3) {
                for (f in c.declaredFields) {
                    if (!Modifier.isStatic(f.modifiers) && f.type.simpleName == "DoubleList") listFields.add(f)
                }
                c = c.superclass
            }
            if (listFields.size < 3) return null

            val lists = ArrayList<Any>()
            val getters = ArrayList<Method>()
            val setters = ArrayList<Method>()
            for (f in listFields.take(3)) {
                f.isAccessible = true
                val list = f.get(shape) ?: return null
                val type = f.type // DoubleList interface, public
                val get = type.methods.firstOrNull { it.name == "getDouble" && it.parameterCount == 1 && it.parameterTypes[0] == Integer.TYPE }
                    ?: type.methods.firstOrNull { it.name == "get" && it.parameterCount == 1 && it.parameterTypes[0] == Integer.TYPE }
                    ?: return null
                val set = type.methods.firstOrNull {
                    it.name == "set" && it.parameterCount == 2 &&
                        it.parameterTypes[0] == Integer.TYPE && it.parameterTypes[1] == java.lang.Double.TYPE
                } ?: return null
                get.isAccessible = true
                set.isAccessible = true
                // Exactly one box: two numbers per axis.
                val size = type.methods.firstOrNull { it.name == "size" && it.parameterCount == 0 }?.invoke(list) as? Int ?: return null
                if (size != 2) return null
                lists.add(list)
                getters.add(get)
                setters.add(set)
            }
            return ArrayVoxelShape(shape, lists, getters, setters)
        }

        /**
         * After the numbers changed: forget everything computed from the old ones, then let Paper rebuild its
         * collision cache (single box, bounds, root coordinate arrays). Vanilla/Spigot has nothing of that.
         */
        private fun resetCaches(shape: Any) {
            var c: Class<*>? = shape.javaClass
            while (c != null && c != Any::class.java) {
                for (f in c.declaredFields) {
                    if (Modifier.isStatic(f.modifiers) || f.type.isPrimitive) continue
                    val isFaceArray = f.type.isArray && f.type.componentType.simpleName == "VoxelShape"
                    if (f.name in CACHE_FIELDS || isFaceArray) {
                        try {
                            f.isAccessible = true
                            f.set(shape, null)
                        } catch (_: Throwable) {
                            // best effort
                        }
                    }
                }
                c = c.superclass
            }
            val rebuild = shape.javaClass.methods.firstOrNull { it.name == "moonrise\$initCache" && it.parameterCount == 0 }
            rebuild?.let {
                it.isAccessible = true
                it.invoke(shape)
            }
        }
    }
}

/** Pure maths of the ladder change, separate from reflection so it can be tested. */
object LadderMath {

    /**
     * The thinned box for a ladder-like shape, or null if the box is not a ladder (a full-height, full-width slab
     * hugging one wall) or is already at most [thickness] thick. Keeps the ladder on the wall side.
     */
    @JvmStatic
    fun thin(box: DoubleArray, thickness: Double): DoubleArray? {
        val eps = 1.0E-4
        // Y must be the full block height.
        if (box[1] > eps || box[4] < 1.0 - eps) return null

        // Exactly one horizontal axis (X = 0, Z = 2) is thin, the other one spans the whole block.
        val thinAxis = when {
            isThin(box, 0) && isFull(box, 2) -> 0
            isThin(box, 2) && isFull(box, 0) -> 2
            else -> return null
        }
        val current = box[thinAxis + 3] - box[thinAxis]
        if (current <= thickness + eps) return null

        val result = box.copyOf()
        when {
            box[thinAxis] <= eps -> result[thinAxis + 3] = thickness          // wall at the minimum side
            box[thinAxis + 3] >= 1.0 - eps -> result[thinAxis] = 1.0 - thickness // wall at the maximum side
            else -> return null
        }
        return result
    }

    private fun isThin(box: DoubleArray, axis: Int) = box[axis + 3] - box[axis] in 1.0E-4..0.5
    private fun isFull(box: DoubleArray, axis: Int) = box[axis] <= 1.0E-4 && box[axis + 3] >= 1.0 - 1.0E-4
}
