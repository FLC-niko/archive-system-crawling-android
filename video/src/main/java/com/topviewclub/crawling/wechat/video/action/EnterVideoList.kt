package com.topviewclub.crawling.wechat.video.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

/**
 * 进入视频播放列表
 * */
class EnterVideoList : Action {

    companion object {
        private const val VIDEO_VIEW_CLASS_NAME =
            "com.tencent.mm.plugin.finder.feed.ui.FinderProfileTimeLineUI"
        private const val VIDEO_LIST_ID = "com.tencent.mm:id/i6r"
        private const val VIDEO_ITEM_ID = "com.tencent.mm:id/i5q"
        private const val LIVE_TEXT = "直播中"
    }

    override val actionName: String = "EnterVideoList"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        return if (match(root)) {
            Thread.sleep(3000L)
            "GetVideoInfo"
        } else actionName
    }

    private fun match(root: AccessibilityNodeInfo): Boolean {
        val listNodes = root.findNodes {
            className == CLS_RECYCLER_VIEW
                    && viewIdResourceName == VIDEO_LIST_ID
        }
        var listNode: AccessibilityNodeInfo? = null
        for (i in listNodes.indices) {
            val node = listNodes[i]
            if (node.findNodeOrNull { className == CLS_IMAGE_VIEW } != null) {
                listNode = node
                break
            }
        }
        if (listNode != null) {
            val target = listNode.findNodeOrNull {
                className == CLS_LINEAR_LAYOUT
                        && viewIdResourceName == VIDEO_ITEM_ID
                        // 不点直播
                        && findNodeOrNull { text?.toString() == LIVE_TEXT } == null
            } ?: return false
            target.click()
            return true
        }
        return false
    }

}