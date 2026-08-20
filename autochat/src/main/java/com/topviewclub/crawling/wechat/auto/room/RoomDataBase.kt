package com.topviewclub.crawling.wechat.auto.room

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.topviewclub.common.base.appContext
import com.topviewclub.crawling.wechat.auto.room.acv.ACVDao
import com.topviewclub.crawling.wechat.auto.room.acv.ACVideo
import com.topviewclub.crawling.wechat.auto.room.acl.ACLDao
import com.topviewclub.crawling.wechat.auto.room.acl.ACLimited
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val roomACLock = ReentrantLock()

val ACVideoDao: ACVDao get() = acDataBase.acVideoDao()

val ACLimitedDao: ACLDao get() = acDataBase.acLimitedDao()

private const val DATA_BASE_VERSION = 1
private const val DATA_BAST_NAME = "ACServiceDB"

private val acDataBase by lazy {
    Room.databaseBuilder(appContext, ACDataBase::class.java, DATA_BAST_NAME)
        .allowMainThreadQueries()
        .build()
}

@Database(entities = [ACVideo::class, ACLimited::class], version = DATA_BASE_VERSION)
abstract class ACDataBase : RoomDatabase() {

    abstract fun acVideoDao(): ACVDao

    abstract fun acLimitedDao(): ACLDao

}

private const val THREE_DAY_LONG = 3 * 24 * 60 * 60 * 1000L

fun requireACVideo(typeCode: String): ACVideo {
    var ac: ACVideo
    runCatching {
        roomACLock.withLock {
            // 把 3 天前的数据清空
            val acs = ACVideoDao.selectVideoBeforeTime(
                System.currentTimeMillis() - THREE_DAY_LONG
            )
            ACVideoDao.deleteVideo(*acs.toTypedArray())

            val type = typeCode.substring(0, 2)
            val code = typeCode.substring(2).toLong()
            ac = ACVideoDao.selectVideo(code, type).first()
            return ac
        }
    }.onFailure {
        throw it
    }
    throw IllegalStateException()
}
