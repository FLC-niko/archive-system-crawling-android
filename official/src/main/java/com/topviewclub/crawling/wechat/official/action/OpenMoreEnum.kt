package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

class OpenMoreEnum : Action {

    private companion object {
        private const val MORE_INFO_DES = "更多信息"
        private const val MORE_INFO_ID = "com.tencent.mm:id/en"
    }

    override val actionName: String = "OpenMoreEnum"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        while (true) {
            service.windows.forEach {
                val window = it
                if (window != null) {
                    val root = window.root
                    if (root != null) {
                        val t = root.findNodeOrNull {
                            isClickable &&
                                    contentDescription == MORE_INFO_DES &&
                                    viewIdResourceName == MORE_INFO_ID
                        }
                        if (t != null) {
                            if (t.click()) {
                                Thread.sleep(1000L)
                                service.resumeServiceDelay(event, 0L)
                                return "CopyOfficialArticleURL"
                            }
                        }
                    }
                }
            }
            Thread.sleep(100L)
        }
    }
}