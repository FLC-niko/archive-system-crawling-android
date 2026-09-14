package com.topviewclub.crawling.service.wechat

/**
 * 微信无障碍操作服务宿主协议接口。
 * 提供动作链在页面流转时获取宿主目标与首要动作，消除对具体 Service 实现类的向下强转。
 */
interface WechatActionHost {
    /** 校验目标账号成功后，进入业务链的首个动作名称（如 "HomingOfficialList"、"PlayVideo"） */
    val firstlyTargetActionName: String

    /** 当前任务标识 tag */
    val serviceTag: String?

    /** 当前目标账号名 */
    val target: String
}
