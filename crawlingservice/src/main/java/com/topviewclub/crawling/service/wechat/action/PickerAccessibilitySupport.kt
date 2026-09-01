package com.topviewclub.crawling.service.wechat.action

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.tap

internal const val WECHAT_PACKAGE_NAME = "com.tencent.mm"

private val PICKER_FOLDER_TITLES = setOf("所有图片", "aaos", "QRCode")

/**
 * 微信相册页的几个页面在部分版本中是自绘 View，AccessibilityNodeInfo 只有空根节点。
 * 这里统一从事件类名和所有可访问窗口判断页面，动作只有确认页面已切换后才推进。
 */
internal fun AccessibilityEvent.uiClassName(): String =
    className?.toString().orEmpty()

internal fun AccessibilityEvent.isScanUiEvent(): Boolean =
    uiClassName().contains("BaseScanUI", ignoreCase = true) ||
            uiClassName().contains("scanner.ui", ignoreCase = true)

/**
 * 扫一扫使用自绘 View 时，rootInActiveWindow/windows 可能暂时没有可用根节点，
 * 但事件本身仍然带有微信包名和 BaseScanUI 类名。动作分发应把这类事件视为
 * 当前仍在微信扫一扫页，否则 ClickAlbum 会在真正可点击的页面上静默等待。
 */
internal fun AccessibilityEvent.isWechatScanContext(): Boolean =
    packageName?.toString() == WECHAT_PACKAGE_NAME && isScanUiEvent()

internal fun AccessibilityEvent.isGalleryUiEvent(): Boolean {
    val name = uiClassName()
    return name.contains("gallery", ignoreCase = true) ||
            name.contains("album", ignoreCase = true) ||
            name.contains("photopicker", ignoreCase = true) ||
            name.contains("picker", ignoreCase = true)
}

private fun isWechatPackage(packageName: CharSequence?): Boolean =
    packageName?.toString() == WECHAT_PACKAGE_NAME

internal fun AutoOperationService.wechatRoots(): Sequence<AccessibilityNodeInfo> = sequence {
    rootInActiveWindow?.let { root ->
        if (root.packageName?.toString() == WECHAT_PACKAGE_NAME) yield(root)
    }
    windows.forEach { window ->
        window.root?.let { root ->
            if (root.packageName?.toString() == WECHAT_PACKAGE_NAME) yield(root)
        }
    }
}

internal fun AutoOperationService.pickerRoots(): Sequence<AccessibilityNodeInfo> = sequence {
    rootInActiveWindow?.let { root ->
        if (isWechatPackage(root.packageName)) yield(root)
    }
    windows.forEach { window ->
        window.root?.let { root ->
            if (isWechatPackage(root.packageName)) yield(root)
        }
    }
}

internal fun AutoOperationService.findWechatNodeOrNull(
    match: AccessibilityNodeInfo.() -> Boolean,
): AccessibilityNodeInfo? = wechatRoots().firstNotNullOfOrNull { root ->
    root.findNodeOrNull(match)
}

internal fun AutoOperationService.findPickerNodeOrNull(
    match: AccessibilityNodeInfo.() -> Boolean,
): AccessibilityNodeInfo? = pickerRoots().firstNotNullOfOrNull { root ->
    root.findNodeOrNull(match)
}

/**
 * 只读取窗口根节点的 className，确认当前是否已经是微信原生相册页。
 *
 * AlbumPreviewUI 的图片树可能非常大，读取整棵树会让动作线程长时间阻塞；
 * 对当前 Xiaomi/微信版本，窗口类名已经足够区分相册页，因此所有动作都应先
 * 走这个快速判断，再决定是否需要进一步读取节点。
 */
internal fun AutoOperationService.isWechatPhotoPickerContext(): Boolean {
    // 不读取 windows.root：每个 root 都可能触发微信整棵图片无障碍树的 Binder
    // 查询。当前活动窗口的根节点类名已足够识别页面。
    val currentClass = rootInActiveWindow
        ?.takeIf { isWechatPackage(it.packageName) }
        ?.className
        ?.toString()
        .orEmpty()
    return currentClass.let { name ->
        name.contains("AlbumPreviewUI", ignoreCase = true) ||
                name.contains("AlbumUI", ignoreCase = true) ||
                name.contains("PhotoPicker", ignoreCase = true) ||
                name.contains("PickerUI", ignoreCase = true)
    }
}

/**
 * 返回微信相册顶部当前目录标题。
 *
 * AlbumPreviewUI 在当前微信版本同时包含目录列表和图片网格，树中偶尔会保留
 * 不在当前页面的目录节点。因此不能只用“能找到 aaos”判断页面；顶部标题才是
 * 当前真正选中的目录。微信当前版本的节点顺序中，顶部标题先于目录行/缩略图；
 * 找到第一个可见标题即可，避免每次探针都收集并遍历整棵大型图片树。
 */
internal fun AutoOperationService.pickerFolderTitleOrNull(): String? {
    for (root in pickerRoots()) {
        val title = root.findNodeOrNull {
            isVisibleToUser &&
                    (text?.toString() in PICKER_FOLDER_TITLES ||
                            contentDescription?.toString() in PICKER_FOLDER_TITLES)
        } ?: continue
        return title.text?.toString()
            ?.takeIf { it in PICKER_FOLDER_TITLES }
            ?: title.contentDescription?.toString()
                ?.takeIf { it in PICKER_FOLDER_TITLES }
    }
    return null
}

internal fun AutoOperationService.hasWechatWindow(): Boolean =
    rootInActiveWindow?.packageName?.toString() == WECHAT_PACKAGE_NAME ||
            windows.any { it.root?.packageName?.toString() == WECHAT_PACKAGE_NAME }

internal fun AutoOperationService.hasPickerWindow(): Boolean = hasWechatWindow()

internal fun AutoOperationService.isGalleryPickerVisible(event: AccessibilityEvent): Boolean {
    // 微信扫一扫的“相册”打开的是微信自己的 AlbumUI/PhotoPickerUI。
    // MIUI Gallery 不是本责任链的目标：即使它产生事件，也不能把它当成
    // 已进入选择器，否则后续坐标会误点 MIUI 的“影集”等控件。
    if (event.packageName?.toString()?.let { it != WECHAT_PACKAGE_NAME } == true) {
        return false
    }

    val currentRoot = rootInActiveWindow
    val currentPackage = currentRoot?.packageName?.toString()
    val wechatWindow = currentPackage == WECHAT_PACKAGE_NAME
    if (!wechatWindow && event.packageName?.toString() != WECHAT_PACKAGE_NAME) {
        return false
    }

    if (event.isGalleryUiEvent()) return true

    val currentClassName = currentRoot?.className?.toString().orEmpty()
    if (currentClassName.let { name ->
            name.contains("AlbumUI", ignoreCase = true) ||
                    name.contains("PhotoPicker", ignoreCase = true) ||
                    name.contains("PickerUI", ignoreCase = true) ||
                    name.contains("AlbumPreviewUI", ignoreCase = true)
        }) return true

    // 延迟探针携带的 event 可能仍是 BaseScanUI 的旧事件，因此页面判定
    // 必须优先看当前微信窗口中的选择器节点，而不是只看 event 类名。
    // 仅在类名和事件都无法识别时才读取节点树；正常的 AlbumPreviewUI 会在
    // 上面的快速路径返回，不要让大图片树阻塞责任链。
    return if (wechatWindow) {
        currentRoot?.findNodeOrNull {
            text?.toString() in setOf("所有图片", "aaos", "QRCode") ||
                    contentDescription?.toString()?.startsWith("图片") == true ||
                    viewIdResourceName == "com.tencent.mm:id/f5"
        } != null
    } else false
}

internal fun AutoOperationService.isFolderListVisible(event: AccessibilityEvent): Boolean {
    if (!isGalleryPickerVisible(event)) return false
    if (findPickerNodeOrNull {
            isVisibleToUser &&
                    (text?.toString() == "aaos" || text?.toString() == "QRCode")
        } != null
    ) return true

    // 兼容微信将文件夹列表绘制在 AlbumUI 中而不暴露文字节点的版本。
    val name = event.uiClassName()
    if (name.contains("AlbumUI", ignoreCase = true) ||
        name.contains("AlbumPreviewUI", ignoreCase = true)
    ) {
        // AlbumPreviewUI 在当前微信版本同时承载“所有图片”下拉后的文件夹列表。
        // 只有确认没有图片网格时才将它当成文件夹页，避免跳过 aaos 目录。
        return !isPhotoGridVisible(event)
    }

    // 延迟探针的 event 可能是旧的 BaseScanUI，仍以当前微信选择器窗口
    // 为准；没有图片网格时，当前页只能是文件夹列表。
    return !isPhotoGridVisible(event)
}

internal fun AutoOperationService.isPhotoGridVisible(event: AccessibilityEvent): Boolean =
    isGalleryPickerVisible(event) && findPickerNodeOrNull {
        isVisibleToUser &&
                (contentDescription?.toString()?.startsWith("图片") == true ||
                        viewIdResourceName == "com.tencent.mm:id/micro_thumb")
    } != null

internal fun AutoOperationService.tapPickerRatio(
    xRatio: Float,
    yRatio: Float,
    callback: AccessibilityService.GestureResultCallback? = null,
): Boolean {
    // resources.displayMetrics 在 Android 14 上可能只返回去掉状态栏、导航栏后的
    // 应用可用区域（本机约 1080x2316），而 dispatchGesture 使用的是物理屏幕
    // 坐标。扫一扫和微信相册都是全屏页面，因此必须按 realMetrics 的
    // 1080x2460 计算触点，否则底部相册按钮会被点高一百多像素。
    val realMetrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
        ?.getRealMetrics(realMetrics)
    val width = realMetrics.widthPixels.takeIf { it > 0 }
        ?: resources.displayMetrics.widthPixels
    val height = realMetrics.heightPixels.takeIf { it > 0 }
        ?: resources.displayMetrics.heightPixels
    val bounds = Rect(0, 0, width, height)
    return tap(
        bounds.left + bounds.width() * xRatio,
        bounds.top + bounds.height() * yRatio,
        callback = callback,
    )
}

internal fun AutoOperationService.tapWechatRatio(
    xRatio: Float,
    yRatio: Float,
    callback: AccessibilityService.GestureResultCallback? = null,
): Boolean = tapPickerRatio(xRatio, yRatio, callback)

internal fun AutoOperationService.tapPickerNodeCenter(
    node: AccessibilityNodeInfo,
): Boolean {
    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    return if (bounds.isEmpty) false else tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
}

/**
 * 对 dispatchGesture 的返回值不做页面切换承诺。手势提交后只安排一次无障碍探针，
 * 下一次执行仍会检查页面状态；这样不会把旧的扫一扫页误当成相册页。
 */
internal class PickerRetryState(
    private val actionName: String,
    private val maxAttempts: Int = 12,
    private val retryIntervalMs: Long = 850L,
    private val probeDelayMs: Long = 350L,
) {
    private var attempts = 0
    private var lastGestureAt = 0L
    private var probeScheduled = false

    fun reset() {
        attempts = 0
        lastGestureAt = 0L
        probeScheduled = false
    }

    fun scheduleProbe(service: AutoOperationService, event: AccessibilityEvent) {
        if (probeScheduled) return
        probeScheduled = true
        service.resumeServiceDelay(event, probeDelayMs) {
            probeScheduled = false
        }
    }

    fun dispatch(
        service: AutoOperationService,
        event: AccessibilityEvent,
        xRatio: Float,
        yRatio: Float,
        onGestureResult: ((String) -> Unit)? = null,
    ): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        val remaining = retryIntervalMs - (now - lastGestureAt)
        if (remaining > 0L) {
            scheduleProbe(service, event)
            return false
        }
        if (attempts >= maxAttempts) {
            throw IllegalStateException("$actionName 页面切换超时，已重试 $attempts 次")
        }
        attempts++
        lastGestureAt = now
        val accepted = service.tapWechatRatio(
            xRatio,
            yRatio,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription) {
                    onGestureResult?.invoke("completed")
                }

                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription) {
                    onGestureResult?.invoke("cancelled")
                }
            },
        )
        scheduleProbe(service, event)
        return accepted
    }
}
