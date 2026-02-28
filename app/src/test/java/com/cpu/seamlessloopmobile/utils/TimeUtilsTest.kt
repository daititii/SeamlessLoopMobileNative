package com.cpu.seamlessloopmobile.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TimeUtils 时间换算魔法工具箱的单元测试喵！
 * 确保每一帧、每一毫秒都算得精准无误喵！(๑•̀ㅂ•́)و✧
 */
class TimeUtilsTest {

    @Test
    fun testFormatTime() {
        // 44100 采样率下，44100 帧就是 1 秒喵
        val oneSecondFrames = 44100L
        assertEquals("00:01", TimeUtils.formatTime(oneSecondFrames, 44100L))

        // 测试整点分钟：60 秒 * 44100 = 2646000 帧喵！
        val oneMinuteFrames = 2646000L
        assertEquals("01:00", TimeUtils.formatTime(oneMinuteFrames, 44100L))

        // 测试包含分钟和秒的高级情况：1分30秒
        val oneMinThirtySec = (60L + 30L) * 44100L
        assertEquals("01:30", TimeUtils.formatTime(oneMinThirtySec, 44100L))

        // 测试防摔保护机制：即使大意传了 0 的采样率，也会被兜底拯救喵！
        assertEquals("00:01", TimeUtils.formatTime(44100L, 0L))
    }

    @Test
    fun testFormatTimeMs() {
        // 1 分 1 秒 500 毫秒 = (60k + 1k + 500) = 61500 毫秒
        // 转换成采样点 = 61500 * (44100 / 1000) = 61500 * 44.1 = 2712150 帧喵
        assertEquals("01:01.500", TimeUtils.formatTimeMs(2712150L, 44100L))

        // 极限考验：只有短短的几毫秒！
        // 10 毫秒 = 10 * 44.1 = 441 帧喵
        assertEquals("00:00.010", TimeUtils.formatTimeMs(441L, 44100L))
    }

    @Test
    fun testSamplesToMillis() {
        // 44100 帧 = 1000 毫秒喵
        val samples = 44100L
        val sampleRate = 44100L
        val expectedMillis = 1000L

        val result = TimeUtils.samplesToMillis(samples, sampleRate)
        assertEquals(expectedMillis, result)

        // 即使给了 0 的采样率也要稳如老狗喵
        val resultZeroRate = TimeUtils.samplesToMillis(44100L, 0L)
        assertEquals(1000L, resultZeroRate)
        
        // 测试不同的采样率：48000
        val samples48k = 48000L
        val result48k = TimeUtils.samplesToMillis(samples48k, 48000L)
        assertEquals(1000L, result48k)
    }

    @Test
    fun testMillisToSamples() {
        // 1000 毫秒应重构出 44100 个细胞（帧）喵
        val millis = 1000L
        val sampleRate = 44100L
        val expectedSamples = 44100L

        val result = TimeUtils.millisToSamples(millis, sampleRate)
        assertEquals(expectedSamples, result)

        // 测试不同的采样率喵
        val millisIn48k = 500L
        val result48k = TimeUtils.millisToSamples(millisIn48k, 48000L)
        // 半秒 = 24000 帧喵
        assertEquals(24000L, result48k)
    }
}
