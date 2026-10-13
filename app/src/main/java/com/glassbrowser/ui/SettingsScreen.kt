package com.glassbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.glassbrowser.core.SearchEngine
import com.glassbrowser.glass.GlassTier

/**
 * 设置页（纯 Compose，无 XML）。
 *
 * 三块：
 *  1. 默认搜索引擎（改完立刻生效，地址栏下一次搜索就用新引擎）
 *  2. 玻璃效果档位（自动 / 强制三档）—— 低端机用户可手动关掉实时模糊换流畅
 *  3. 网页延伸（glass 采样源切换）+ 跳系统"默认应用"设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentEngine: SearchEngine,
    currentTierOverride: GlassTier?,
    detectedTier: GlassTier,
    pageUnderBar: Boolean,
    onEngineChange: (SearchEngine) -> Unit,
    onTierOverrideChange: (GlassTier?) -> Unit,
    onPageUnderBarChange: (Boolean) -> Unit,
    onOpenSystemDefaultApps: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("默认搜索引擎")
            SearchEngine.entries.forEach { engine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = engine == currentEngine,
                            onClick = { onEngineChange(engine) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = engine == currentEngine, onClick = null)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(engine.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider()
            SectionTitle("玻璃效果")
            Text(
                text = "当前设备自动判定为：${detectedTier.cnLabel()}（${detectedTier.explain()}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            listOf<GlassTier?>(null, GlassTier.Full, GlassTier.Reduced, GlassTier.Scrim).forEach { tier ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = tier == currentTierOverride,
                            onClick = { onTierOverrideChange(tier) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = tier == currentTierOverride, onClick = null)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(if (tier == null) "自动（按设备能力）" else tier.cnLabel())
                }
            }

            HorizontalDivider()
            SectionTitle("网页显示")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPageUnderBarChange(!pageUnderBar) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("网页延伸到顶栏下方", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "开启后玻璃会直接模糊网页内容（更沉浸，但部分机型可能糊不出来）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = pageUnderBar, onCheckedChange = onPageUnderBarChange)
            }

            HorizontalDivider()
            SectionTitle("系统")
            Button(
                onClick = onOpenSystemDefaultApps,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("打开系统「默认应用」设置（设为默认浏览器）")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

private fun GlassTier.cnLabel(): String = when (this) {
    GlassTier.Full -> "完整（实时模糊 24dp）"
    GlassTier.Reduced -> "精简（实时模糊 12dp）"
    GlassTier.Scrim -> "无模糊（半透明纯色）"
}

private fun GlassTier.explain(): String = when (this) {
    GlassTier.Full -> "Android 13+ 且非低内存设备"
    GlassTier.Reduced -> "Android 12/12L 或低内存设备"
    GlassTier.Scrim -> "Android 11 及以下，或强制关闭"
}
