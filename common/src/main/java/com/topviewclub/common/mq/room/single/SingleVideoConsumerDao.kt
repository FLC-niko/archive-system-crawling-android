package com.topviewclub.common.mq.room.single

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SingleVideoConsumerDao {
    @Insert
    fun insert(vararg  singleVideoData: SingleVideoData)

    @Query("SELECT * FROM singleVideoCorrelationIdDB WHERE correlationId = :correlationId")
    fun selectCorrelationId(correlationId: String): SingleVideoData?


    @Query("SELECT * FROM singleVideoCorrelationIdDB order by time desc")
    fun selectAllSingleVideoDesc(): List<SingleVideoData>


    @Query("SELECT * FROM singleVideoCorrelationIdDB WHERE time <= :time order by time asc")
    fun selectLimitedOrderByTimeAsc(time: Long = Long.MAX_VALUE): List<SingleVideoData>

    @Delete
    fun deleteVideo(vararg video: SingleVideoData)

}