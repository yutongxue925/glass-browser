package com.glassbrowser.core

/**
 * 搜索引擎定义。
 *
 * 与设置页共用同一份数据：设置页只存 [key]，跳转时用 [key] 反查，避免"设置改了不生效"。
 */
enum class SearchEngine(
    val key: String,
    val label: String,
    /** 模板里的 %s 会被替换成 URL 编码后的关键词 */
    val template: String,
) {
    BING("bing", "必应", "https://www.bing.com/search?q=%s"),
    BAIDU("baidu", "百度", "https://www.baidu.com/s?wd=%s"),
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s");

    /** 组装最终搜索 URL。 */
    fun searchUrl(query: String): String = template.replace("%s", UrlResolver.encodeQuery(query))

    companion object {
        val DEFAULT = BING

        /** 容错反查：未知 key 一律回落到默认引擎，绝不抛异常。 */
        fun fromKey(key: String?): SearchEngine =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}
