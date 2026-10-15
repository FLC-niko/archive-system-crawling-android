package com.topviewclub.crawling.wechat.official

import org.junit.Assert.*
import org.junit.Test

class OfficialDateLabelsTest {
    @Test fun titleAndPosterTextAreNotPublicationDates() {
        assertNull(OfficialPageDetector.extractDateOrNull("今天，海珠政法人共庆祖国华诞！"))
        assertNull(OfficialPageDetector.extractDateOrNull("通知：2024年3月17日开展活动"))
        assertNull(OfficialPageDetector.extractDateOrNull("1949/10/01—2021/10/01"))
    }
    @Test fun independentDateLabelsAndTimesStillWork() {
        assertEquals(officialTimeFormat("今天"), OfficialPageDetector.extractDateOrNull("今天 10:00"))
        assertEquals(officialTimeFormat("2024年3月17日"), OfficialPageDetector.extractDateOrNull("2024年3月17日 星期日"))
        assertEquals(officialTimeFormat("周三"), OfficialPageDetector.extractDateOrNull("星期三"))
    }
}
