package com.glassbrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.glassbrowser.R

/**
 * 网站标志（favicon）—— 专门修"裂图"。
 *
 * 直接 `<img src=...>` 或没有兜底的图片加载，在站点没有图标 / favicon 404 时
 * 会画一个破图方块（肉眼看到的就是一个小横杠）。
 * 这里 placeholder + error + fallback 三重兜底，任何情况都显示内置地球图标。
 */
@Composable
fun FaviconImage(
    url: String?,
    modifier: Modifier = Modifier,
) {
    val fallback = painterResource(R.drawable.ic_globe)

    if (url.isNullOrBlank()) {
        Image(
            painter = fallback,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Fit,
        )
        return
    }

    AsyncImage(
        model = url,
        contentDescription = null,
        placeholder = fallback,
        error = fallback,        // ← 加载失败自动回退地球图标
        fallback = fallback,
        contentScale = ContentScale.Fit,
        modifier = modifier.clip(RoundedCornerShape(3.dp)),
    )
}
