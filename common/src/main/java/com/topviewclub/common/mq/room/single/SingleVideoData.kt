package com.topviewclub.common.mq.room.single

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "singleVideoCorrelationIdDB",indices = [Index(value = ["time"])])
data class SingleVideoData(
    @PrimaryKey(autoGenerate = true)
    val id:Int,
    val correlationId:String,
    val time: Long
):java.io.Serializable{
    constructor(correlationId: String,time: Long):this(0,correlationId,time)
}
