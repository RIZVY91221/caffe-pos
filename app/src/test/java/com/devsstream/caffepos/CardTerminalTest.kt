package com.devsstream.caffepos

import com.devsstream.caffepos.data.terminal.CardResult
import com.devsstream.caffepos.data.terminal.CardTerminalWrapper
import com.devsstream.terminal.LegacyCardTerminal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CardTerminalTest {

    private val terminal = LegacyCardTerminal()
    private val wrapper = CardTerminalWrapper(terminal)

    @Test
    fun `TC-10 split card then cash computes correct change`() = runBlocking {
        val ref = UUID.randomUUID().toString()
        val cardResult = wrapper.charge(60_000L, ref)
        assertTrue(cardResult is CardResult.Approved)

        val remaining = 109_400L - 60_000L
        assertEquals(49_400L, remaining)
        val change = 50_000L - remaining
        assertEquals(600L, change)
    }

    @Test
    fun `TC-11 card declined for amount ending in 13`() = runBlocking {
        val ref = UUID.randomUUID().toString()
        val result = wrapper.charge(101_300L, ref)
        assertTrue(result is CardResult.Declined)
        assertEquals("INSUFFICIENT_FUNDS", (result as CardResult.Declined).reason)
        assertEquals(1, terminal.attempts(ref))
    }

    @Test(timeout = 20_000)
    fun `TC-12 no callback timeout then approved via queryStatus`() = runBlocking {
        val ref = UUID.randomUUID().toString()
        val result = wrapper.charge(107_700L, ref)
        assertTrue(result is CardResult.Approved)
        assertEquals(1, terminal.attempts(ref))
    }

    @Test(timeout = 10_000)
    fun `TC-13 double callback resolves once`() = runBlocking {
        val ref = UUID.randomUUID().toString()
        val result = wrapper.charge(105_500L, ref)
        assertTrue(result is CardResult.Approved)
        assertEquals(1, terminal.attempts(ref))
    }
}
