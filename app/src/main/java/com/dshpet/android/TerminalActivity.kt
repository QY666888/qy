package com.dshpet.android

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** MT 式脚本输出页：一次执行一个 su 进程，退出后显示退出码并回收。 */
class TerminalActivity : Activity() {
    private lateinit var logView: TextView
    private lateinit var statusView: TextView
    private lateinit var input: EditText
    private lateinit var send: Button
    private var runner: ScriptRunner? = null
    private var ended = false

    private val lineListener: (String) -> Unit = { line ->
        runOnUiThread {
            logView.append("$line\n")
            statusView.text = "执行中"
            autoScroll()
        }
    }

    private val exitListener: (Int) -> Unit = { code ->
        runOnUiThread {
            ended = true
            statusView.text = "已结束，回车关闭"
            logView.append("[进程已结束 - 退出码 $code]\n")
            input.isEnabled = false
            send.isEnabled = false
            autoScroll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF101418.toInt())
        }
        statusView = TextView(this).apply {
            text = "正在启动 su..."
            setTextColor(0xFF8899AA.toInt())
            textSize = 12f
        }
        logView = TextView(this).apply {
            setTextColor(0xFFD7E0E8.toInt())
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        input = EditText(this).apply {
            hint = "菜单数字或命令，回车发送"
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF778899.toInt())
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendCurrent()
                    true
                } else false
            }
        }
        send = Button(this).apply { text = "发送" }
        send.setOnClickListener { sendCurrent() }

        val scroll = ScrollView(this)
        scroll.addView(logView)
        root.addView(statusView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(input)
        root.addView(send)
        setContentView(root)

        val cmd = intent.getStringExtra("run_cmd")
        if (cmd.isNullOrBlank()) {
            lineListener("没有要执行的命令")
            ended = true
            statusView.text = "已结束"
            input.isEnabled = false
            send.isEnabled = false
            return
        }

        runner = ScriptRunner(lineListener, exitListener).also {
            if (!it.start(cmd)) {
                ended = true
                statusView.text = "启动失败"
                input.isEnabled = false
                send.isEnabled = false
            }
        }
    }

    private fun sendCurrent() {
        if (ended) {
            finish()
            return
        }
        val cmd = input.text.toString().trim()
        if (cmd.isEmpty()) return
        runner?.write(cmd)
        input.setText("")
        autoScroll()
    }

    private fun autoScroll() {
        (logView.parent as? ScrollView)?.post {
            (logView.parent as ScrollView).fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        runner?.stop()
        runner = null
        super.onDestroy()
    }
}
