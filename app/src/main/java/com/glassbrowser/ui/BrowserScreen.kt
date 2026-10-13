package com.glassbrowser.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.glassbrowser.core.BrowserState
import com.glassbrowser.core.SearchEngine
import com.glassbrowser.core.UrlResolver
import com.glassbrowser.glass.GlassTier
import com.glassbrowser.glass.rememberGlassTier
import com.glassbrowser.web.BrowserWebView
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 整屏结构：Scaffold(顶栏=功能区) + WebView(网页显示区)。
 *
 * 布局约束（按需求）：
 *  · **网页不能被顶栏遮挡** → WebView 用 `Modifier.padding(innerPadding)` 让出顶栏高度；
 *  · 玻璃要有东西可采样 → 背后铺一层全屏渐变，`hazeSource` 挂在这一层上；
 *  · 如果想让"网页延伸到顶栏下方"（Chrome 那种沉浸效果），把 [pageUnderBar] 设为 true，
 *    此时采样源切换成包裹 WebView 的容器（代价：WebView 是独立 View+自有图层，
 *    部分机型上实时采样可能糊不出来，所以默认关闭）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: BrowserState,
    engine: SearchEngine,
    tierOverride: GlassTier?,
    pageUnderBar: Boolean,
    onNavigate: (UrlResolver.NavTarget) -> Unit,
    onSettingsClick: () -> Unit,
    onOpenExternal: (Uri) -> Unit,
) {
    val tier = rememberGlassTier(forced = tierOverride)
    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize()) {

        // ① 背景层：全屏渐变。玻璃从这里取样 —— 极重要，纯色/纯黑的背景上"看不出模糊"，
        //    会让用户以为玻璃没生效。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF16202B),
                        0.45f to Color(0xFF0D141B),
                        1f to Color(0xFF070A0D),
                    )
                )
                .hazeSource(hazeState),      // ← 采样源（与 hazeEffect 是兄弟节点，禁止互相嵌套）
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // ★ 必须透明：容器一旦有实心底色，背后的模糊就被盖住，玻璃彻底失效
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                TopAppBar(
                    title = {
                        GlassSearchBar(
                            url = state.url,
                            title = state.title,
                            faviconUrl = state.favicon,
                            engine = engine,
                            tier = tier,
                            hazeState = hazeState,
                            onNavigate = onNavigate,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    // 搜索栏旁边的设置键（需求 二.4）
                    navigationIcon = {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(48.dp),   // ≥48dp 触摸目标
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "设置",
                                tint = Color.White.copy(alpha = 0.92f),
                            )
                        }
                    },
                    expandedHeight = 64.dp,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                    // 状态栏内边距交给它，别再手写 padding(top = 24.dp)，否则必然错位
                    windowInsets = TopAppBarDefaults.windowInsets,
                )
            },
        ) { innerPadding ->

            Box(modifier = Modifier.fillMaxSize()) {

                // ② 网页区
                val webModifier = if (pageUnderBar) {
                    // 沉浸模式：网页铺满，采样源改为 WebView 所在容器
                    Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                } else {
                    // 默认：让出顶栏高度 → 网页绝不被遮挡
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                }

                BrowserWebView(
                    state = state,
                    onOpenExternal = onOpenExternal,
                    modifier = webModifier,
                )

                // ③ 加载进度条：贴在顶栏下沿，细到不打扰，但看得见
                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .padding(innerPadding)
                            .align(Alignment.TopCenter)
                            .zIndex(1f),
                        color = Color(0xFF8AB4F8),
                        trackColor = Color.Transparent,
                    )
                }
            }
        }
    }
}
