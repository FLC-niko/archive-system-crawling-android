package com.topviewclub.crawling.wechat.auto.room.acl

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["updateTime"])])
data class ACLimited(
    @PrimaryKey
    val numberOfWechat: String,
    val nameOfWechat: String,
    val requestCount: Int,
    val errorCount: Int,
    val totalRequest: Int,
    val totalError: Int,
    val updateTime: Long
) : java.io.Serializable
