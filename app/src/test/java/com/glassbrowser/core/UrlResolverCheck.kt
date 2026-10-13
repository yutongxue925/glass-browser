package com.glassbrowser.core

/**
 * UrlResolver 行为验证（可独立运行：java -jar urlresolver-check.jar）。
 * 不依赖 JUnit，避免为了跑一次验证去拉测试框架。
 */
private var pass = 0
private var fail = 0

private fun T(name: String, cond: Boolean) {
    if (cond) { pass++; println("  \u2713 $name") } else { fail++; println("  \u2717 FAIL: $name") }
}

private fun urlOf(input: String): String? =
    (UrlResolver.resolve(input) as? UrlResolver.NavTarget.ToUrl)?.url

private fun searchOf(input: String, engine: SearchEngine = SearchEngine.BING): String? =
    (UrlResolver.resolve(input, engine) as? UrlResolver.NavTarget.ToSearch)?.url

fun main() {
    println("== 1. 裸域名必须识别为网址（原 Bug 现场）==")
    T("yutongxue.cc.cd → https", urlOf("yutongxue.cc.cd") == "https://yutongxue.cc.cd")
    T("www.baidu.com → https", urlOf("www.baidu.com") == "https://www.baidu.com")
    T("a.b.c.d.e.com 多级域名", urlOf("a.b.c.d.e.com") == "https://a.b.c.d.e.com")
    T("my-site.example.co.uk", urlOf("my-site.example.co.uk") == "https://my-site.example.co.uk")

    println("== 2. 带协议的输入原样放行 ==")
    T("http:// 原样", urlOf("http://example.com") == "http://example.com")
    T("https:// 原样", urlOf("https://example.com/a?b=1#c") == "https://example.com/a?b=1#c")
    T("大写协议", urlOf("HTTP://EXAMPLE.COM") == "HTTP://EXAMPLE.COM")
    T("带端口+路径", urlOf("https://a.example.com:8443/p?q=1") == "https://a.example.com:8443/p?q=1")

    println("== 3. 中文/空格/纯数字 → 搜索 ==")
    T("神秘海函数 → 搜索", searchOf("神秘海函数")!!.startsWith("https://www.bing.com/search?q="))
    T("中文被正确 URL 编码", searchOf("神秘海函数")!!.endsWith("%E7%A5%9E%E7%A7%98%E6%B5%B7%E5%87%BD%E6%95%B0"))
    T("how to use kotlin（含空格）→ 搜索", searchOf("how to use kotlin") != null)
    T("123 → 搜索", searchOf("123") != null)
    T("abc（无点）→ 搜索", searchOf("abc") != null)
    T("version 2.0 → 搜索", searchOf("version 2.0") != null)
    T("1.5 → 搜索（不误判为域名）", searchOf("1.5") != null)
    T("文件.txt → 搜索（TLD 非字母）", searchOf("文件.txt") != null)

    println("== 4. IP / 本地地址 ==")
    T("127.0.0.1 → http://（回环用 http）", urlOf("127.0.0.1") == "http://127.0.0.1")
    T("192.168.1.1 → http://", urlOf("192.168.1.1") == "http://192.168.1.1")
    T("localhost:8080 → http://", urlOf("localhost:8080") == "http://localhost:8080")
    T("10.0.0.5:3000 → http://", urlOf("10.0.0.5:3000") == "http://10.0.0.5:3000")
    T("8.8.8.8（公网 IP）→ https://", urlOf("8.8.8.8") == "https://8.8.8.8")
    T("example.com:8080 → https://（不能被当成协议）", urlOf("example.com:8080") == "https://example.com:8080")
    T("EXAMPLE.COM:8080 大写同理", urlOf("EXAMPLE.COM:8080") == "https://EXAMPLE.COM:8080")
    T("192.168.0.10:9090/path → http://", urlOf("192.168.0.10:9090/path") == "http://192.168.0.10:9090/path")
    T("999.999.999.999 → 搜索（段越界）", searchOf("999.999.999.999") != null)
    T("256.1.1.1 → 搜索", searchOf("256.1.1.1") != null)

    println("== 5. 非法/危险输入 ==")
    T("javascript: 不当网址", searchOf("javascript:alert(1)") != null)
    T("intent:// 不当网址", searchOf("intent://scan/#Intent;scheme=zxing;end") != null)
    T("file:/// 不当网址", searchOf("file:///etc/passwd") != null)
    T("mailto: 不当网址", searchOf("mailto:a@b.com") != null)
    T("未知协议 myapp:// 不当网址", searchOf("myapp://open?id=1") != null)
    T("纯空白 → null（不导航）", UrlResolver.resolve("   ") == null)
    T("空串 → null", UrlResolver.resolve("") == null)
    T("null → null", UrlResolver.resolve(null) == null)
    T("非法端口 example.com:abc → 搜索", searchOf("example.com:abc") != null)
    T("端口越界 example.com:99999 → 搜索", searchOf("example.com:99999") != null)

    println("== 6. 脏输入清洗（从网页复制来的地址）==")
    T("首尾空格", urlOf("  yutongxue.cc.cd  ") == "https://yutongxue.cc.cd")
    T("零宽字符 + BOM", urlOf("\u200bhttps://example.com\ufeff") == "https://example.com")
    T("不换行空格", urlOf("yutongxue.cc.cd\u00a0") == "https://yutongxue.cc.cd")
    T("全角空格 → 搜索", searchOf("神秘海函数\u3000") != null)

    println("== 7. 引擎分发（设置里改了要立刻生效）==")
    val baidu = searchOf("神秘海函数", SearchEngine.BAIDU)!!
    T("百度模板生效", baidu.startsWith("https://www.baidu.com/s?wd="))
    T("Google 模板生效", searchOf("kotlin", SearchEngine.GOOGLE)!!.startsWith("https://www.google.com/search?q=kotlin"))
    T("DuckDuckGo 模板生效", searchOf("kotlin", SearchEngine.DUCKDUCKGO)!!.contains("duckduckgo.com"))
    T("未知 key 回落默认引擎", SearchEngine.fromKey("nope") == SearchEngine.DEFAULT)
    T("key 大小写不敏感", SearchEngine.fromKey("BAIDU") == SearchEngine.BAIDU)
    T("未知 key 时仍能拼出可用地址", searchOf("test", SearchEngine.fromKey("nope")) != null)

    println("== 8. 编码与展示 ==")
    T("空格编码为 %20 而非 +", UrlResolver.encodeQuery("a b") == "a%20b")
    T("保留 -._~", UrlResolver.encodeQuery("-._~") == "-._~")
    T("# 被编码", UrlResolver.encodeQuery("a#b") == "a%23b")
    T("中文编码", UrlResolver.encodeQuery("中") == "%E4%B8%AD")
    T("stripScheme 去协议", UrlResolver.stripScheme("https://a.com/") == "a.com")
    T("displayText 标题·域名", UrlResolver.displayText("https://a.com", "标题") == "标题  ·  a.com")
    T("标题==域名时只显示域名", UrlResolver.displayText("https://a.com", "a.com") == "a.com")
    T("标题为空只显示域名", UrlResolver.displayText("https://a.com", "") == "a.com")

    println("== 9. 输入法动作键的自适应判定 ==")
    T("输入网址 → 用 Go 键", UrlResolver.looksLikeUrl("yutongxue.cc.cd"))
    T("输入文本 → 用 Search 键", !UrlResolver.looksLikeUrl("神秘海函数"))
    T("半途输入 yutongxue 判为搜索", !UrlResolver.looksLikeUrl("yutongxue"))
    // 故意保守：TLD 还没输入完的域名（yutongxue.）判为搜索词，
    // 免得把半截域名丢给 WebView 打开一个必然报错的页面。
    T("半途输入 yutongxue. 判为搜索（TLD 未完成）", !UrlResolver.looksLikeUrl("yutongxue."))
    T("输完 yutongxue.c 判为搜索（TLD 不足两位）", !UrlResolver.looksLikeUrl("yutongxue.c"))
    T("输完 yutongxue.cc 判为网址", UrlResolver.looksLikeUrl("yutongxue.cc"))

    println("\n结果: $pass 通过, $fail 失败")
    if (fail > 0) kotlin.system.exitProcess(1)
}
