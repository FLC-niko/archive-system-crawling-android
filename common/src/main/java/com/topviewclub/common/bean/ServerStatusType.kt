package com.topviewclub.common.bean
import com.topviewclub.common.network.*
import kotlinx.serialization.Serializable

object ServerStatusType{
    var record = 0
    const val IDLE = 1
    const val BUSY = 2
    const val SLEEPING = 3
}


@Serializable
data class ServerData(
    val macAddress:String = com.topviewclub.common.network.macAddress ,
    val serviceName:String = "Android",
    val status:Int,
    val description:String?,
    val insertTime:String
)