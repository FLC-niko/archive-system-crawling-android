package com.topviewclub.crawling.service

import com.topviewclub.common.bean.TaskResultType

sealed class ServiceResult(val msg: String) {

    /**
     * 表示抓取完成
     *
     * @param completedMsg 完成信息，
     * 一般为 [TaskResultType.TASK_COMPLETED]
     * */
    class Completed(
        completedMsg: String
    ) : ServiceResult(completedMsg)

    /**
     * 表示抓取失败
     *
     * @param errorMsg 错误信息
     * */
    class Error(
        errorMsg: String
    ) : ServiceResult(errorMsg)

}