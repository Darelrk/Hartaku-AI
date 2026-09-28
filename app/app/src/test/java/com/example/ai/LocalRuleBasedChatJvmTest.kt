package com.example.ai

import com.example.data.FakeTransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM smoke test (no Robolectric) untuk [LocalRuleBasedChat].
 */
class LocalRuleBasedChatJvmTest {

    @Test
    fun `bantu returns non-blank help text`() = runBlocking {
        val chat = LocalRuleBasedChat(FakeTransactionRepository())
        val result = chat.processQuery("bantu")
        assertTrue("bantu should return non-blank, got: '$result'", result.isNotBlank())
    }

    @Test
    fun `halo returns greeting containing Halo`() = runBlocking {
        val chat = LocalRuleBasedChat(FakeTransactionRepository())
        val result = chat.processQuery("halo")
        assertTrue("halo should return greeting, got: '$result'", result.contains("Halo", ignoreCase = true))
    }

    @Test
    fun `processQuery with empty history works`() = runBlocking {
        val chat = LocalRuleBasedChat(FakeTransactionRepository())
        val result = chat.processQuery("halo", emptyList())
        assertTrue(result.isNotBlank())
    }
}
