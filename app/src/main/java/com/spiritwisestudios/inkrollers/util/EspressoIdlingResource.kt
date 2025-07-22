package com.spiritwisestudios.inkrollers.util

import androidx.test.espresso.idling.CountingIdlingResource

/**
 * Utility class for managing Espresso IdlingResource to synchronize UI tests
 * with long-running operations like network calls and profile loading.
 */
object EspressoIdlingResource {
    private const val RESOURCE = "GLOBAL"

    @JvmField
    val countingIdlingResource = CountingIdlingResource(RESOURCE)

    fun increment() {
        countingIdlingResource.increment()
    }

    fun decrement() {
        if (!countingIdlingResource.isIdleNow) {
            countingIdlingResource.decrement()
        }
    }
} 