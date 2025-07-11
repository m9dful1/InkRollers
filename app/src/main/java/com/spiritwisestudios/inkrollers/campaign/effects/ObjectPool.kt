package com.spiritwisestudios.inkrollers.campaign.effects

import android.graphics.Paint
import android.graphics.RectF
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Generic object pool for reusing objects to reduce garbage collection.
 * Improves performance by avoiding frequent object creation and destruction.
 */
class ObjectPool<T>(
    private val factory: () -> T,
    private val reset: (T) -> Unit,
    private val maxSize: Int = 20
) {
    private val pool = ConcurrentLinkedQueue<T>()
    
    /**
     * Gets an object from the pool or creates a new one if pool is empty.
     */
    fun acquire(): T {
        return pool.poll() ?: factory()
    }
    
    /**
     * Returns an object to the pool for reuse.
     * Resets the object state before returning it.
     */
    fun release(obj: T) {
        if (pool.size < maxSize) {
            reset(obj)
            pool.offer(obj)
        }
    }
    
    /**
     * Clears all objects from the pool.
     */
    fun clear() {
        pool.clear()
    }
    
    /**
     * Gets the current size of the pool.
     */
    fun size(): Int = pool.size
}

/**
 * Predefined object pools for common campaign effect objects.
 */
object CampaignObjectPools {
    
    /**
     * Pool for Paint objects used in visual effects.
     */
    val paintPool = ObjectPool<Paint>(
        factory = { Paint() },
        reset = { paint ->
            paint.reset()
            paint.isAntiAlias = true
        },
        maxSize = 10
    )
    
    /**
     * Pool for RectF objects used for effect bounds.
     */
    val rectPool = ObjectPool<RectF>(
        factory = { RectF() },
        reset = { rect ->
            rect.setEmpty()
        },
        maxSize = 15
    )
    
    /**
     * Pool for Float arrays used for effect calculations.
     */
    val floatArrayPool = ObjectPool<FloatArray>(
        factory = { FloatArray(4) },
        reset = { array ->
            array.fill(0f)
        },
        maxSize = 8
    )
    
    /**
     * Clears all object pools.
     */
    fun clearAll() {
        paintPool.clear()
        rectPool.clear()
        floatArrayPool.clear()
    }
} 