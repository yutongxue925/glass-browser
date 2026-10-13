package com.glassbrowser.core

/**
 * 地址栏输入的语义解析：**网址 → 跳转；文本 → 按设置里的搜索引擎搜索**。
 *
 * 设计原则（对应"输入 yutongxue.cc.cd 不跳转"这类 Bug 的根因）：
 *  1. 不用 `startsWith("http")` 判断 —— 裸域名（yutongxue.cc.cd）必须识别为网址；
 *  2. 不做过度宽松的"含点即网址" —— `1.5`、`version 2.0` 这类会被误判；
 *  3. 中文字符/空白出现 → 一定是搜索词（避免"神秘海函数"被当域名）；
 *  4. 非 http/https 协议（javascript:、intent:、file:）**不当网址**，防 XSS / 误跳转；
 *  5. 私网地址补 http://（https 会握手失败），公网裸域名补 https://；
 *  6. 任何输入都不会抛异常，最坏情况回落到"搜索"。
 *
 * 本文件是**纯 Kotlin**（不依赖任何 Android API），因此可以直接跑 JVM 单测；
 * Android 侧可再叠加 `Patterns.WEB_URL` 做二次兜底（见 docs 说明）。
 */
object UrlResolver {

    /** 带 http/https 协议 */
    private val HAS_HTTP = Regex("^https?://", RegexOption.IGNORE_CASE)

    /** 任意其它协议（ftp: / mailto: / javascript: / intent: …） */
    private val ANY_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:", RegexOption.IGNORE_CASE)

    /** 空白 / 全角空格 / CJK —— 出现即判为搜索词 */
    private val WS_OR_CJK = Regex("[\\s\\u3000\\u00a0\\u4e00-\\u9fff\\u3400-\\u4dbf\\uf900-\\ufaff\\uff01-\\uff5e]")

    /** 宽松域名：标签(a-z0-9-)+点，TLD 纯字母 ≥2，可选端口/路径/查询/锚点 */
    private val DOMAIN = Regex(
        "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}" +
        "(?::\\d{1,5})?" +
        "(?:[/?#]\\S*)?$"
    )

    private val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    // ---------------------------------------------------------------- 结果模型

    sealed interface NavTarget {
        /** 直接打开这个 URL */
        data class ToUrl(val url: String) : NavTarget

        /** 用指定引擎搜索；url 是拼好的最终地址 */
        data class ToSearch(val query: String, val url: String, val engine: SearchEngine) : NavTarget
    }

    // ---------------------------------------------------------------- 对外 API

    /**
     * 解析用户输入。
     * @return null 表示空输入 —— 调用方应保持当前页面不动（不要导航到 about:blank）
     */
    fun resolve(raw: String?, engine: SearchEngine = SearchEngine.DEFAULT): NavTarget? {
        val v = clean(raw)
        if (v.isEmpty()) return null
        return if (looksLikeUrl(v)) NavTarget.ToUrl(normalize(v))
        else NavTarget.ToSearch(query = v, url = engine.searchUrl(v), engine = engine)
    }

    /** 这个输入像不像网址（不产生任何副作用，可用于实时切换输入法动作键）。 */
    fun looksLikeUrl(raw: String?): Boolean {
        val v = clean(raw)
        if (v.isEmpty()) return false

        if (HAS_HTTP.containsMatchIn(v)) return hasHostAfterScheme(v)
        if (looksLikeOtherScheme(v)) return false            // ftp:/javascript: 等：不当作网址
        if (WS_OR_CJK.containsMatchIn(v)) return false       // 有中文/空格：是搜索词

        val hostPort = v.substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostPort.isEmpty()) return false

        val host = hostOf(hostPort) ?: return false
        return isLocalhost(host) || isIpv4(host) || DOMAIN.matches(v)
    }

    /** 补全协议并去掉首尾空白，得到可直接交给 WebView 的地址。 */
    fun normalize(raw: String?): String {
        val v = clean(raw)
        if (v.isEmpty()) return v
        if (HAS_HTTP.containsMatchIn(v)) return v

        val hostPort = v.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = hostOf(hostPort)
        val scheme = if (host != null && isPrivateOrLoopback(host)) "http://" else "https://"
        return scheme + v
    }

    /** 地址栏未聚焦时的展示文本：`标题 · 域名`（标题为空或等于域名时只显示域名）。 */
    fun displayText(url: String?, title: String?): String {
        val shown = stripScheme(url)
        val t = title?.trim().orEmpty()
        return if (t.isNotEmpty() && t != shown) "$t  ·  $shown" else shown
    }

    /** 去掉协议与结尾多余的 '/'，得到适合放进一行地址栏的短地址。 */
    fun stripScheme(url: String?): String {
        var u = url?.trim().orEmpty()
        u = u.removePrefix("https://").removePrefix("http://")
        while (u.length > 1 && u.endsWith("/")) u = u.dropLast(1)
        return u
    }

    /** 等价 JS 的 encodeURIComponent：空格编码成 %20（不是 +，避免被当成加号）。 */
    fun encodeQuery(query: String): String {
        val sb = StringBuilder()
        for (b in query.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c in UNRESERVED) sb.append(c)
            else sb.append('%').append("%02X".format(b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- 内部实现

    /** 去掉首尾空白 + 零宽字符 / BOM / 不换行空格（从网页复制来的地址常带这些，是"复制了却跳不过去"的常见原因）。 */
    fun clean(raw: String?): String =
        (raw ?: "").trim().replace(Regex("[\\u200b\\u200c\\u200d\\ufeff\\u00a0]"), "")

    private fun hasHostAfterScheme(v: String): Boolean {
        val rest = v.replaceFirst(HAS_HTTP, "")
        val hostPort = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostPort.isEmpty()) return false
        val host = hostOf(hostPort) ?: return false
        return isLocalhost(host) || isIpv4(host) || DOMAIN.matches(rest)
    }

    /**
     * 是不是"真正的其它协议"。
     *
     * 坑：`localhost:8080`、`example.com:8080` 也能被 `scheme:` 的正则匹配上，
     * 但它们其实是 **host:port**，不能一棍子打死（这正是"输入地址不跳转"的一个隐形原因）。
     * 判定规则：冒号后是纯数字（端口）且冒号前不是已知协议名 → 按 host:port 继续走；
     * 否则（mailto: / intent: / javascript: / 自定义 scheme）一律不当网址。
     */
    private fun looksLikeOtherScheme(v: String): Boolean {
        val m = ANY_SCHEME.find(v) ?: return false
        val scheme = v.substring(0, m.value.length - 1)
        val rest = v.substring(m.value.length)
        val head = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val portLike = head.isNotEmpty() && head.all { it.isDigit() }
        if (KNOWN_SCHEMES.contains(scheme.lowercase()) || !portLike) return true
        return false
    }

    private val KNOWN_SCHEMES = setOf(
        "http", "https", "ftp", "ftps", "mailto", "tel", "sms", "smsto",
        "intent", "javascript", "data", "file", "content", "about", "blob", "ws", "wss",
    )

    /** 拆分 host:port；端口非法（非数字 / 越界）返回 null。 */
    private fun hostOf(hostPort: String): String? {
        val idx = hostPort.lastIndexOf(':')
        if (idx < 0) return hostPort.ifEmpty { null }
        val host = hostPort.substring(0, idx)
        val port = hostPort.substring(idx + 1)
        if (host.isEmpty() || port.isEmpty() || !port.all { it.isDigit() }) return null
        val p = port.toIntOrNull() ?: return null
        return if (p in 1..65535) host else null
    }

    private fun isLocalhost(host: String): Boolean = host.equals("localhost", ignoreCase = true)

    /** 严格 IPv4：四段且每段 0..255（`999.1.1.1` 会被判为搜索词，与 Chrome 一致）。 */
    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { p ->
            p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() } &&
                (p.toIntOrNull() ?: return false) in 0..255
        }
    }

    /** 私网 / 回环：这些地址用 https 多半握手失败，按 Chrome 习惯补 http:// */
    private fun isPrivateOrLoopback(host: String): Boolean {
        if (isLocalhost(host)) return true
        if (!isIpv4(host)) return false
        val (a, b) = host.split('.').let { it[0].toInt() to it[1].toInt() }
        return a == 127 || a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254)
    }
}
