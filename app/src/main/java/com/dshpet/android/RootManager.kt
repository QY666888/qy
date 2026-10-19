package com.dshpet.android

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** 一次性 root 文件命令；交互式终端由 RootShell 单独管理。 */
object RootManager {
    data class CmdResult(val code: Int, val out: String, val err: String) {
        val success get() = code == 0
    }

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun runSu(merged: String, timeoutMs: Long = 20_000L): CmdResult {
        return try {
            val p = ProcessBuilder("su", "-c", merged).start()
            runCatching { p.outputStream.close() }
            val out = StringBuilder(); val err = StringBuilder()
            val tOut = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(p.inputStream)).forEachLine {
                        synchronized(out) { out.appendLine(it) }
                    }
                }
            }
            val tErr = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(p.errorStream)).forEachLine {
                        synchronized(err) { err.appendLine(it) }
                    }
                }
            }
            tOut.start(); tErr.start()
            val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) {
                p.destroyForcibly(); tOut.join(500); tErr.join(500)
                return CmdResult(124, "", "命令超时")
            }
            tOut.join(); tErr.join()
            CmdResult(p.exitValue(), out.toString().trim(), err.toString().trim())
        } catch (e: Exception) {
            CmdResult(-1, "", e.message ?: "su 启动失败")
        }
    }

    fun isRootAvailable(): Boolean = runSu("id", 5_000L).success
    fun listNames(path: String): List<String> =
        runSu("ls -1p ${quote(path)}").out.lines().filter { it.isNotBlank() }
    fun readFile(path: String): String = runSu("cat ${quote(path)}").out
    fun chmod(path: String, mode: String = "0755"): CmdResult = runSu("chmod $mode ${quote(path)}")
    fun join(parent: String, child: String): String {
        val p = parent.trimEnd('/'); val c = child.trimStart('/')
        return if (p.isEmpty()) "/$c" else "$p/$c"
    }
}
