package com.topviewclub.common.mq.room.gzh

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GzhConsumerDao {
    @Insert
    fun insert(vararg  gzhData: GzhData)

    @Query("SELECT * FROM gzhCorrelationIdDB WHERE correlationId = :correlationId")
    fun selectCorrelationId(correlationId: String): GzhData?


    @Query("SELECT * FROM gzhCorrelationIdDB order by time desc")
    fun selectAllVideoDesc(): List<GzhData>


    @Query("SELECT * FROM gzhCorrelationIdDB WHERE time <= :time order by time asc")
    fun selectLimitedOrderByTimeAsc(time: Long = Long.MAX_VALUE): List<GzhData>

    @Delete
    fun deleteVideo(vararg video: GzhData)
}