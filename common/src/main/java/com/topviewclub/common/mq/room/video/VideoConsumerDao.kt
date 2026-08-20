package com.topviewclub.common.mq.room.video

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.topviewclub.common.mq.room.gzh.GzhData

@Dao
interface VideoConsumerDao {
    @Insert
    fun insert(vararg  correlationId: VideoData)

    @Query("SELECT * FROM videoCorrelationIdDB WHERE correlationId = :correlationId")
    fun selectCorrelationId(correlationId: String): VideoData?

    @Query("SELECT * FROM videoCorrelationIdDB order by time desc")
    fun selectAllVideoDesc(): List<VideoData>


    @Query("SELECT * FROM videoCorrelationIdDB WHERE time <= :time order by time asc")
    fun selectLimitedOrderByTimeAsc(time: Long = Long.MAX_VALUE): List<VideoData>

    @Delete
    fun deleteVideo(vararg video: VideoData)

}