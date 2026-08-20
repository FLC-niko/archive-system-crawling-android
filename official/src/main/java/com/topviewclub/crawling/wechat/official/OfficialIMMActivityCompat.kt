package com.topviewclub.crawling.wechat.official

import androidx.appcompat.app.AppCompatActivity
import com.topviewclub.common.bean.OfficialArticle
import com.topviewclub.common.util.clipboardContent
import com.topviewclub.crawling.wechat.official.action.GetOfficialArticleURL

internal val officialArticleSetInternal = linkedSetOf<OfficialArticle>()

class OfficialIMMActivityCompat : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        window.decorView.post {
            val content = clipboardContent
            officialArticleSetInternal.add(OfficialArticle(content))
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        GetOfficialArticleURL.isIMMActivityDestroyed = true
    }

}
