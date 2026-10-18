package com.qy.pet.root

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 交互式 su 行终端：保持一个 su 会话，输入命令后实时回显输出。
 * 这是“可用菜单版”：不是完整 PTY，但支持 read 数字菜单、回车输入、TERM 设置和 ANSI 清洗。
 */
object RootShell {

    @Volatile private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: Thread? = null
    private val listeners = mutableListOf<(String) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    // ANSI/VT 控制序列：颜色码、光标码、OSC 标题等；可用版先清洗，避免乱码方块。
    private val ansiRegex = Regex(
        "\u001B\\[[0-9;?]*[ -/]*[@-~]" +
            "|\u001B\\][^\u0007]*\u0007" +
            "|\u001B[()][A-Za-z0-9]" +
            "|\r"
    )

    val isReady: Boolean get() = process?.isAlive == true

    @Synchronized
    fun start() {
        if (isReady) return
        runCatching {
            // 不用 su -i（KSU/Magisk 不一定支持）；关键是保持 stdin 常开、设置 TERM。
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            process = p
            writer = OutputStreamWriter(p.outputStream)
            writer?.apply {
                write("export TERM=xterm\n")
                write("export PATH=/system/bin:/system/xbin:/sbin:/vendor/bin:/data/local/tmp:${'$'}PATH\n")
                flush()
            }

            reader = Thread {
                runCatching {
                    val br = BufferedReader(InputStreamReader(p.inputStream))
                    val buffer = StringBuilder()
                    var lastActive = System.currentTimeMillis()
                    while (true) {
                        if (br.ready()) {
                            val c = br.read()
                            if (c == -1) break
                            lastActive = System.currentTimeMillis()
                            when (val ch = c.toChar()) {
                                '\n' -> { emit(stripAnsi(buffer.toString())); buffer.clear() }
                                '\r' -> Unit
                                else -> buffer.append(ch)
                            }
                        } else {
                            // 没有换行的 read 提示也能尽快显示出来。
                            if (buffer.isNotEmpty() && System.currentTimeMillis() - lastActive > 250) {
                                emit(stripAnsi(buffer.toString()))
                                buffer.clear()
                            }
                            Thread.sleep(15)
                        }
                    }
                    if (buffer.isNotEmpty()) emit(stripAnsi(buffer.toString()))
                }
                emit("[shell 已退出]")
            }.also { it.isDaemon = true; it.start() }
        }.onFailure { emit("[启动 su 失败: ${it.message}]") }
    }

    private fun stripAnsi(text: String): String =
        ansiRegex.replace(text, "").trimEnd()

    fun addListener(l: (String) -> Unit) {
        synchronized(listeners) { listeners.add(l) }
    }

    fun removeListener(l: (String) -> Unit) {
        synchronized(listeners) { listeners.remove(l) }
    }

    private fun emit(line: String) {
        if (line.isEmpty()) return
        synchronized(listeners) {
            listeners.toList().forEach { cb -> main.post { cb(line) } }
        }
    }

    fun send(cmd: String) {
        start()
        if (!isReady) {
            emit("[无法启动 su，请先在 KernelSU 授权本应用]")
            return
        }
        emit("nya# $cmd")
        runCatching {
            writer?.write(cmd + "\n")
            writer?.flush()
        }.onFailure { emit("[写入失败: ${it.message}]") }
    }

    fun stop() {
        runCatching { writer?.write("exit\n"); writer?.flush() }
        runCatching { process?.destroy() }
        process = null
        writer = null
        reader = null
    }
}
