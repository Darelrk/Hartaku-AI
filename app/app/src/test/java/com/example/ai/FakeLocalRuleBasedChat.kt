package com.example.ai

import com.example.data.TransactionRepository

/**
 * Test fake for [LocalRuleBasedChat]. Wraps the real implementation so tests
 * exercise the actual tool-execution + answer-formatting pipeline against
 * a fake [TransactionRepository]. Override [responseToReturn] to skip the
 * real pipeline and return canned output.
 */
class FakeLocalRuleBasedChat(
    transactionRepository: TransactionRepository
) : LocalRuleBasedChat(transactionRepository) {
    var responseToReturn: String? = null
    var callCount = 0

    override suspend fun processQuery(
        userQuery: String,
        history: List<ChatMessageItem>
    ): String {
        callCount++
        return responseToReturn ?: super.processQuery(userQuery, history)
    }
}
