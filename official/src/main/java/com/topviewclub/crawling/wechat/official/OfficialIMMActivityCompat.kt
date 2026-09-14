package com.topviewclub.crawling.wechat.official

import androidx.appcompat.app.AppCompatActivity
import com.topviewclub.common.bean.OfficialArticle
import com.topviewclub.common.bean.TaskCrawlingType
import com.topviewclub.common.log.logI
import com.topviewclub.common.util.clipboardContent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.wechat.official.action.GetOfficialArticleURL

internal val officialArticleSetInternal = linkedSetOf<OfficialArticle>()

class OfficialIMMActivityCompat : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        window.decorView.post {
            val content = clipboardContent
            logI("OfficialIMMActivityCompat", "Captured article URL from clipboard: $content (total=${officialArticleSetInternal.size + 1})")
            officialArticleSetInternal.add(OfficialArticle(content))
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        GetOfficialArticleURL.isIMMActivityDestroyed = true
        AutoOperationService.wakeServiceForTask(TaskCrawlingType.TYPE_OFFICIAL)
    }

}
