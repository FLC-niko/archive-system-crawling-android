package com.topviewclub.crawling.wechat.official.action

import java.net.URI

internal data class OfficialUrlCaptureResult(
    val validUrl: Boolean,
    val timedOut: Boolean = false,
)

/** Associates clipboard results with the task that opened the capture activity. */
internal class OfficialUrlCaptureState {
    private var activeGeneration: Long? = null
    private var completedGeneration: Long? = null
    private var completedResult: OfficialUrlCaptureResult? = null

    @Synchronized
    fun begin(generation: Long): Boolean {
        if (activeGeneration == generation || completedGeneration == generation) return false
        activeGeneration = generation
        completedGeneration = null
        completedResult = null
        return true
    }

    @Synchronized
    fun complete(generation: Long, result: OfficialUrlCaptureResult): Boolean {
        if (activeGeneration != generation) return false
        activeGeneration = null
        completedGeneration = generation
        completedResult = result
        return true
    }

    @Synchronized
    fun takeCompleted(generation: Long): OfficialUrlCaptureResult? {
        if (completedGeneration != generation) return null
        val result = completedResult
        completedGeneration = null
        completedResult = null
        return result
    }

    @Synchronized
    fun isActive(generation: Long): Boolean = activeGeneration == generation

    @Synchronized
    fun reset() {
        activeGeneration = null
        completedGeneration = null
        completedResult = null
    }
}

internal fun isValidOfficialArticleUrl(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    uri.scheme.let { it.equals("https", ignoreCase = true) || it.equals("http", ignoreCase = true) } &&
        uri.host.equals("mp.weixin.qq.com", ignoreCase = true) &&
        !uri.path.isNullOrBlank()
}.getOrDefault(false)

