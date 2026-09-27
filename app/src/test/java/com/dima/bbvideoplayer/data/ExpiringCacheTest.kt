package com.dima.bbvideoplayer.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExpiringCacheTest {

    @Test
    fun valueIsReadableWithinTtl() {
        var t = 0L
        val cache = ExpiringCache<String, Int>(ttlMs = 100, maxEntries = 10) { t }
        cache.put("a", 1)
        assertThat(cache.get("a")).isEqualTo(1)
    }

    @Test
    fun expiredEntryIsDroppedOnRead() {
        var t = 0L
        val cache = ExpiringCache<String, Int>(ttlMs = 100, maxEntries = 10) { t }
        cache.put("a", 1)
        t = 150
        assertThat(cache.get("a")).isNull()
    }

    @Test
    fun insertionBeyondCapEvictsOldest() {
        val cache = ExpiringCache<String, Int>(ttlMs = 1_000, maxEntries = 2) { 0 }
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        assertThat(cache.get("a")).isNull()
        assertThat(cache.get("b")).isEqualTo(2)
        assertThat(cache.get("c")).isEqualTo(3)
        assertThat(cache.size()).isEqualTo(2)
    }

    @Test
    fun clearRemovesEverything() {
        val cache = ExpiringCache<String, Int>(ttlMs = 1_000, maxEntries = 10) { 0 }
        cache.put("a", 1)
        cache.clear()
        assertThat(cache.get("a")).isNull()
        assertThat(cache.size()).isEqualTo(0)
    }
}
