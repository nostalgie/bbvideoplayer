package com.dima.bbvideoplayer.data

/**
 * Thread-safe cache with per-entry TTL and a hard size cap.
 * Inserting beyond [maxEntries] evicts the oldest entries first, so long
 * kiosk sessions cannot grow the cache unbounded.
 */
class ExpiringCache<K : Any, V : Any>(
    private val ttlMs: Long,
    private val maxEntries: Int,
    private val now: () -> Long = System::currentTimeMillis
) {
    private class Entry<V>(val value: V, val at: Long)

    private val map = LinkedHashMap<K, Entry<V>>()

    operator fun get(key: K): V? = synchronized(map) {
        val entry = map[key]
        when {
            entry == null -> null
            now() - entry.at >= ttlMs -> {
                map.remove(key)
                null
            }
            else -> entry.value
        }
    }

    fun put(key: K, value: V) {
        synchronized(map) {
            val currentTime = now()
            // Expired entries are dead weight: sweep them while we hold the lock.
            map.entries.removeAll { currentTime - it.value.at >= ttlMs }
            while (map.size >= maxEntries) {
                map.remove(map.keys.first())
            }
            map[key] = Entry(value, currentTime)
        }
    }

    fun clear() {
        synchronized(map) { map.clear() }
    }

    fun size(): Int = synchronized(map) { map.size }
}
