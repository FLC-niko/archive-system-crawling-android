package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.back

class BackToOfficialArticleList : Action {

    override val actionName: String = "BackToOfficialArticleList"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        Thread.sleep(500L)
        service.back()
        service.resumeServiceDelay(event, 0L)
        return "EnterOfficialArticle"
    }

}