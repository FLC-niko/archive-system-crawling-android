package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

class CopyOfficialArticleURL : Action {

    private companion object {
        private const val COPY_URL_TEXT = "复制链接"
        private const val FLOATING_TEXT = "浮窗"
        private const val SEARCH_TEXT = "搜索页面内容"
        private const val ITEM_ID = "com.tencent.mm:id/ko8"
    }

    override val actionName: String = "CopyOfficialArticleURL"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        service.resumeServiceDelay(event, 0L)
        while (true) {
            service.windows.forEach {
                val window = it
                if (window != null) {
                    val root = window.root
                    if (root != null) {
                        val floatingButton = root.findNodeOrNull {
                            text?.toString() == FLOATING_TEXT &&
                                    viewIdResourceName == ITEM_ID
                        }
                        if (floatingButton != null) {
                            val searchButton = root.findNodeOrNull {
                                text?.toString() == SEARCH_TEXT &&
                                        viewIdResourceName == ITEM_ID
                            }
                            if (searchButton != null) {
                                val copyUrlButton = root.findNodeOrNull {
                                    text?.toString() == COPY_URL_TEXT &&
                                            viewIdResourceName == ITEM_ID
                                }
                                if (copyUrlButton != null) {
                                    if (copyUrlButton.parent.click()) {
                                        return "GetOfficialArticleURL"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}