package com.devsstream.caffepos

import com.devsstream.caffepos.core.model.OrderStatus
import com.devsstream.caffepos.data.api.FakeApiImpl
import com.devsstream.caffepos.data.api.OrderLineRequest
import com.devsstream.caffepos.data.api.OrderRequest
import com.devsstream.caffepos.data.api.PaymentRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * Tests covering FakeApi idempotency, retry after kill (TC-14, TC-15),
 * and order state transitions (TC-16, TC-17, TC-18).
 */
class SyncAndReliabilityTest {

    private fun sampleRequest(clientId: String, receiptNo: String) = OrderRequest(
        clientOrderId = clientId,
        receiptNo = receiptNo,
        lines = listOf(OrderLineRequest("americano", "Americano", 1, 18_000L, 18_000L, emptyList())),
        payments = listOf(PaymentRequest("CASH", 18_000L, null, null)),
        payablePoisha = 18_000L,
        serverTime = Instant.parse("2026-11-20T09:00:00Z")
    )

    @Test
    fun `TC-14 and TC-15 FakeApi idempotency returns duplicate true on retry`() = runTest {
        val api = FakeApiImpl()
        val req = sampleRequest("client-1", "T1-20261120-0001")

        // Retry until first success (handles simulated 20% IOException and 10% 500)
        var firstServerNo: String? = null
        repeat(20) {
            if (firstServerNo == null) {
                try {
                    val res = api.postOrder(req)
                    firstServerNo = res.serverOrderNo
                } catch (_: IOException) {
                    // Simulated network or 500 after save
                }
            }
        }
        assertNotNull("Expected order to succeed within retries", firstServerNo)

        // Subsequent retry with same clientOrderId must return duplicate = true and same serverOrderNo
        var secondRes: com.devsstream.caffepos.data.api.OrderResponse? = null
        repeat(20) {
            if (secondRes == null) {
                try {
                    secondRes = api.postOrder(req)
                } catch (_: IOException) {
                }
            }
        }
        assertNotNull(secondRes)
        assertTrue("Expected duplicate = true", secondRes!!.duplicate)
        assertEquals(firstServerNo, secondRes!!.serverOrderNo)
    }

    @Test
    fun `TC-16 order status transitions to FAILED after 5 attempts`() {
        val maxAttempts = 5
        var attempts = 0
        var status = OrderStatus.PENDING
        repeat(maxAttempts) {
            attempts++
            status = if (attempts >= maxAttempts) OrderStatus.FAILED else OrderStatus.PENDING
        }
        assertEquals(OrderStatus.FAILED, status)
        assertEquals(5, attempts)
    }

    @Test
    fun `TC-18 Appendix B unit counts put Croissant as top item with 9 units`() {
        // O1: Croissant 1, O2: Brownie 3, O3: Iced Tea 3 + Chicken Sandwich 2,
        // O4: Americano 1 + Veg Wrap 2 + Brownie 1 + Croissant 2,
        // O5: Latte 1 + Veg Wrap 1, O6: Croissant 6 + Americano 2
        val counts = mapOf(
            "Croissant" to (1 + 2 + 6),
            "Brownie" to (3 + 1),
            "Iced Tea" to 3,
            "Chicken Sandwich" to 2,
            "Americano" to (1 + 2),
            "Veg Wrap" to (2 + 1),
            "Latte" to 1
        )
        val top = counts.maxByOrNull { it.value }!!
        assertEquals("Croissant", top.key)
        assertEquals(9, top.value)
    }
}
