package com.topviewclub.crawling.wechat.official

import androidx.appcompat.app.AppCompatActivity
import com.topviewclub.common.bean.OfficialArticle
import com.topviewclub.common.bean.TaskCrawlingType
import com.topviewclub.common.log.logI
import com.topviewclub.common.util.clipboardContent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.wechat.official.action.GetOfficialArticleURL
import com.topviewclub.crawling.wechat.official.action.isValidOfficialArticleUrl

internal val officialArticleSetInternal = linkedSetOf<OfficialArticle>()

class OfficialIMMActivityCompat : AppCompatActivity() {

    private val taskGeneration: Long by lazy {
        intent.getLongExtra(GetOfficialArticleURL.EXTRA_TASK_GENERATION, -1L)
    }

    private var captureAccepted = false

    override fun onResume() {
        super.onResume()
        window.decorView.post {
            val content = clipboardContent.trim()
            val validUrl = isValidOfficialArticleUrl(content)
            captureAccepted = GetOfficialArticleURL.recordActivityResult(taskGeneration, validUrl)
            if (captureAccepted && validUrl) {
                officialArticleSetInternal.add(OfficialArticle(content))
            }
            logI(
                "OfficialIMMActivityCompat",
                "Clipboard capture accepted=$captureAccepted, validUrl=$validUrl, " +
                    "total=${officialArticleSetInternal.size}",
            )
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        if (captureAccepted) {
            AutoOperationService.wakeServiceForTask(TaskCrawlingType.TYPE_OFFICIAL, taskGeneration)
        }
    }

}
