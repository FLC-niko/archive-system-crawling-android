package com.topviewclub.crawling.wechat.video.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.common.bean.VideoInfo
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.wechat.video.videoTimeFormat

internal val videoInfoSetInternal = linkedSetOf<VideoInfo>()

class GetVideoInfo : Action {

    companion object {
        private const val COMMENT_ID = "com.tencent.mm:id/bjp"
        private const val TITLE_ID = "com.tencent.mm:id/bga"
        private const val RELEASE_TIME_ID = "com.tencent.mm:id/brd"
        private const val BACK_TO_LIST_ID = "com.tencent.mm:id/be_"
        private const val LOADING_ID = "com.tencent.mm:id/g30"
        private const val LOADING_TEXT = "正在加载..."
        private const val VIEW_PAGER_BANNER_ID = "com.tencent.mm:id/f3r"

        private var notVideo = false
    }

    override val actionName: String = "GetVideoInfo"

    private var stepInternal = 0

    private val commentMap = linkedMapOf<AccessibilityNodeInfo, Boolean>()

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val res = when (stepInternal) {
            0 -> openComment(root)
            1 -> getVideoInfo(root)
            2 -> return if (isBeforePublishDate(service.startDate)) {
                // 等待5秒，应该可以使标题与视频的对应关系更稳定
                Thread.sleep(5000L)
                "ScrollVideoList"
            } else {
                Thread.sleep(5000L)
                "WriteVideoInfo"
            }
            else -> false
        }

        return if (res) {
            Thread.sleep(3000L)
            "ScrollVideoList"
        } else actionName
    }

    private fun openComment(root: AccessibilityNodeInfo): Boolean {
        if (root.findNodeOrNull { viewIdResourceName == VIEW_PAGER_BANNER_ID } != null) {
            // 找到此控件，说明不是视频
            notVideo = true
        }
        if (commentMap.isNotEmpty()) {
            var node: AccessibilityNodeInfo? = null
            commentMap.map {
                if (!it.value) {
                    node = it.key
                    return@map
                }
            }
            if (node != null) {
                commentMap[node!!] = true
                node!!.click()
                stepInternal = 1
            } else {
                commentMap.clear()
                stepInternal = 2
            }
            return false
        }
        // 正在加载，等待加载完毕
        if (isLoading(root)) return false
        // 未点击打开评论区
        val targets = root.findNodes {
            viewIdResourceName == COMMENT_ID
        }
        if (targets.isEmpty()) return true
        targets.reversed().map { commentMap[it] = false }
        Thread.sleep(500L)
//        if (targets.isEmpty()) return false
//        var target: AccessibilityNodeInfo? = null
//        for (i in targets.indices) {
//            val t = targets[i]
//            t.getBoundsInScreen(bound)
//            if (bound.bottom > bound.top) {
//                target = t
//            }
//        }
//        if (target == null) return false
//        if (!target.click()) return false
//        stepInternal++
        return false
    }

    private lateinit var releaseTime: String

    private fun getVideoInfo(root: AccessibilityNodeInfo): Boolean {
        // 已打开评论区
        val releaseTimeTarget = root.findNodeOrNull {
            viewIdResourceName == RELEASE_TIME_ID
        }

        if (releaseTimeTarget == null) {
            stepInternal = 0
            return false
        }

        val titleTarget = root.findNodeOrNull {
            viewIdResourceName == TITLE_ID
        }!!
        // 返回到视频列表
        val back = root.findNodeOrNull {
            viewIdResourceName == BACK_TO_LIST_ID
        }!!

        if (!back.click()) return false

        releaseTime = releaseTimeTarget.text.toString()
        var title = titleTarget.text.toString()
        if (title.length > 200) {
            title = title.substring(0, 200)
        }
        val video = VideoInfo(releaseTime, title, !notVideo)
        notVideo = false
        // 防止重复
        videoInfoSetInternal.add(video)
        stepInternal = 0
        Thread.sleep(500L)
        return false
    }

    private fun isBeforePublishDate(startDate: Long): Boolean {
        stepInternal = 0
        // 非正值，一直抓
        if (startDate == Long.MIN_VALUE) return true

        // 至少抓三个
        if (videoInfoSetInternal.size < 4) return true

        val time = videoTimeFormat(releaseTime)
        return time >= startDate
    }

    private fun isLoading(root: AccessibilityNodeInfo) =
        root.findNodeOrNull {
            viewIdResourceName == LOADING_ID
                    && text?.toString() == LOADING_TEXT
        } != null

}