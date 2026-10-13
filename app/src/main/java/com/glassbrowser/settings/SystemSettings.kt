package com.glassbrowser.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 打开系统设置页（默认浏览器 / 默认应用 / 本应用详情）。
 *
 * ROM 差异很大，部分厂商会砍掉其中某些入口 —— **不 try/catch 就是点一下闪退**。
 * 逐个候选降级：默认浏览器 → 默认应用 → 应用详情 → 系统设置首页。
 */
object SystemSettings {

    fun openDefaultBrowserSettings(context: Context) {
        val candidates = buildList {
            add(Intent(Settings.ACTION_MANAGE_DEFAULT_BROWSER_APPLICATION))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            add(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // 换下一个
            } catch (_: SecurityException) {
                // 某些 ROM 权限拦截，继续降级
            }
        }

        // 全失败也别闪退
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}
