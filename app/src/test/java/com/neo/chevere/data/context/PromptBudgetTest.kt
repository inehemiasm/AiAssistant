package com.neo.chevere.data.context

import org.junit.Assert.*
import org.junit.Test

class PromptBudgetTest {
    @Test fun longHistoryShrinksButCurrentRequestAndPolicyStayWhole() {
        val current = "CURRENT REQUEST: retain this whole question, including its final instruction."
        val result = PromptBudget.compose("Important policy", current, "old history\n".repeat(3000) + "latest fact", "gemma.litertlm", false) as PromptBudget.Result.Fits
        assertTrue(result.compacted)
        assertTrue(result.prompt.startsWith("Important policy"))
        assertTrue(result.prompt.endsWith(current))
        assertTrue(result.prompt.contains("latest fact"))
        assertTrue(PromptBudget.estimateTokens(result.prompt) <= PromptBudget.inputLimit("gemma.litertlm", false))
    }

    @Test fun oversizedCurrentRequestIsRejectedRatherThanSilentlyTruncated() {
        assertEquals(PromptBudget.Result.TooLarge, PromptBudget.compose("Policy", "x".repeat(20_000), "history", "gemma.litertlm", false))
    }

    @Test fun multilingualAndImageInputsReserveMoreCapacity() {
        assertTrue(PromptBudget.estimateTokens("漢字".repeat(100)) > PromptBudget.estimateTokens("ab".repeat(100)))
        assertTrue(PromptBudget.inputLimit("gemma.litertlm", true) < PromptBudget.inputLimit("gemma.litertlm", false))
        assertTrue(PromptBudget.inputLimit("Spark-X2.6-4B_int8.litertlm", false) < PromptBudget.inputLimit("gemma.litertlm", false))
    }
}
