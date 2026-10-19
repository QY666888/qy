package com.dshpet.android

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 单个 PTY 会话：页面打开时创建，页面关闭或脚本退出后销毁。
 */
class PtySession(
    private val onLine: (String) -> Unit,
    private val onExit: () -> Unit,
) {
    private var handle: Long = 0L
    private var pfd: ParcelFileDescriptor? = null
    private var output: FileOutputStream? = null
    private var input: FileInputStream? = null
    private var reader: Thread? = null

    @Volatile private var alive = false

    private val ansiRegex = Regex(
        "\u001B\\[[0-9;?]*[ -/]*[@-~]" +
            "|\u001B\\][^\u0007]*\u0007" +
            "|\u001B[()][A-Za-z0-9]" +
            "|\r"
    )

    fun start(): Boolean = runCatching {
        handle = nativeStart()
        if (handle <= 0) return false
        val masterFd = (handle and 0xffffffffL).toInt()
        pfd = ParcelFileDescriptor.adoptFd(masterFd)
        output = pfd?.fileOutputStream
        input = pfd?.fileInputStream
        alive = true

        reader = Thread {
            val buffer = StringBuilder()
            val chunk = ByteArray(4096)
            var lastActive = System.currentTimeMillis()
            try {
                while (alive) {
                    val available = input?.available() ?: 0
                    if (available > 0) {
                        val n = input!!.read(chunk, 0, minOf(available, chunk.size))
                        if (n < 0) break
                        lastActive = System.currentTimeMillis()
                        buffer.append(String(chunk, 0, n, Charsets.UTF_8))
                        while (buffer.contains('\n')) {
                            val idx = buffer.indexOf('\n')
                            emitLine(buffer.substring(0, idx))
                            buffer.delete(0, idx + 1)
                        }
                    } else {
                        if (buffer.isNotEmpty() && System.currentTimeMillis() - lastActive > 250) {
                            emitLine(buffer.toString())
                            buffer.clear()
                        }
                        Thread.sleep(15)
                    }
                }
                if (buffer.isNotEmpty()) emitLine(buffer.toString())
            } catch (_: Exception) {
            } finally {
                alive = false
                onExit()
                release()
            }
        }.also { it.isDaemon = true; it.start() }
        true
    }.getOrElse {
        release()
        false
    }

    private fun emitLine(raw: String) {
        val line = ansiRegex.replace(raw, "").trimEnd()
        if (line.isNotEmpty()) onLine(line)
    }

    fun write(text: String) {
        runCatching {
            output?.write((text + "\n").toByteArray(Charsets.UTF_8))
            output?.flush()
        }
    }

    fun destroy() = release()

    private fun release() {
        alive = false
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { pfd?.close() }
        if (handle > 0) nativeDestroy(handle)
        handle = 0
        reader = null
        input = null
        output = null
        pfd = null
    }

    companion object {
        init { System.loadLibrary("dshpty") }
        external fun nativeStart(): Long
        external fun nativeDestroy(handle: Long)
    }
}
