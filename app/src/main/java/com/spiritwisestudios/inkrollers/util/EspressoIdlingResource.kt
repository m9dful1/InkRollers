package com.spiritwisestudios.inkrollers.util

import androidx.test.espresso.idling.CountingIdlingResource

/**
 * A simple singleton implementation of an idling resource that can be used to track
 * long-running operations in the application. This is essential for synchronizing
 * Espresso tests with asynchronous tasks like network requests.
 *
 * In a production app, a more sophisticated dependency injection mechanism might be
 * used, but for this project, a singleton is a clean and effective solution.
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