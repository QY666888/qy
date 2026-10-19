package com.dshpet.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.io.File

/** 简单 root 文件浏览：点目录进入，点 .sh 交给 PTY 终端执行。 */
class FileBrowserActivity : Activity() {
    private lateinit var pathView: TextView
    private lateinit var list: ListView
    private var current = "/"
    private val items = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = intent.getStringExtra("path") ?: "/"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF101216.toInt())
        }
        pathView = TextView(this).apply {
            setTextColor(0xFF7FD4FF.toInt()); textSize = 14f
        }
        val refresh = Button(this).apply { text = "刷新" }
        refresh.setOnClickListener { load() }
        list = ListView(this).apply {
            setBackgroundColor(0xFF1B2029.toInt())
        }
        root.addView(pathView)
        root.addView(refresh)
        root.addView(list)
        setContentView(root)

        list.setOnItemClickListener { _, _, position, _ ->
            val name = items[position]
            val full = RootManager.join(current, name.trimEnd('/'))
            if (name.endsWith("/")) {
                current = full
                load()
            } else if (name.endsWith(".sh", true) || name.endsWith(".command", true)) {
                val dir = File(full).parent ?: "/"
                val cmd = "cd ${RootManager.quote(dir)} && sh ${RootManager.quote(File(full).name)}; exit"
                startActivity(Intent(this, TerminalActivity::class.java).putExtra("run_cmd", cmd))
            }
        }
        load()
    }

    private fun load() {
        Thread {
            val names = RootManager.listNames(current)
            runOnUiThread {
                items.clear()
                items.addAll(names)
                pathView.text = current
                list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
            }
        }.start()
    }

    override fun onBackPressed() {
        val parent = File(current).parent
        if (parent != null && parent != current) {
            current = parent
            load()
        } else super.onBackPressed()
    }
}
