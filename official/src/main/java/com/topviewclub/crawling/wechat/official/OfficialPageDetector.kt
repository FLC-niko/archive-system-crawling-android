package com.topviewclub.crawling.wechat.official

import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.CLS_RECYCLER_VIEW
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.wechat.official.action.RecognizedScreenLine

internal object OfficialPageDetector {

    const val THE_END_TEXT = "已无更多订阅消息"

    // 文章顶部元数据与内容特征词
    private val ARTICLE_CONTENT_MARKERS = listOf(
        "微信公众平台",
        "原创",
        "公众号",
        "发表于",
        "收录于合集",
        "关注公众号",
        "喜欢此内容的人还喜欢",
        "人划线",
        "IP属地",
        "IP 属地",
        "阅读",
        "在看",
        "点赞",
        "赞同",
    )

    // 文章底部与操作菜单特征词
    private val ARTICLE_MENU_MARKERS = listOf(
        "写留言",
        "听全文",
        "分享到朋友圈",
        "转发给朋友",
        "收藏",
        "在浏览器打开",
        "调整字体",
        "浮窗",
        "搜索页面内容",
        "复制链接",
    )

    // 公众号主页/历史列表头部特征词
    private val LIST_HEADER_MARKERS = listOf(
        "全部消息",
        "已关注",
        "发消息",
        "服务",
        "视频号",
        "关注公众号",
    )

    private val DATE_REGEX = Regex(
        "今天|昨天|星期[一二三四五六日]|周[一二三四五六日]|(?:\\d{4}年)?\\d{1,2}月\\d{1,2}日"
    )

    fun extractDateOrNull(text: String): Long? {
        val match = DATE_REGEX.find(text)?.value ?: return null
        return officialTimeFormat(match.replace("星期", "周"))
    }

    fun hasVisibleDates(lines: List<RecognizedScreenLine>): Boolean {
        return lines.any { extractDateOrNull(it.text) != null }
    }

    /**
     * 判断当前是否处于微信文章详情页（WebView / 正文 / 底部交互菜单）
     */
    fun isArticleDetailPage(
        lines: List<RecognizedScreenLine>,
        root: AccessibilityNodeInfo?,
        pageClass: String = "",
    ): Boolean {
        // 1. 类名判定
        if (pageClass.contains("WebView", ignoreCase = true) ||
            pageClass.contains("TmplWebViewMMUI", ignoreCase = true) ||
            pageClass.contains("MMWebView", ignoreCase = true)
        ) {
            return true
        }

        // 2. 节点树特征判定
        if (root != null && root.childCount > 0) {
            val hasWebViewNode = root.findNodeOrNull {
                className?.toString()?.contains("WebView", ignoreCase = true) == true
            } != null
            if (hasWebViewNode) return true
        }

        // 3. OCR 文本特征判定（文章底部/菜单 或 文章正文元数据）
        if (lines.isNotEmpty()) {
            val matchesMenuMarker = lines.any { line ->
                ARTICLE_MENU_MARKERS.any { marker -> line.text.contains(marker) }
            }
            if (matchesMenuMarker) return true

            val matchesContentMarker = lines.any { line ->
                ARTICLE_CONTENT_MARKERS.any { marker -> line.text.contains(marker) }
            }
            // 如果命中正文特征词，且屏幕上完全没有列表末尾和列表日期，判定为文章页
            if (matchesContentMarker && !hasVisibleDates(lines) && !lines.any { it.text.contains(THE_END_TEXT) }) {
                return true
            }
        }

        return false
    }

    /**
     * 判断当前是否处于微信公众号历史文章列表页
     */
    fun isOfficialListPage(
        lines: List<RecognizedScreenLine>,
        root: AccessibilityNodeInfo?,
        pageClass: String = "",
    ): Boolean {
        // 1. 类名判定
        if (pageClass.contains("ContactInfoUI", ignoreCase = true) ||
            pageClass.contains("BizContactInfoUI", ignoreCase = true)
        ) {
            return true
        }

        // 2. 节点树判定（如果有 RecyclerView）
        if (root != null && root.childCount > 0) {
            val hasRecyclerView = root.findNodeOrNull {
                className == CLS_RECYCLER_VIEW
            } != null
            if (hasRecyclerView) return true
        }

        // 3. OCR 文本特征判定
        if (lines.isNotEmpty()) {
            if (lines.any { it.text.contains(THE_END_TEXT) }) return true
            if (hasVisibleDates(lines)) return true
            if (lines.any { line -> LIST_HEADER_MARKERS.any { marker -> line.text.contains(marker) } }) {
                return true
            }
        }

        return false
    }
}
