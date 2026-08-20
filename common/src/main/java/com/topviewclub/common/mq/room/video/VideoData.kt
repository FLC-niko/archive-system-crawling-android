package com.topviewclub.common.mq.room.video

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "videoCorrelationIdDB",indices = [Index(value = ["time"])])
data class VideoData(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val correlationId: String,
    val time: Long
): java.io.Serializable{
    constructor(correlationId: String,time: Long):this(0,correlationId,time)
}