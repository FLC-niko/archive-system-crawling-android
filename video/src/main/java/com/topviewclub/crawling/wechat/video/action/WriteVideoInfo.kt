package com.topviewclub.crawling.wechat.video.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.bean.AAOSTask
import com.topviewclub.common.bean.Video
import com.topviewclub.common.log.logE
import com.topviewclub.common.log.logRabbit
import com.topviewclub.common.storage.video.VideoURLWriter
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.wechat.video.VideoOperationService

class WriteVideoInfo : Action {

    override val actionName: String = "WriteVideoInfo"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        try {
            writeVideoInfo(service.aaosTask)
            return "ExitVideoList"
        } finally {
            service.resumeServiceDelay(event, 0L)
        }
    }

    private fun writeVideoInfo(aaosTask: AAOSTask) {
        val videoInfoList = VideoOperationService.videoInfoList
        val urlInfoList = VideoOperationService.videoUrlList

        val videoList = mutableListOf<Video>()

        logE("RabbitTest","writeVideoInfo : $urlInfoList")
        for (i in videoInfoList.indices) {
            if (!videoInfoList[i].video) {
                videoList.add(
                    Video(
                        videoInfoList[i],
                        videoList.lastOrNull()?.url ?: ""
                    )
                )
            } else {
                videoList.add(
                    Video(
                        videoInfoList[i],
                        if (i !in urlInfoList.indices) "" else urlInfoList[i]
                    )
                )
            }
        }
        VideoURLWriter.sendVideoAutoToBackend(videoList, aaosTask)
    }
}