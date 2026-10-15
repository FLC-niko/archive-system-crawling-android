package com.topviewclub.crawling.wechat.official

/**
 * Tracks inspected viewports after scrolling. The caller supplies a fingerprint
 * without status bars or other changing decoration, and a generation advanced
 * only when a scroll is requested.
 *
 * A stalled viewport gets one chance to re-enter the account. A second stall
 * fails explicitly; reaching the same viewport never proves task completion.
 */
internal class OfficialListProgressGuard(
    private val maxUnchangedScrolls: Int = 5,
) {
    enum class Decision {
        CONTINUE,
        REENTER,
        FAIL,
    }

    init {
        require(maxUnchangedScrolls > 0)
    }

    private var lastFingerprint: String? = null
    private var lastScrollGeneration: Int? = null
    private var unchangedScrolls = 0
    private var reentryAttempted = false
    private var failed = false

    @Synchronized
    fun observe(fingerprint: String, scrollGeneration: Int): Decision {
        if (failed) return Decision.FAIL
        if (fingerprint.isBlank()) return Decision.CONTINUE

        // Repeated accessibility events and stale OCR callbacks are not scrolls.
        val previousGeneration = lastScrollGeneration
        if (previousGeneration != null && scrollGeneration <= previousGeneration) {
            return Decision.CONTINUE
        }
        lastScrollGeneration = scrollGeneration

        if (fingerprint != lastFingerprint) {
            lastFingerprint = fingerprint
            unchangedScrolls = 0
            return Decision.CONTINUE
        }

        unchangedScrolls++
        if (unchangedScrolls < maxUnchangedScrolls) return Decision.CONTINUE

        if (reentryAttempted) {
            failed = true
            return Decision.FAIL
        }

        reentryAttempted = true
        // The first stable viewport after re-entry establishes a fresh baseline.
        // Its scroll sequence may restart, while the one-reentry budget remains.
        lastFingerprint = null
        lastScrollGeneration = null
        unchangedScrolls = 0
        return Decision.REENTER
    }

    /** Starts a new task, including its independent recovery budget. */
    @Synchronized
    fun reset() {
        lastFingerprint = null
        lastScrollGeneration = null
        unchangedScrolls = 0
        reentryAttempted = false
        failed = false
    }
}
