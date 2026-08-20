package com.topviewclub.common.bean

object TaskResultType {

    /**
     * 作业完成
     * */
    const val TASK_COMPLETED = "TC Success"

    /**
     * 提示主机更新图片
     * */
    const val PLEASE_PUSH_PICTURE = "PPP"

    /**
     * 提示主机重启微信
     * */
    const val RESTART_MIRCO_MESSAGE = "RMM"

    /**
     * 提示主机重启学习强国
     * */
    const val RESTART_XUE_XI = "RXX"

    /**
     * 处理广播失败
     * */
    const val PROCESSING_BROADCAST_EXCEPTION = "PBE Error"

    /**
     * 更新（二维码）图片失败
     * */
    const val UPDATE_PICTURE_EXCEPTION = "UPE Error"

    /**
     * 单个视频的提取码错误
     * */
    const val REQUEST_CODE_EXCEPTION = "RC Error"

    /**
     * 目标为空错误
     * */
    const val TARGET_IS_NULL = "TIN Error"

    /**
     * 服务无响应错误
     * */
    const val SERVICE_NO_RESPONSE = "SNR Error"

    /**
     * 二维码扫描结果错误
     * */
    const val QRCODE_SCAN_EXCEPTION = "QCS Error"

    /**
     * 网络异常
     * */
    const val NETWORK_EXCEPTION = "NET Error"

    /**
     * 预料之外的抓取类型
     * */
    const val UNEXPECTED_CRAWLING_TYPE = "UCT Error"

    /**
     * 服务异常退出
     * */
    const val SERVICE_DESTROY_UNEXPECTEDLY = "SDU Error"

    /**
     * 未知错误
     * */
    const val UNKNOWN_EXCEPTION = "UNK Error"

}