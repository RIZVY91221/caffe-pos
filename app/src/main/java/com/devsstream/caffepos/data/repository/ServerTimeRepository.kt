package com.devsstream.caffepos.data.repository

import com.devsstream.caffepos.data.api.FakeApi
import com.devsstream.caffepos.data.db.ServerTimeDao
import com.devsstream.caffepos.data.db.ServerTimeOffsetEntity
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decision D1: Server time = deviceClock + offsetMs stored in Room.
 * - On each successful API call, we update the offset.
 * - Offline or after restart: use last known offset from Room.
 * - Completely ignores the device clock's absolute value for correctness.
 */
@Singleton
class ServerTimeRepository @Inject constructor(
    private val fakeApi: FakeApi,
    private val serverTimeDao: ServerTimeDao
) {
    companion object {
        private val DHAKA = ZoneId.of("Asia/Dhaka")
    }

    suspend fun refreshOffset() {
        try {
            val response = fakeApi.getCatalog()
            val serverInstant = response.serverTime
            val deviceNowMs = System.currentTimeMillis()
            val offsetMs = serverInstant.toEpochMilli() - deviceNowMs
            serverTimeDao.saveOffset(
                ServerTimeOffsetEntity(
                    offsetMs = offsetMs,
                    fetchedAtMs = deviceNowMs
                )
            )
        } catch (_: Exception) {
            // Offline — keep last offset
        }
    }

    suspend fun getServerTime(): ZonedDateTime {
        val offset = serverTimeDao.getOffset()
        val offsetMs = offset?.offsetMs ?: 150_000L  // fallback: 150s ahead (matches fake API)
        val serverMs = System.currentTimeMillis() + offsetMs
        return Instant.ofEpochMilli(serverMs).atZone(DHAKA)
    }

    suspend fun getServerInstant(): Instant = getServerTime().toInstant()
}
