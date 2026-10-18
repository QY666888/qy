package com.qy.pet.root

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 交互式 su 行终端：保持一个 su 会话，输入命令后实时回显输出。
 * 不是完整 PTY（无颜色/光标控制），但可以喂命令、回车、运行菜单脚本的选项。
 */
object RootShell {

    @Volatile private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: Thread? = null
    private val listeners = mutableListOf<(String) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    val isReady: Boolean get() = process?.isAlive == true

    @Synchronized
    fun start() {
        if (isReady) return
        runCatching {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            process = p
            writer = OutputStreamWriter(p.outputStream)
            writer?.write("export PATH=/system/bin:/system/xbin:/sbin:/vendor/bin:/data/local/tmp:${'$'}PATH\n")
            writer?.flush()

            reader = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(p.inputStream)).forEachLine { line ->
                        emit(line)
                    }
                }
                emit("[shell 已退出]")
            }.also { it.isDaemon = true; it.start() }
        }.onFailure { emit("[启动 su 失败: ${it.message}]") }
    }

    fun addListener(l: (String) -> Unit) {
        synchronized(listeners) { listeners.add(l) }
    }

    fun removeListener(l: (String) -> Unit) {
        synchronized(listeners) { listeners.remove(l) }
    }

    private fun emit(line: String) {
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
