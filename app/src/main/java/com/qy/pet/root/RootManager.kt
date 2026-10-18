package com.qy.pet.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 一次性 Root 命令：用于文件管理等不需要交互的操作。
 * 交互式终端由 RootShell 单独管理，避免互相影响。
 */
object RootManager {

    data class CmdResult(
        val code: Int,
        val out: String,
        val err: String,
    ) {
        val success get() = code == 0
    }

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun runSu(merged: String, timeoutMs: Long = 20_000L): CmdResult {
        return try {
            val p = ProcessBuilder("su", "-c", merged).start()
            runCatching { p.outputStream.close() }

            val out = StringBuilder()
            val err = StringBuilder()
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
                p.destroyForcibly()
                tOut.join(500); tErr.join(500)
                return CmdResult(124, "", "命令超时")
            }
            tOut.join(); tErr.join()
            CmdResult(p.exitValue(), out.toString().trim(), err.toString().trim())
        } catch (e: Exception) {
            CmdResult(-1, "", e.message ?: "su 启动失败")
        }
    }

    suspend fun exec(vararg cmd: String): CmdResult = withContext(Dispatchers.IO) {
        runSu(cmd.joinToString(" && "))
    }

    suspend fun execRaw(command: String, timeoutMs: Long = 60_000L): CmdResult =
        withContext(Dispatchers.IO) { runSu(command, timeoutMs) }

    fun isRootAvailable(): Boolean = runSu("id", 5_000L).success

    suspend fun listNames(path: String): List<String> =
        exec("ls -1p ${quote(path)}").out.lines().filter { it.isNotBlank() }

    suspend fun readFile(path: String): String = exec("cat ${quote(path)}").out

    suspend fun makeDir(path: String): CmdResult = exec("mkdir -p ${quote(path)}")

    suspend fun delete(path: String): CmdResult = exec("rm -rf ${quote(path)}")

    suspend fun rename(from: String, to: String): CmdResult = exec("mv ${quote(from)} ${quote(to)}")

    suspend fun copy(src: String, dstDir: String): CmdResult = exec("cp -a ${quote(src)} ${quote(dstDir)}")

    suspend fun move(src: String, dstDir: String): CmdResult = exec("mv ${quote(src)} ${quote(dstDir)}")

    suspend fun chmod(path: String, mode: String = "0755"): CmdResult = exec("chmod $mode ${quote(path)}")

    fun join(parent: String, child: String): String {
        val p = parent.trimEnd('/')
        val c = child.trimStart('/')
        return if (p.isEmpty()) "/$c" else "$p/$c"
    }
}
