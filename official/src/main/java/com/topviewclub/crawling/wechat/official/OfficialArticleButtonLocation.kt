package com.topviewclub.crawling.wechat.official

/**
 * 因为这个实在是没有办法了，只能盲点，所以需要根据设备调整点击的位置
 * <p>
 *
 * 如何查看按钮位置？
 * <p>
 *
 * 开发者选项中有一个显示指针位置的选项，打开它，屏幕上就会实时显示点击的位置了
 * */
sealed class OfficialArticleButtonLocation(
    /**
     * 这个要根据设备更改，
     * 此参数是公众号文章界面右上角三个点按钮的 X 坐标
     * */
    val moreInfoX: Float,
    /**
     * 这个要根据设备更改，
     * 此参数是公众号文章界面右上角三个点按钮的 Y 坐标
     * */
    val moreInfoY: Float,
    /**
     * 这个要根据设备更改，
     * 此参数是公众号文章界面点开右上角三个点按钮后下方复制链接按钮的 X 坐标
     * */
    val copyUrlX: Float,
    /**
     * 这个要根据设备更改，
     * 此参数是公众号文章界面点开右上角三个点按钮后下方复制链接按钮的 Y 坐标
     * */
    val copyUrlY: Float
) {

    /**
     * 联想小新 Pro13 AMD 2020 版
     *
     * @author Kokomi
     * */
    class LenovoXiaoXinPro13AMD2020 : OfficialArticleButtonLocation(
        2460f, 100f,
        900f, 1200f
    )

    /**
     * 华为 MateBook14 Intel 2021 款
     *
     * @author Kokomi
     * */
    class HuaWeiMateBook14Intel2021 : OfficialArticleButtonLocation(
        2085f, 81f,
        750f, 1100f
    )

    /**
     * 华为 nova 5i
     *
     * @author Kokomi
     * */
    class HuaWeiNova5I : OfficialArticleButtonLocation(
        665f, 110f,
        320f, 1200f
    )

}

