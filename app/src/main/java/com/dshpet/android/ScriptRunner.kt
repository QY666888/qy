package com.dshpet.android

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * MT 式一次性脚本执行器：一次 `su -c` 只负责当前脚本；
 * 脚本退出后进程立即结束并回收，不长期持有 root shell。
 */
class ScriptRunner(
    private val onLine: (String) -> Unit,
    private val onExit: (Int) -> Unit,
) {
    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var reader: Thread? = null

    @Volatile private var alive = false

    private val controlRegex = Regex(
        "\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B[()][A-Za-z0-9]|\u001B\\][^\u0007]*\u0007|\r"
    )

    fun start(command: String): Boolean = runCatching {
        val p = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .apply {
                environment()["PATH"] = "/system/bin:/system/xbin:/sbin:/vendor/bin:/su/bin:/data/local/tmp"
            }
            .start()
        process = p
        stdin = p.outputStream
        alive = true

        reader = Thread {
            val reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
            try {
                while (alive) {
                    val line = reader.readLine() ?: break
                    emitLine(line)
                }
            } catch (_: Exception) {
            } finally {
                val code = runCatching { p.waitFor() }.getOrDefault(-1)
                alive = false
                closeStreams()
                onExit(code)
            }
        }.also { it.isDaemon = true; it.start() }
        true
    }.getOrElse {
        alive = false
        closeStreams()
        onLine("[su 启动失败：${it.message ?: "未知错误"}]")
        false
    }

    fun write(text: String) {
        runCatching {
            stdin?.write((text + "\n").toByteArray(Charsets.UTF_8))
            stdin?.flush()
        }.onFailure { onLine("[输入写入失败：${it.message}]") }
    }

    fun stop() {
        alive = false
        runCatching { process?.destroyForcibly() }
        closeStreams()
        process = null
        stdin = null
        reader = null
    }

    private fun emitLine(raw: String) {
        val line = controlRegex.replace(raw, "").trimEnd()
        if (line.isNotEmpty()) onLine(line)
    }

    private fun closeStreams() {
        runCatching { stdin?.close() }
        runCatching { process?.inputStream?.close() }
        process = null
        stdin = null
    }
}
