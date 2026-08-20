package com.topviewclub.common.mq.room.total

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(tableName = "pickupDB", indices = [Index(value = ["time"])])
data class PickupData(
    @PrimaryKey
    val target: String,
    val correlationId: String,
    var frequency: Int,
    val time: Long,
) : java.io.Serializable
