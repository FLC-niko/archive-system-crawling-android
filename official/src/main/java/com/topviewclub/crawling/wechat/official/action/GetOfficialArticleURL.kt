package com.topviewclub.crawling.wechat.official.action

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.wechat.official.OfficialIMMActivityCompat

class GetOfficialArticleURL : Action {

    companion object {
        const val EXTRA_TASK_GENERATION = "official_url_task_generation"
        private const val CAPTURE_TIMEOUT_MS = 30_000L
        private val captureState = OfficialUrlCaptureState()

        fun recordActivityResult(generation: Long, validUrl: Boolean): Boolean =
            captureState.complete(generation, OfficialUrlCaptureResult(validUrl))
    }

    override val actionName: String = "GetOfficialArticleURL"

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var activeGeneration: Long? = null
    private var timeoutRunnable: Runnable? = null

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val generation = service.currentTaskGeneration()
        val result = captureState.takeCompleted(generation)
        if (result != null) {
            cancelTimeout()
            activeGeneration = null
            if (result.timedOut) {
                throw ActionException("OFFICIAL_ARTICLE_URL_CAPTURE_TIMEOUT")
            }
            if (!result.validUrl) {
                throw ActionException("OFFICIAL_ARTICLE_URL_CAPTURE_INVALID")
            }
            return "BackToOfficialArticleList"
        }

        if (activeGeneration != generation) {
            cancelTimeout()
            if (!captureState.begin(generation)) return actionName
            activeGeneration = generation
            val timeout = Runnable {
                if (service.isTaskGenerationActive(generation) &&
                    captureState.complete(
                        generation,
                        OfficialUrlCaptureResult(validUrl = false, timedOut = true),
                    )
                ) {
                    service.resumeCurrentAction(generation)
                }
            }
            timeoutRunnable = timeout
            timeoutHandler.postDelayed(timeout, CAPTURE_TIMEOUT_MS)
            service.startActivity(
                Intent(service, OfficialIMMActivityCompat::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(EXTRA_TASK_GENERATION, generation)
                }
            )
        }
        return actionName
    }

    override fun reset() {
        cancelTimeout()
        activeGeneration = null
        captureState.reset()
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let(timeoutHandler::removeCallbacks)
        timeoutRunnable = null
    }

}
