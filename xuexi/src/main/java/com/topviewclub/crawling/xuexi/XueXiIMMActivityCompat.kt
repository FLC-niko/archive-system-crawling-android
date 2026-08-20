package com.topviewclub.crawling.xuexi

import androidx.appcompat.app.AppCompatActivity
import com.topviewclub.common.bean.XueXiArticle
import com.topviewclub.common.util.clipboardContent
import com.topviewclub.crawling.xuexi.action.EnterXueXiArticle
import com.topviewclub.crawling.xuexi.action.GetXueXiArticleInfoCompat

internal val xueXiArticleSetInternal = linkedSetOf<XueXiArticle>()

private var count = 0

class XueXiIMMActivityCompat : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        window.decorView.post {
            val content = clipboardContent
            if (xueXiArticleSetInternal.add(XueXiArticle(content))) {
                count = 0
            } else {
                count++
            }
            // 连续 5 篇重复文章，则认为已经到底
            if (count > 5) {
                EnterXueXiArticle.isBottom = true
            }
            finish()
            window.decorView.postDelayed({
                GetXueXiArticleInfoCompat.isIMMActivityDestroyed = true
            }, 100L)
        }
    }

}