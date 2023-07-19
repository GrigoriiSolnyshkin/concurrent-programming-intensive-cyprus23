package day4

import kotlinx.atomicfu.*


// This implementation never stores `null` values.
class DynamicArray<E: Any> {

    inner class Frozen(val e: E)

    private val core = atomic(Core(capacity = 1)) // Do not change the initial capacity

    /**
     * Adds the specified [element] to the end of this array.
     */
    fun addLast(element: E) {
        while (true) {
            val curCore : Core = core.value
            val curSize = curCore.size.value
            val curCapacity = curCore.capacity

            if (curSize < curCapacity) {
                // in this case we definitely know that
                // curCore was actual core at the moment we checked the size
                if (curCore.array[curSize].compareAndSet(null, element)) {
                    curCore.size.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    curCore.size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                // in this case we either need to make a transition
                // or help to make it
                if (curCore.next.value == null) {

                    // this branch is efficiency heuristic
                    val temporalCore = Core(capacity = 2 * curCapacity)
                    curCore.next.compareAndSet(null, temporalCore)
                }

                //extracting new core; now it is definitely not a null
                val newCore = curCore.next.value!!


                //we need to help it
                help(curCore, newCore)
            }
        }
    }


    @Suppress("UNCHECKED_CAST")
    private fun help(curCore: Core, newCore: Core) {

        val curCapacity = curCore.capacity
        while (true) {
            val newSize = newCore.size.value
            if (newSize < curCapacity) {
                val elem = curCore.array[newSize].value

                // need to help when frozen
                if (elem is DynamicArray<*>.Frozen) {
                    newCore.array[newSize].compareAndSet(null, elem.e)
                    newCore.size.compareAndSet(newSize, newSize + 1)
                } else if (curCore.array[newSize].compareAndSet(elem, Frozen(elem as E))) {
                    //move the element
                    newCore.array[newSize].compareAndSet(null, elem)
                    newCore.size.compareAndSet(newSize, newSize + 1)
                }

            } else {
                // we are definitely allowed to make a transition on a newCore
                core.compareAndSet(curCore, newCore)
                return
            }
        }
    }

    /**
     * Puts the specified [element] into the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the size of this array.
     */
    fun set(index: Int, element: E) {
        while (true) {
            val curCore = core.value
            val curSize = curCore.size.value
            require(index < curSize) { "index must be lower than the array size" }
            val elem = curCore.array[index].value
            if (elem is DynamicArray<*>.Frozen)
                help(curCore, curCore.next.value!!)
            else if (curCore.array[index].compareAndSet(elem, element)) return
        }
    }

    /**
     * Returns the element located in the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the size of this array.
     */
    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        while (true) {
            val curCore = core.value
            val curSize = curCore.size.value
            require(index < curSize) { "index must be lower than the array size" }
            val elem = curCore.array[index].value
            if (elem is DynamicArray<*>.Frozen)
                help(curCore, curCore.next.value!!)
            else
                return elem as E
        }
    }

    private class Core(
        val capacity: Int
    ) {
        val array = atomicArrayOfNulls<Any?>(capacity)
        val size = atomic(0)
        val next = atomic<Core?>(null)
    }
}