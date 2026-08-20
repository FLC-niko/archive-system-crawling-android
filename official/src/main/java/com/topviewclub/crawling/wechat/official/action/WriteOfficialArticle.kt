package com.topviewclub.crawling.wechat.official.action

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.topviewclub.common.storage.official.OfficialArticleWriter
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.wechat.official.officialArticleSetInternal

class WriteOfficialArticle : Action {

    override val actionName: String = "WriteOfficialArticle"

    @RequiresApi(Build.VERSION_CODES.O)
    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        try {
//            OfficialArticleWriter.writeOfficialArticleSet(
//                officialArticleSetInternal,
//                service.serviceTag ?: ""
//            )
            OfficialArticleWriter.sendOfficialArticleSetToBigData(
                officialArticleSetInternal,
                service.aaosTask
            )
            return "ExitOfficialArticleList"
        } finally {
            service.resumeServiceDelay(event, 0L)
        }
    }

}