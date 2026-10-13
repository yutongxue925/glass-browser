package com.glassbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glassbrowser.core.SearchEngine
import com.glassbrowser.core.UrlResolver
import com.glassbrowser.glass.GlassTier
import com.glassbrowser.glass.glassSurface
import dev.chrisbanes.haze.HazeState

/**
 * 液态玻璃搜索栏（纯 Compose，无 EditText / 无 XML 布局）。
 *
 * 行为对齐安卓默认浏览器：
 *  · 未聚焦 → 显示 `标题 · 域名`，左侧 favicon（失败回退地球，不裂图）
 *  · 点一下 → 变成输入框，内容换成**完整 URL 并全选**
 *  · 右侧 ✓ / 输入法 Go 键 / Search 键 → 同一个 submit()：网址直跳、文本按设置里的引擎搜索
 *  · 失焦 → 恢复展示文本
 *
 * Material 3 对齐规范（与系统设置页一致）：
 *  栏高 48dp、圆角 24dp（整圆）、内容左右 12dp 内边距、与设置键间距 8dp
 */
@Composable
fun GlassSearchBar(
    url: String,
    title: String?,
    faviconUrl: String?,
    engine: SearchEngine,
    tier: GlassTier,
    hazeState: HazeState,
    onNavigate: (UrlResolver.NavTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var editing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    // 输入法动作键随内容自适应：像网址给"前往(Go)"，否则给"搜索(Search)"
    val imeAction = if (UrlResolver.looksLikeUrl(field.text)) ImeAction.Go else ImeAction.Search

    // 未聚焦时把"标题 · 域名"同步进输入框（页面加载完标题会变）
    LaunchedEffect(focused, url, title) {
        if (!focused) {
            editing = false
            field = TextFieldValue(UrlResolver.displayText(url, title), TextRange.Zero)
        }
    }

    fun beginEdit() {
        if (!focused) {
            editing = true
            field = TextFieldValue(url, TextRange(0, url.length))
            focusRequester.requestFocus()
        }
    }

    fun submit() {
        // 没编辑就直接点 ✓ = 重新加载当前页（与默认浏览器一致）
        val input = if (editing) field.text else url
        val target = UrlResolver.resolve(input, engine)
        focusManager.clearFocus()
        if (target != null) onNavigate(target)   // 空输入不导航，保持当前页
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            // 玻璃：统一走 glassSurface，三档降级都在里面（Scrim 档自动变半透明纯色）
            .glassSurface(
                tier = tier,
                hazeState = hazeState,
                shape = RoundedCornerShape(24.dp),
            )
            // 点栏身任意位置（除 ✓）→ 进入编辑；indication = null 去掉水波纹，贴合浏览器观感
            .clickable(interactionSource = interaction, indication = null) { beginEdit() }
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = "地址和搜索栏" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FaviconImage(url = faviconUrl, modifier = Modifier.size(18.dp))

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = field,
                onValueChange = {
                    editing = true
                    field = it
                },
                singleLine = true,          // ★ 必须：不加的话回车会换行，动作键永不触发
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.95f)),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onGo = { submit() },        // IME_ACTION_GO
                    onSearch = { submit() },    // IME_ACTION_SEARCH
                    onDone = { submit() },      // 部分输入法只给 Done，一并兜住
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            if (field.text.isEmpty()) {
                Text(
                    text = "搜索或输入网址",
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // 最右边的对勾：确认跳转 / 搜索（视觉 32dp，热区由父 Row 的 clickable 补齐）
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "前往",
            tint = Color.White.copy(alpha = 0.96f),
            modifier = Modifier
                .size(32.dp)
                .glassSurface(
                    tier = tier,
                    hazeState = hazeState,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable { submit() }
                .padding(7.dp),
        )
    }
}
