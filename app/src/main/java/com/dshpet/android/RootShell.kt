package com.dshpet.android

import android.os.Handler
import android.os.Looper

/** PTY root shell：页面持有一个会话，退出后销毁，避免 su 进程残留。 */
object RootShell {
    private var session: PtySession? = null
    private val listeners = mutableListOf<(String) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    val isReady: Boolean get() = session != null

    @Synchronized
    fun start() {
        if (session != null) return
        val pty = PtySession(
            onLine = { emit(it) },
            onExit = {
                val ended = session != null
                session = null
                if (ended) emit("[进程已结束，可直接关闭页面]")
            },
        )
        if (pty.start()) session = pty else {
            pty.destroy()
            emit("[无法创建 PTY，请先在 KernelSU 授权本应用]")
        }
    }

    fun addListener(l: (String) -> Unit) = synchronized(listeners) { listeners.add(l) }
    fun removeListener(l: (String) -> Unit) = synchronized(listeners) { listeners.remove(l) }

    private fun emit(line: String) {
        synchronized(listeners) {
            listeners.toList().forEach { cb -> main.post { cb(line) } }
        }
    }

    fun send(cmd: String) {
        start()
        if (session == null) return
        runCatching { session?.write(cmd) }.onFailure { emit("[写入失败: ${it.message}]") }
    }

    @Synchronized
    fun stop() {
        session?.destroy()
        session = null
    }
}
