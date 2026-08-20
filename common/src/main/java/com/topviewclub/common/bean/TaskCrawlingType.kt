package com.topviewclub.common.bean

import androidx.annotation.StringDef
import com.topviewclub.common.bean.TaskCrawlingType.TYPE_AUTO_CHAT
import com.topviewclub.common.bean.TaskCrawlingType.TYPE_CHECK_WECHAT_QRCODE
import com.topviewclub.common.bean.TaskCrawlingType.TYPE_NOTHING
import com.topviewclub.common.bean.TaskCrawlingType.TYPE_OFFICIAL
import com.topviewclub.common.bean.TaskCrawlingType.TYPE_VIDEO
import com.topviewclub.common.bean.TaskCrawlingType.TYPE_XUE_XI
import com.topviewclub.common.bean.TaskCrawlingType.TYPE_WEREAD_LOGIN

@StringDef(
    value = [
        TYPE_NOTHING,
        TYPE_VIDEO,
        TYPE_OFFICIAL,
        TYPE_AUTO_CHAT,
        TYPE_CHECK_WECHAT_QRCODE,
        TYPE_XUE_XI,
        TYPE_WEREAD_LOGIN
    ]
)
annotation class TaskType

object TaskCrawlingType {
    /**
     * 未在进行任何任务
     * */
    const val TYPE_NOTHING = "nothing"

    /**
     * 表示抓取视频号视频
     * */
    const val TYPE_VIDEO = "video"

    /**
     * 表示抓取公众号文章
     * */
    const val TYPE_OFFICIAL = "official"

    /**
     * AC 服务
     * */
    const val TYPE_AUTO_CHAT = "auto_chat"

    /**
     * 表示测试微信二维码是否有效
     * */
    const val TYPE_CHECK_WECHAT_QRCODE = "wechat_qrcode_check"

    /**
     * 表示抓取学习强国文章
     * */
    const val TYPE_XUE_XI = "xue_xi"

    /** 微信读书扫码登录 */
    const val TYPE_WEREAD_LOGIN = "weread_login"
}