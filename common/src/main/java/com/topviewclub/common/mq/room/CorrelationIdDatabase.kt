package com.topviewclub.common.mq.room

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.topviewclub.common.base.appContext
import com.topviewclub.common.log.logRabbit
import com.topviewclub.common.mq.room.gzh.GzhConsumerDao
import com.topviewclub.common.mq.room.gzh.GzhData
import com.topviewclub.common.mq.room.single.SingleVideoConsumerDao
import com.topviewclub.common.mq.room.single.SingleVideoData
import com.topviewclub.common.mq.room.total.PickupDao
import com.topviewclub.common.mq.room.total.PickupData
import com.topviewclub.common.mq.room.video.VideoConsumerDao
import com.topviewclub.common.mq.room.video.VideoData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val roomGzhLock = ReentrantLock()
val roomVideoLock = ReentrantLock()
val roomSingleVideoLock = ReentrantLock()
val pickupDataLock = ReentrantLock()

val gzhDao: GzhConsumerDao get() = correlationDataBase.gzhConsumerDao()

val videoDao: VideoConsumerDao get() = correlationDataBase.videoConsumerDao()

val singleVideoDao: SingleVideoConsumerDao get() = correlationDataBase.singleVideoConsumerDao()
val pickupDao: PickupDao get() = correlationDataBase.pickupDao()

private const val DATA_BASE_VERSION = 1
private const val DATA_BAST_NAME = "correlationIdDB"

private val correlationDataBase by lazy {
    Room.databaseBuilder(appContext, CorrelationDataBase::class.java, DATA_BAST_NAME)
        .allowMainThreadQueries()
        .build()
}

@Database(
    entities = [GzhData::class, VideoData::class, SingleVideoData::class,PickupData::class],
    version = DATA_BASE_VERSION
)
abstract class CorrelationDataBase : RoomDatabase() {

    abstract fun gzhConsumerDao(): GzhConsumerDao

    abstract fun videoConsumerDao(): VideoConsumerDao

    abstract fun singleVideoConsumerDao(): SingleVideoConsumerDao

    abstract fun pickupDao(): PickupDao

}


private const val THREE_DAY_LONG = 3 * 24 * 60 * 60 * 1000L

/**
 * 用于判断该任务加载是否超过限制
 */
fun isExceededTimes(correlationId:String,target:String): Boolean? {
     return runCatching {
        pickupDataLock.withLock {
            // 把 3 天前的数据清空
            val acs = pickupDao.selectPickupDataByTimeAsc(
                System.currentTimeMillis() - THREE_DAY_LONG
            )
            pickupDao.delete(*acs.toTypedArray())

            // 对任务进行检索
            val data = pickupDao.selectCorrelationId(correlationId)
            if(data==null){
                pickupDao.insert(
                    PickupData(target,correlationId,1,System.currentTimeMillis())
                )
                return@withLock false
            }else{
                // 判断这个任务加载次数是否超过五次，如果超过就把任务删除再返回true
                if(data.frequency>=5){
                    runCatching {
                        pickupDao.delete(data)
                    }
                    return@withLock true
                }else{
                    data.frequency++
                    pickupDao.update(data)
                    return@withLock false
                }
            }
        }
    }.getOrNull()
}

fun addGzhCorrelationData(data: String) {
    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            roomGzhLock.withLock {
                // 把 3 天前的数据清空
                val acs = gzhDao.selectLimitedOrderByTimeAsc(
                    System.currentTimeMillis() - THREE_DAY_LONG
                )
                gzhDao.deleteVideo(*acs.toTypedArray())
                gzhDao.insert(GzhData(data, System.currentTimeMillis()))

            }
        }.onFailure {
            throw it
        }
    }
}

fun addVideoCorrelationData(data: String) {
    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            roomVideoLock.withLock {
                            // 把 3 天前的数据清空
            val acs = videoDao.selectLimitedOrderByTimeAsc(
                System.currentTimeMillis() - THREE_DAY_LONG
            )
            videoDao.deleteVideo(*acs.toTypedArray())
                videoDao.insert(
                    VideoData(data, System.currentTimeMillis())
                )
            }

        }.onFailure {
            throw it
        }
    }

}

fun addSingleVideoCorrelationData(data: String) {
    runCatching {
        roomSingleVideoLock.withLock {
//            // 把 3 天前的数据清空
//            val acs = singleVideoDao.selectLimitedOrderByTimeAsc(
//                System.currentTimeMillis() - THREE_DAY_LONG
//            )
//            singleVideoDao.deleteVideo(*acs.toTypedArray())
            singleVideoDao.insert(
                SingleVideoData(data, System.currentTimeMillis())
            )
        }


    }.onFailure {
        throw it
    }
}