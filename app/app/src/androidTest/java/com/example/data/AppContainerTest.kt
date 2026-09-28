package com.example.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test untuk [AppContainer] — memerlukan native libs
 * (SQLCipher, ObjectBox) yang hanya tersedia di device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class AppContainerTest {

    @Test
    fun container_initializesAllRepositories() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = AppContainer(context)
        assertNotNull("transactionRepository", container.transactionRepository)
        assertNotNull("budgetRepository", container.budgetRepository)
        assertNotNull("categoryRepository", container.categoryRepository)
        assertNotNull("billRepository", container.billRepository)
    }

    @Test
    fun singleton_getInstance_returnsSameInstance() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val a = AppContainer.getInstance(context)
        val b = AppContainer.getInstance(context)
        assertEquals("getInstance must return the same singleton", a, b)
    }
}
