package com.topviewclub.crawling.wechat.official

import android.graphics.Rect
import com.topviewclub.crawling.wechat.official.action.RecognizedScreenLine
import org.junit.Assert.*
import org.junit.Test

class OfficialPageDetectorTest {

    private fun createLine(text: String): RecognizedScreenLine {
        return RecognizedScreenLine(text, Rect(0, 0, 1080, 50))
    }

    @Test
    fun testExtractDate() {
        val today = OfficialPageDetector.extractDateOrNull("今天 10:00")
        assertNotNull(today)

        val yesterday = OfficialPageDetector.extractDateOrNull("昨天 12:30")
        assertNotNull(yesterday)

        val dateYMD = OfficialPageDetector.extractDateOrNull("2026年3月1日")
        assertNotNull(dateYMD)

        val dateMD = OfficialPageDetector.extractDateOrNull("3月15日")
        assertNotNull(dateMD)

        val weekDay = OfficialPageDetector.extractDateOrNull("星期三")
        assertNotNull(weekDay)

        val nonDate = OfficialPageDetector.extractDateOrNull("这是一篇普通的文章标题内容")
        assertNull(nonDate)
    }

    @Test
    fun testArticleDetailPageDetection_WithTopArticleContent() {
        // 文章详情页顶部：只有文章标题、公众号、原创、发表于等元数据，没有底部"写留言"
        val lines = listOf(
            createLine("如何做好移动端性能优化与无障碍"),
            createLine("极客技术团队"),
            createLine("原创"),
            createLine("发表于 广东"),
            createLine("收录于合集 #Android开发 12个"),
            createLine("在很多场景下我们需要做自动化测试..."),
        )

        val isArticle = OfficialPageDetector.isArticleDetailPage(
            lines = lines,
            root = null,
            pageClass = "android.widget.FrameLayout"
        )
        assertTrue("应正确识别仅包含顶部正文元数据的文章详情页", isArticle)

        val isList = OfficialPageDetector.isOfficialListPage(
            lines = lines,
            root = null,
            pageClass = "android.widget.FrameLayout"
        )
        assertFalse("不应将文章正文误判为列表页", isList)
    }

    @Test
    fun testArticleDetailPageDetection_WithMenuMarkers() {
        // 文章详情页底部/交互菜单
        val lines = listOf(
            createLine("写留言"),
            createLine("听全文"),
            createLine("分享到朋友圈"),
            createLine("在浏览器打开"),
        )

        val isArticle = OfficialPageDetector.isArticleDetailPage(
            lines = lines,
            root = null,
            pageClass = "com.tencent.mm.plugin.brandservice.ui.timeline.preload.ui.TmplWebViewMMUI"
        )
        assertTrue("应识别包含底部菜单的文章详情页", isArticle)
    }

    @Test
    fun testOfficialListPageDetection_WithDatesAndHeader() {
        // 历史列表页
        val lines = listOf(
            createLine("全部消息"),
            createLine("已关注"),
            createLine("今天"),
            createLine("大模型时代的开发范式转型探讨"),
            createLine("昨天"),
            createLine("微服务架构设计与治理实践"),
        )

        val isList = OfficialPageDetector.isOfficialListPage(
            lines = lines,
            root = null,
            pageClass = "com.tencent.mm.plugin.profile.ui.ContactInfoUI"
        )
        assertTrue("应识别包含日期和头部特征的公众号历史列表页", isList)

        val isArticle = OfficialPageDetector.isArticleDetailPage(
            lines = lines,
            root = null,
            pageClass = "com.tencent.mm.plugin.profile.ui.ContactInfoUI"
        )
        assertFalse("不应将包含日期的列表页误判为文章详情页", isArticle)
    }

    @Test
    fun testOfficialListPageDetection_TheEnd() {
        // 到底特征
        val lines = listOf(
            createLine("已无更多订阅消息")
        )

        val isList = OfficialPageDetector.isOfficialListPage(
            lines = lines,
            root = null,
            pageClass = ""
        )
        assertTrue("应识别到达底部的列表页", isList)
    }
}
