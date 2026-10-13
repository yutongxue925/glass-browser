package com.glassbrowser.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect

/**
 * 把"液态玻璃"抽象成一个 modifier —— 调用方只写一行，
 * 三档降级、模糊参数、描边高光全在这里收口。
 *
 * 用法：
 * ```
 * val tier = rememberGlassTier()
 * val haze  = rememberHazeState()
 * Box(
 *     Modifier
 *         .glassSurface(tier = tier, hazeState = haze, shape = RoundedCornerShape(24.dp))
 *         .size(48.dp)
 * )
 * ```
 *
 * **玻璃生效的必要条件**（少一个就完全没效果，见 docs/踩坑与规避清单.md）：
 *  1. 背后有内容并挂了 `Modifier.hazeSource(hazeState)`；
 *  2. 玻璃容器自身必须**透明**（有实心底色就把模糊盖住了）；
 *  3. `hazeSource` 与 `hazeEffect` 不能是父子关系；
 *  4. 两者必须共享同一个 [HazeState]。
 */
@Composable
fun rememberGlassTier(
    forced: GlassTier? = null,
): GlassTier {
    val context = LocalContext.current
    // 只在 forced 变化时重算，避免每帧查 ActivityManager
    return remember(forced) { GlassTier.detect(context, forced) }
}

/**
 * 玻璃表面。按档位自动切换实现：
 *  · Full / Reduced → Haze 实时模糊（半径不同）
 *  · Scrim        → 半透明纯色，零模糊、零离屏分配
 */
fun Modifier.glassSurface(
    tier: GlassTier,
    hazeState: HazeState,
    shape: Shape,
    blurRadius: Dp = 24.dp,
    tint: Color = Color(0xFF101418).copy(alpha = 0.28f),
): Modifier = this
    .clip(shape)
    .then(
        if (tier.supportsRealTimeBlur) {
            Modifier.hazeEffect(state = hazeState) {
                // ⚠️ Haze 2.x 必须显式写 blurEffect 块；不写就是"有 modifier 没效果"
                blurEffect {
                    // Reduced 档把半径砍半：RenderEffect 的开销与半径大致正相关
                    blurRadius = if (tier == GlassTier.Full) blurRadius else blurRadius / 2
                }
            }
        } else {
            Modifier
        }
    )
    // 底色：Full 档更透（让模糊内容透出来），Scrim 档更实（保证文字可读）
    .background(
        when (tier) {
            GlassTier.Full -> tint.copy(alpha = tint.alpha * 0.6f)
            GlassTier.Reduced -> tint
            GlassTier.Scrim -> tint.copy(alpha = 0.42f)
        }
    )
    // 顶部高光描边：廉价但很关键，这是"玻璃"观感的一半
    .border(
        width = 0.8.dp,
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.35f),
            0.5f to Color.White.copy(alpha = 0.08f),
            1f to Color.White.copy(alpha = 0.16f),
        ),
        shape = shape,
    )
