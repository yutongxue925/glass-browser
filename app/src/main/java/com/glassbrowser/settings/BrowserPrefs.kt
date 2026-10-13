package com.glassbrowser.settings

import android.content.Context
import com.glassbrowser.core.SearchEngine
import com.glassbrowser.glass.GlassTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置持久化：默认搜索引擎 + 玻璃档位 + 网页是否延伸到顶栏下方。
 *
 * 用 StateFlow 暴露，Compose 侧 `collectAsStateWithLifecycle()` 订阅，
 * 设置页改完立刻生效（不需要重启，这也是"设置改了不生效"那类 Bug 的解药）。
 */
class BrowserPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("glass_browser", Context.MODE_PRIVATE)

    private val _engine = MutableStateFlow(
        SearchEngine.fromKey(prefs.getString(KEY_ENGINE, null))
    )
    val engine: StateFlow<SearchEngine> = _engine.asStateFlow()

    private val _tierOverride = MutableStateFlow(readTier())
    val tierOverride: StateFlow<GlassTier?> = _tierOverride.asStateFlow()

    private val _pageUnderBar = MutableStateFlow(prefs.getBoolean(KEY_PAGE_UNDER_BAR, false))
    val pageUnderBar: StateFlow<Boolean> = _pageUnderBar.asStateFlow()

    fun setEngine(engine: SearchEngine) {
        prefs.edit().putString(KEY_ENGINE, engine.key).apply()
        _engine.value = engine
    }

    /** null = 自动按设备能力选档 */
    fun setTierOverride(tier: GlassTier?) {
        prefs.edit().apply {
            if (tier == null) remove(KEY_TIER) else putString(KEY_TIER, tier.name)
        }.apply()
        _tierOverride.value = tier
    }

    fun setPageUnderBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAGE_UNDER_BAR, enabled).apply()
        _pageUnderBar.value = enabled
    }

    private fun readTier(): GlassTier? =
        prefs.getString(KEY_TIER, null)?.let { name ->
            GlassTier.entries.firstOrNull { it.name == name }
        }

    private companion object {
        const val KEY_ENGINE = "search_engine_key"
        const val KEY_TIER = "glass_tier_override"
        const val KEY_PAGE_UNDER_BAR = "page_under_bar"
    }
}
