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

    // 微信主页 LauncherUI 导航特征词（用于排除误判）
    private val LAUNCHER_UI_MARKERS = listOf(
        "朋友圈",
        "通讯录",
        "听一听",
        "搜一搜",
    )

    private val DATE_REGEX = Regex(
        "置顶|今天|昨天|星期[一二三四五六日]|周[一二三四五六日]|(?:\\d{4}年)?\\d{1,2}月\\d{1,2}日"
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
        currentActivity: String? = null,
    ): Boolean {
        // 0. 前台必须是微信
        val pkg = root?.packageName?.toString()
        if (pkg != null && pkg != "com.tencent.mm") return false

        // 1. 如果已命中公众号列表特征，绝不是文章详情页
        if (isOfficialListPage(lines, root, pageClass, currentActivity)) {
            return false
        }

        // 2. 检查微信主界面：主界面导航栏不是文章详情
        if (lines.any { line ->
                val trimmed = line.text.trim()
                LAUNCHER_UI_MARKERS.any { m -> trimmed == m }
            }) {
            return false
        }

        // 3. OCR 文本特征判定（文章底部菜单栏/分享弹窗 或 文章正文元数据）
        if (lines.isNotEmpty()) {
            val matchesMenuMarker = lines.any { line ->
                val t = line.text.replace(" ", "")
                ARTICLE_MENU_MARKERS.any { marker -> t.contains(marker) }
            }
            if (matchesMenuMarker) return true

            val matchesContentMarker = lines.any { line ->
                val t = line.text.replace(" ", "")
                ARTICLE_CONTENT_MARKERS.any { marker -> t.contains(marker) }
            }
            if (matchesContentMarker && !lines.any { it.text.contains(THE_END_TEXT) }) {
                return true
            }
        }

        // 4. 处于 WebView 但未命中列表特征时，判定为文章页
        val inWebView = pageClass.contains("WebView", ignoreCase = true) ||
            pageClass.contains("TmplWebViewMMUI", ignoreCase = true) ||
            pageClass.contains("MMWebView", ignoreCase = true) ||
            currentActivity?.contains("WebView", ignoreCase = true) == true ||
            currentActivity?.contains("TmplWebViewMMUI", ignoreCase = true) == true ||
            currentActivity?.contains("MMWebView", ignoreCase = true) == true

        if (inWebView) {
            return true
        }

        // 5. 节点树特征判定
        if (root != null && root.childCount > 0) {
            val hasWebViewNode = root.findNodeOrNull {
                className?.toString()?.contains("WebView", ignoreCase = true) == true
            } != null
            if (hasWebViewNode) return true
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
        currentActivity: String? = null,
    ): Boolean {
        // 0. 前台必须是微信
        val pkg = root?.packageName?.toString()
        if (pkg != null && pkg != "com.tencent.mm") return false

        // 0.1 严格排除微信主界面 LauncherUI（如发现页、通讯录等）
        if (currentActivity?.contains("LauncherUI", ignoreCase = true) == true ||
            pageClass.contains("LauncherUI", ignoreCase = true)
        ) {
            // 如果处于 LauncherUI 且未包含列表特有 header，直接排除
            val hasListHeader = lines.any { line ->
                val t = line.text.replace(" ", "")
                t.contains("全部") && (t.contains("文章") || t.contains("视频号") || t.contains("服务") || t.contains("贴图")) ||
                        t.contains("全部消息")
            }
            if (!hasListHeader) return false
        }
        if (lines.any { line ->
                val trimmed = line.text.trim()
                LAUNCHER_UI_MARKERS.any { m -> trimmed == m }
            }) {
            return false
        }

        // 1. 类名与当前Activity判定
        if (pageClass.contains("ContactInfoUI", ignoreCase = true) ||
            pageClass.contains("BizContactInfoUI", ignoreCase = true) ||
            currentActivity?.contains("ContactInfoUI", ignoreCase = true) == true ||
            currentActivity?.contains("BizContactInfoUI", ignoreCase = true) == true
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

        // 3. OCR 文本特征判定（适用于 WebView 渲染的公众号主页）
        if (lines.isNotEmpty()) {
            if (lines.any { it.text.contains(THE_END_TEXT) }) return true

            // 公众号主页特征 Tab 栏：全部 + (文章 | 视频号 | 服务 | 贴图)
            val hasTabs = lines.any { line ->
                val t = line.text.replace(" ", "")
                t.contains("全部") && (t.contains("文章") || t.contains("服务") || t.contains("视频号") || t.contains("贴图")) ||
                        t.contains("全部消息")
            }
            if (hasTabs) return true

            // 公众号主页操作栏：已关注 / 发消息 / 关注公众号
            val hasActionButtons = lines.any { line ->
                val t = line.text.replace(" ", "")
                t == "已关注" || t == "发消息" || t == "关注公众号"
            }
            if (hasActionButtons && hasVisibleDates(lines)) return true

            // 如果屏幕上有可见推文日期，且没有文章操作栏与文章正文元数据，说明处于列表内容区
            if (hasVisibleDates(lines)) {
                val hasArticleMarkers = lines.any { line ->
                    val t = line.text.replace(" ", "")
                    ARTICLE_MENU_MARKERS.any { m -> t.contains(m) } ||
                            ARTICLE_CONTENT_MARKERS.any { m -> t.contains(m) }
                }
                if (!hasArticleMarkers) return true
            }
        }

        return false
    }
}
