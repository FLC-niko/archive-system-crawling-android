package com.topviewclub.common.mq.room.total

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.topviewclub.common.mq.room.gzh.GzhData
import com.topviewclub.common.mq.room.single.SingleVideoData


@Dao
interface PickupDao {
    @Insert
    fun insert(vararg  pickupData: PickupData)


    @Update
    fun update(vararg  pickupData: PickupData)

    @Query("SELECT * FROM pickupDB WHERE correlationId = :correlationId")
    fun selectCorrelationId(correlationId: String): PickupData?

    @Query("SELECT * FROM pickupDB WHERE time <= :time order by time asc")
    fun selectPickupDataByTimeAsc(time: Long = Long.MAX_VALUE): List<PickupData>

    @Delete
    fun delete(vararg pickupData: PickupData)

}