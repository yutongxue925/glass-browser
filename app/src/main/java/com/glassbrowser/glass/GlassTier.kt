package com.glassbrowser.glass

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * 玻璃效果的质量档位 —— **降级方案的核心**。
 *
 * 为什么需要它：实时高斯模糊依赖 `RenderEffect`（Android 12 / API 31 引入），
 * 而且**内存小的设备（isLowRamDevice）即使系统版本够，跑整条工具栏的实时模糊也容易掉帧甚至 OOM**。
 * iOS 26 的液态玻璃在旧硬件上是被系统直接关掉的，Android 这边没有系统级开关，
 * 所以必须由 App 自己分层。
 *
 * 三档行为：
 *  · [Full]    Android 13+ 且非低内存：真实时模糊（24dp），最接近液态玻璃
 *  · [Reduced] Android 12/12L 或厂商低内存但版本够：轻模糊（12dp）+ 更实的底色
 *  · [Scrim]   其余（Android 11 及以下 / 检测不到 RenderEffect）：**完全不做模糊**，
 *              只用半透明纯色 + 描边，零离屏分配，不崩不卡
 *
 * 判断依据不是"我觉得"，而是两个可验证信号：`Build.VERSION.SDK_INT` 与 `ActivityManager.isLowRamDevice`。
 */
enum class GlassTier {
    Full,
    Reduced,
    Scrim;

    val supportsRealTimeBlur: Boolean get() = this != Scrim

    companion object {

        /**
         * 按设备能力自动选档。
         *
         * @param forced 用户在设置页显式指定的档位（null = 自动）
         */
        fun detect(context: Context, forced: GlassTier? = null): GlassTier {
            if (forced != null) return forced

            val sdk = Build.VERSION.SDK_INT
            val lowRam = runCatching {
                context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
            }.getOrDefault(false)

            return when {
                // Android 13 起：AGSL 可用、RenderEffect 成熟，是各玻璃库公认的"最佳环境"
                sdk >= Build.VERSION_CODES.TIRAMISU && !lowRam -> Full
                // Android 12 / 12L：RenderEffect 有了，但缺少部分能力与优化，降一档跑
                sdk >= Build.VERSION_CODES.S && !lowRam -> Reduced
                // 其余一律不做实时模糊
                else -> Scrim
            }
        }

        /** 设备是否真的支持实时模糊（Android 12+）—— 兜底判断，给自定义实现用 */
        fun isBlurSupportedByPlatform(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
