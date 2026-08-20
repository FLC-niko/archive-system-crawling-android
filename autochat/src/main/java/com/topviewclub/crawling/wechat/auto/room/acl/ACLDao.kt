package com.topviewclub.crawling.wechat.auto.room.acl

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ACLDao {
    @Insert
    fun insertLimited(vararg acl: ACLimited)

    @Query("SELECT * FROM ACLimited order by updateTime desc")
    fun selectAllLimited(): List<ACLimited>

    @Query("SELECT * FROM ACLimited WHERE numberOfWechat = :numberOfWechat")
    fun selectLimited(numberOfWechat: String): List<ACLimited>

    @Query("SELECT * FROM ACLimited WHERE updateTime <= :time order by updateTime asc")
    fun selectLimitedOrderByTimeAsc(time: Long = Long.MAX_VALUE): List<ACLimited>

    @Update
    fun updateLimited(vararg acl: ACLimited)

    @Delete
    fun deleteLimited(vararg acl: ACLimited)
}
