package com.topviewclub.crawling.xuexi.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.storage.xuexi.XueXiArticleWriter
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.xuexi.xueXiArticleSetInternal

class WriteXueXiArticle : Action {

    override val actionName: String = "WriteXueXiArticle"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        try {
            XueXiArticleWriter.writeXueXiArticleSet(
                xueXiArticleSetInternal,
                service.serviceTag ?: ""
            )
            return AutoOperationService.ActionType.ActionSuccess
        } finally {
            service.resumeServiceDelay(event, 0L)
        }
    }

}