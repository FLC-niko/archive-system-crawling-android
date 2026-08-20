package com.topviewclub.common.mq.room.gzh

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(tableName = "gzhCorrelationIdDB",indices = [Index(value = ["time"])])
data class GzhData(
    @PrimaryKey(autoGenerate = true)
    val id:Int,
    val correlationId:String,
    val time: Long
): java.io.Serializable{
    constructor(correlationId: String,time: Long):this(0,correlationId,time)
}
