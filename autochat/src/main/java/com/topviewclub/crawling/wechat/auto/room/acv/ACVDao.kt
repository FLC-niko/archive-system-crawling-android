package com.topviewclub.crawling.wechat.auto.room.acv

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ACVDao {
    @Insert
    fun insertVideo(vararg video: ACVideo)

    @Query("SELECT * FROM ACVideo order by time desc")
    fun selectAllVideoDesc(): List<ACVideo>

    @Query("SELECT * FROM ACVideo WHERE numberOfWechat = :numberOfWechat order by time desc")
    fun selectVideoDesc(numberOfWechat: String): List<ACVideo>

    @Query("SELECT * FROM ACVideo WHERE requestCode = :requestCode AND requestType = :requestType")
    fun selectVideo(requestCode: Long, requestType: String): List<ACVideo>

    @Query("SELECT * FROM ACVideo WHERE time <= :time")
    fun selectVideoBeforeTime(time: Long): List<ACVideo>

    @Delete
    fun deleteVideo(vararg video: ACVideo)
}
