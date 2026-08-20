package com.topviewclub.crawling.wechat.official.action

import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.wechat.official.OfficialIMMActivityCompat

class GetOfficialArticleURL : Action {

    companion object {
        var isIMMActivityActive = false
        var isIMMActivityDestroyed = false
    }

    override val actionName: String = "GetOfficialArticleURL"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        service.resumeServiceDelay(event, 0L)
        if (isIMMActivityDestroyed) {
            isIMMActivityActive = false
            isIMMActivityDestroyed = false
            return "BackToOfficialArticleList"
        }
        if (!isIMMActivityActive) {
            isIMMActivityActive = true
            service.startActivity(
                Intent(service, OfficialIMMActivityCompat::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
        return actionName
    }

}