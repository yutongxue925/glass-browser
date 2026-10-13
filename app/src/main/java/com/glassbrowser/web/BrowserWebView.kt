package com.glassbrowser.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.glassbrowser.core.BrowserState
import java.util.Locale

/**
 * Compose 里的 WebView 封装。
 *
 * 职责：
 *  · 创建/配置 WebView（Compose 通过 AndroidView 互操作，这是官方唯一姿势）
 *  · 把加载进度 / 标题 / favicon / 可后退 回写给 [BrowserState] → 顶部地址栏实时更新
 *  · 修正"点链接不跳转"的两个经典雷（见 shouldOverrideUrlLoading）
 *  · 把 WebView 的返回/刷新能力交给外部（返回键、刷新按钮）
 *
 * 注意：WebView 不是 Composable，是 View。想让它做玻璃的**取样源**时，
 * 要把 hazeSource 挂在**包裹它的 Box** 上，而不是 AndroidView 自身（见 BrowserScreen）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    state: BrowserState,
    onOpenExternal: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    onWebViewReady: (WebView) -> Unit = {},
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                configureWebView(state, onOpenExternal)
                onWebViewReady(this)
            }
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureWebView(
    state: BrowserState,
    onOpenExternal: (Uri) -> Unit,
) {
    settings.apply {
        javaScriptEnabled = true                 // 现代站点没这个直接白屏
        domStorageEnabled = true                 // localStorage / sessionStorage，同上
        databaseEnabled = false                  // 多数站点不需要，关掉省内存
        loadsImagesAutomatically = true
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false              // 别显示那个丑的 +/- 悬浮控件
        setSupportMultipleWindows(false)
        mediaPlaybackRequiresUserGesture = true  // 移动网络下别自动播视频
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        textZoom = 100                           // 跟随系统字号但别被站点放大搞乱
    }
    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

    webViewClient = object : WebViewClient() {

        /**
         * 雷 ①：返回 true = "这次导航我接管了"，如果你只是想放行必须 **false**，
         *      否则就是"点了没反应"。
         * 雷 ②：API 24+ 走的是 WebResourceRequest 这个重载；只重写已废弃的 String 版本
         *      等于没有拦截逻辑。两个都收口到同一个函数。
         */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            handleUrl(request.url, onOpenExternal)

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            handleUrl(Uri.parse(url), onOpenExternal)

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            state.url = url
            state.progress = 0
            state.canGoBack = view.canGoBack()
            state.favicon = faviconUrlOf(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            state.url = url
            state.title = view.title
            state.progress = 100
            state.canGoBack = view.canGoBack()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // 只处理主框架错误：子资源（统计脚本、头像）失败不该影响地址栏
            if (!request.isForMainFrame) return
            state.progress = 100
        }
    }

    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            state.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            state.title = title
            state.canGoBack = view?.canGoBack() == true
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            // 站点自己声明的图标，比 /favicon.ico 更准；这里只标记一次刷新
            state.favicon = faviconUrlOf(view?.url ?: state.url)
        }
    }

    // 把返回/刷新能力交给 UI 层（返回键、刷新按钮用）
    state.attachWebViewBindings(
        goBack = {
            if (canGoBack()) {
                goBack()
                true
            } else {
                false
            }
        },
        reload = { reload() },
    )

    // 主页：留空比乱设一个站点更合适，用户点搜索栏输入即可
    loadUrl(HOME)
}

/** 导航放行/拦截的唯一收口 */
private fun handleUrl(uri: Uri, onOpenExternal: (Uri) -> Unit): Boolean =
    when (uri.scheme?.lowercase(Locale.ROOT)) {
        // 放行：交回 WebView 自己加载（不要在这里再调 loadUrl，会重复加载）
        "http", "https", "about", "data", "blob", "file", "content" -> false
        // mailto: / tel: / intent: / 自定义 scheme → 交给系统
        else -> {
            onOpenExternal(uri)
            true
        }
    }

/** origin + /favicon.ico；失败由 UI 层的地球图标兜底（绝不裂图） */
internal fun faviconUrlOf(pageUrl: String?): String? =
    runCatching {
        val uri = Uri.parse(pageUrl.orEmpty())
        val scheme = uri.scheme ?: return null
        val authority = uri.authority ?: return null
        "$scheme://$authority/favicon.ico"
    }.getOrNull()

internal const val HOME = "about:blank"
