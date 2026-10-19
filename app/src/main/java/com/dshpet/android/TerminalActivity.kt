package com.dshpet.android

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 交互式 PTY 终端；页面关闭或脚本退出后释放 su 进程。 */
class TerminalActivity : Activity() {
    private lateinit var logView: TextView
    private lateinit var statusView: TextView
    private lateinit var input: EditText
    private var ended = false

    private val listener: (String) -> Unit = { line ->
        runOnUiThread {
            logView.append("$line\n")
            if (line.startsWith("[进程已结束")) {
                ended = true
                statusView.text = "已结束，回车关闭"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RootShell.start()
        RootShell.addListener(listener)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF101418.toInt())
        }
        statusView = TextView(this).apply {
            text = "PTY 已连接"
            setTextColor(0xFF8899AA.toInt())
            textSize = 12f
        }
        logView = TextView(this).apply {
            setTextColor(0xFFD7E0E8.toInt())
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        input = EditText(this).apply {
            hint = "输入命令或菜单选项，回车发送"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF778899.toInt())
        }
        val send = Button(this).apply { text = "发送" }
        send.setOnClickListener { sendCurrent() }

        val scroll = ScrollView(this)
        scroll.addView(logView)
        root.addView(statusView)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.MATCH_PARENT, 0, 1f))
        root.addView(input)
        root.addView(send)
        setContentView(root)

        intent.getStringExtra("run_cmd")?.let { RootShell.send(it) }
    }

    private fun sendCurrent() {
        if (ended) { finish(); return }
        val cmd = input.text.toString().trim()
        if (cmd.isEmpty()) return
        RootShell.send(cmd)
        input.setText("")
    }

    override fun onDestroy() {
        RootShell.removeListener(listener)
        RootShell.stop()
        super.onDestroy()
    }
}
