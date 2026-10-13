package com.glassbrowser.core

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 地址栏要展示 / 交互的全部状态。
 *
 * 单独抽出来是为了：WebView 的状态变化（onPageStarted/Finished/onProgressChanged）
 * 能直接驱动 Compose 重组，而不必把 WebView 实例塞进 UI 层。
 */
@Stable
class BrowserState {
    /** 当前页面 URL（始终是完整地址） */
    var url by mutableStateOf("")

    /** 页面标题（SPA 会在加载后改，所以要能覆盖） */
    var title by mutableStateOf<String?>(null)

    /** favicon（站点声明的图标地址，取不到就由 UI 层回退内置图标） */
    var favicon by mutableStateOf<String?>(null)

    /** 加载进度 0..100 */
    var progress by mutableStateOf(100)

    /** 是否在加载 */
    val isLoading: Boolean get() = progress in 1..99

    /** 能否后退（返回键要用） */
    var canGoBack by mutableStateOf(false)

    /** UI 侧操作 WebView 的回调，由 WebView 封装层注入 */
    var goBackHandler: (() -> Boolean)? = null
        private set

    var reloadHandler: (() -> Unit)? = null
        private set

    fun attachWebViewBindings(
        goBack: () -> Boolean,
        reload: () -> Unit,
    ) {
        goBackHandler = goBack
        reloadHandler = reload
    }

    fun goBack(): Boolean = goBackHandler?.invoke() ?: false
}

/** 让 BrowserState 能通过 CompositionLocal 下发（避免层层传参） */
val LocalBrowserState = staticCompositionLocalOf<BrowserState> {
    error("BrowserState 未提供")
}
