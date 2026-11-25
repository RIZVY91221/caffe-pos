package com.devsstream.caffepos.data.terminal

import com.devsstream.terminal.LegacyCardTerminal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

sealed class CardResult {
    data class Approved(val authCode: String) : CardResult()
    data class Declined(val reason: String) : CardResult()
    object Unknown : CardResult()
}

/**
 * Kotlin coroutine wrapper around LegacyCardTerminal.
 *
 * Handles:
 * (a) Single callback — resumes with Approved/Declined.
 * (b) Double callback (tail==55) — first callback wins; second is ignored via AtomicBoolean.
 * (c) No callback in 15s (tail==77) — calls queryStatus(ref), returns Approved or Unknown.
 * (d) Coroutine cancellation — if cancelled before callback, we do NOT call charge() again;
 *     the ref is never reused since it was stored in Room before charge() was called.
 */
class CardTerminalWrapper(private val terminal: LegacyCardTerminal) {

    suspend fun charge(amountPoisha: Long, ref: String): CardResult {
        val callbackFired = AtomicBoolean(false)

        val result = withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { cont ->
                terminal.charge(amountPoisha, ref, object : LegacyCardTerminal.Callback {
                    override fun onApproved(authCode: String) {
                        // Guard against double callback (TC-13: tail==55)
                        if (callbackFired.compareAndSet(false, true)) {
                            cont.resume(CardResult.Approved(authCode))
                        }
                    }
                    override fun onDeclined(reason: String) {
                        if (callbackFired.compareAndSet(false, true)) {
                            cont.resume(CardResult.Declined(reason))
                        }
                    }
                })
            }
        }

        // If timeout: query the terminal (TC-12: tail==77)
        return result ?: queryAfterTimeout(ref)
    }

    private fun queryAfterTimeout(ref: String): CardResult {
        return when (terminal.queryStatus(ref)) {
            "APPROVED" -> CardResult.Approved("QUERIED-${ref.takeLast(5)}")
            "DECLINED" -> CardResult.Declined("QUERIED_DECLINED")
            else -> CardResult.Unknown
        }
    }
}
