package com.glassbrowser

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glassbrowser.core.BrowserState
import com.glassbrowser.core.LocalBrowserState
import com.glassbrowser.core.UrlResolver
import com.glassbrowser.glass.GlassTier
import com.glassbrowser.settings.BrowserPrefs
import com.glassbrowser.settings.SystemSettings
import com.glassbrowser.ui.BrowserScreen
import com.glassbrowser.ui.GlassBrowserTheme
import com.glassbrowser.ui.SettingsScreen
import androidx.compose.runtime.CompositionLocalProvider

/** 应用入口：这里只做"持有全局依赖"这一件事，不写业务 */
class GlassBrowserApp : Application()

/**
 * 单 Activity 架构：
 *   浏览器界面 ⇄ 设置界面（Compose 内切换，不引入 Navigation 依赖，减少一处出错点）
 */
class MainActivity : ComponentActivity() {

    private val prefs by lazy { BrowserPrefs(this) }
    private val browserState = BrowserState()

    private var webView: WebView? = null

    /** 冷启动时 Intent 先到、WebView 还没建好，先存起来等 factory 回调 */
    private var pendingUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GlassBrowserTheme {
                BrowserApp(
                    state = browserState,
                    prefs = prefs,
                    onWebViewReady = { wv ->
                        webView = wv
                        // WebView 建好后补发冷启动时没能加载的链接
                        consumePendingUrl()?.let(wv::loadUrl)
                    },
                    onNavigate = ::navigate,
                    onOpenExternal = ::openExternal,
                )
            }
        }

        handleIntent(intent)
    }

    /** singleTask：App 在后台时点外部链接走这里。**不重写就是"App 起来了页面没变"** */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * 外部 Intent 入口：默认浏览器 / 全局搜索 / 分享文本。
     * 三种都要接，否则"设为默认浏览器"后点链接会转一圈又回到系统。
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val url: String? = when (intent.action) {
            Intent.ACTION_VIEW ->
                intent.data?.toString()?.takeIf { it.isNotBlank() }?.let(UrlResolver::normalize)

            Intent.ACTION_WEB_SEARCH ->
                intent.getStringExtra(android.app.SearchManager.QUERY)
                    ?.let { UrlResolver.resolve(it, prefs.engine.value)?.url }

            Intent.ACTION_SEND ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                    // 分享过来的常是"一句话 + 一个链接"，从里面揪出第一个 http(s) URL
                    FIRST_URL.find(text)?.value?.let(UrlResolver::normalize)
                        ?: UrlResolver.resolve(text, prefs.engine.value)?.url
                }

            else -> null
        }
        url?.let(::loadUrl)
    }

    private fun navigate(target: UrlResolver.NavTarget) {
        when (target) {
            is UrlResolver.NavTarget.ToUrl -> loadUrl(target.url)
            is UrlResolver.NavTarget.ToSearch -> loadUrl(target.url)
        }
    }

    private fun loadUrl(url: String) {
        val wv = webView ?: run { pendingUrl = url; return }
        wv.loadUrl(url)
    }

    private fun openExternal(uri: android.net.Uri) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    /** 供 WebView factory 回调后补发 pendingUrl */
    private fun consumePendingUrl(): String? = pendingUrl?.also { pendingUrl = null }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private companion object {
        val FIRST_URL = Regex("https?://\\S+")
    }
}

@Composable
private fun MainActivity.BrowserApp(
    state: BrowserState,
    prefs: BrowserPrefs,
    onWebViewReady: (WebView) -> Unit,
    onNavigate: (UrlResolver.NavTarget) -> Unit,
    onOpenExternal: (android.net.Uri) -> Unit,
) {
    val engine by prefs.engine.collectAsStateWithLifecycle()
    val tierOverride by prefs.tierOverride.collectAsStateWithLifecycle()
    val pageUnderBar by prefs.pageUnderBar.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }

    // 返回键：优先交给网页自己（能后退就后退），不能后退且不在设置页才退出 App
    BackHandler(enabled = true) {
        when {
            showSettings -> showSettings = false
            state.goBack() -> Unit
            else -> onBackPressedDispatcher.onBackPressed()
        }
    }

    CompositionLocalProvider(LocalBrowserState provides state) {
        if (showSettings) {
            SettingsScreen(
                currentEngine = engine,
                currentTierOverride = tierOverride,
                detectedTier = GlassTier.detect(this),
                pageUnderBar = pageUnderBar,
                onEngineChange = prefs::setEngine,
                onTierOverrideChange = prefs::setTierOverride,
                onPageUnderBarChange = prefs::setPageUnderBar,
                onOpenSystemDefaultApps = { SystemSettings.openDefaultBrowserSettings(this) },
                onBack = { showSettings = false },
            )
        } else {
            BrowserScreen(
                state = state,
                engine = engine,
                tierOverride = tierOverride,
                pageUnderBar = pageUnderBar,
                onNavigate = onNavigate,
                onSettingsClick = { showSettings = true },
                onOpenExternal = onOpenExternal,
            )
        }
    }
}

/** 说明：设置改为在 Compose 内切换（showSettings），因此不需要 Navigation 依赖 */
