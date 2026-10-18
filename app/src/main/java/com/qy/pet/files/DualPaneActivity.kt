package com.qy.pet.files

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qy.pet.root.RootManager
import com.qy.pet.terminal.TerminalActivity
import kotlinx.coroutines.launch

class Pane(var path: String, val stack: ArrayDeque<String> = ArrayDeque()) {
    var entries by mutableStateOf<List<Entry>>(emptyList())
    val selected = mutableStateListOf<String>()
}

data class Entry(val display: String, val isDir: Boolean)

enum class ClipOp { COPY, MOVE }

class DualPaneActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val panes = remember { arrayOf(Pane("/"), Pane("/sdcard")) }
                var active by remember { mutableStateOf(0) }
                val clipboard = remember { mutableStateOf<Pair<List<String>, ClipOp>?>(null) }
                var status by remember { mutableStateOf("root: 检测中…") }
                val scope = rememberCoroutineScope()

                var dlgRename by remember { mutableStateOf<String?>(null) }
                var dlgMkdir by remember { mutableStateOf(false) }
                var dlgDelete by remember { mutableStateOf(false) }
                var dlgInput by remember { mutableStateOf("") }
                var fileMenu by remember { mutableStateOf<Pair<String, Entry>?>(null) }
                var viewContent by remember { mutableStateOf<Pair<String, String>?>(null) }

                suspend fun refresh(pane: Pane) {
                    val names = RootManager.listNames(pane.path)
                    pane.entries = names.map { Entry(it, it.endsWith("/")) }
                        .sortedByDescending { it.isDir }
                }

                LaunchedEffect(Unit) {
                    status = "root: ${if (RootManager.isRootAvailable()) "已授权" else "未授权"}"
                    panes.forEach { refresh(it) }
                }

                fun navigateInto(pane: Pane, e: Entry) {
                    if (!e.isDir) return
                    pane.stack.addLast(pane.path)
                    pane.path = RootManager.join(pane.path, e.display.trimEnd('/'))
                    scope.launch { refresh(pane); pane.selected.clear(); status = pane.path }
                }

                fun goUp(pane: Pane) {
                    pane.stack.removeLastOrNull()?.let { pane.path = it }
                    scope.launch { refresh(pane); pane.selected.clear() }
                }

                fun pasteInto(dst: Pane) {
                    val clip = clipboard.value ?: run { status = "剪贴板为空"; return }
                    scope.launch {
                        clip.first.forEach { src ->
                            val r = if (clip.second == ClipOp.COPY)
                                RootManager.copy(src, dst.path) else RootManager.move(src, dst.path)
                            status = if (r.success) "完成" else "失败：${r.err}"
                        }
                        clipboard.value = null
                        refresh(dst)
                    }
                }

                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f)) {
                            panes.forEachIndexed { i, pane ->
                                PaneColumn(
                                    modifier = Modifier.weight(1f),
                                    pane = pane,
                                    active = active == i,
                                    onActivate = { active = i },
                                    onOpen = { navigateInto(pane, it) },
                                    onUp = { goUp(pane) },
                                    onToggleSelect = { full ->
                                        if (pane.selected.contains(full)) pane.selected.remove(full)
                                        else pane.selected.add(full)
                                    },
                                    onFileClick = { full, e -> fileMenu = full to e },
                                    onRefresh = { scope.launch { refresh(pane) } },
                                )
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            ActionBtn("复制") {
                                clipboard.value = panes[active].selected.toList() to ClipOp.COPY
                                status = "已复制 ${panes[active].selected.size} 项"
                            }
                            ActionBtn("剪切") {
                                clipboard.value = panes[active].selected.toList() to ClipOp.MOVE
                                status = "已剪切 ${panes[active].selected.size} 项"
                            }
                            ActionBtn("粘贴") { pasteInto(panes[1 - active]) }
                            ActionBtn("删除") { dlgDelete = true }
                            ActionBtn("改名") {
                                val sel = panes[active].selected.singleOrNull()
                                if (sel != null) { dlgRename = sel; dlgInput = sel.substringAfterLast("/") }
                                else status = "请单选一个文件"
                            }
                            ActionBtn("新建") { dlgMkdir = true; dlgInput = "" }
                            ActionBtn("权限") {
                                val sel = panes[active].selected.singleOrNull()
                                if (sel != null) {
                                    scope.launch {
                                        val r = RootManager.chmod(sel, "0755")
                                        status = if (r.success) "已 chmod 0755" else "失败：${r.err}"
                                    }
                                } else status = "请单选一个文件"
                            }
                        }

                        Text(
                            status,
                            Modifier.fillMaxWidth().background(Color(0xFFEEF2F5)).padding(8.dp),
                            fontSize = 12.sp, color = Color(0xFF556677), fontFamily = FontFamily.Monospace,
                        )
                    }

                    fileMenu?.let { (full, e) ->
                        val canExec = e.display.endsWith(".sh", true) || e.display.endsWith(".command", true)
                        AlertDialog(
                            onDismissRequest = { fileMenu = null },
                            title = { Text(e.display) },
                            text = {
                                Column {
                                    if (canExec) MenuRow("执行（打开终端运行）") {
                                        val f = java.io.File(full)
                                        val dir = f.parent ?: "/"
                                        val cmd = "cd ${RootManager.quote(dir)} && sh ${RootManager.quote(f.name)}"
                                        fileMenu = null
                                        startActivity(
                                            Intent(this@DualPaneActivity, TerminalActivity::class.java)
                                                .putExtra("run_cmd", cmd)
                                        )
                                    }
                                    MenuRow("查看内容") {
                                        fileMenu = null
                                        scope.launch { viewContent = full to RootManager.readFile(full) }
                                    }
                                    MenuRow("选择此项") {
                                        panes[active].selected.let {
                                            if (it.contains(full)) it.remove(full) else it.add(full)
                                        }
                                        fileMenu = null
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { fileMenu = null }) { Text("取消") }
                            },
                        )
                    }

                    viewContent?.let { (path, text) ->
                        AlertDialog(
                            onDismissRequest = { viewContent = null },
                            title = { Text(path) },
                            text = {
                                Text(text.ifBlank { "（空文件或无权限）" },
                                    fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                    modifier = Modifier.height(240.dp).verticalScroll(rememberScrollState()))
                            },
                            confirmButton = { TextButton(onClick = { viewContent = null }) { Text("关闭") } },
                        )
                    }

                    if (dlgDelete) AlertDialog(
                        onDismissRequest = { dlgDelete = false },
                        title = { Text("删除选中项？") },
                        text = { Text("将以 root 执行 rm -rf，不可恢复：${panes[active].selected.joinToString()}") },
                        confirmButton = {
                            TextButton(onClick = {
                                val pane = panes[active]
                                scope.launch {
                                    pane.selected.forEach { RootManager.delete(it) }
                                    pane.selected.clear(); dlgDelete = false
                                    refresh(pane); status = "已删除"
                                }
                            }) { Text("删除", color = Color.Red) }
                        },
                        dismissButton = { TextButton(onClick = { dlgDelete = false }) { Text("取消") } },
                    )

                    dlgRename?.let { target ->
                        AlertDialog(
                            onDismissRequest = { dlgRename = null },
                            title = { Text("重命名") },
                            text = { TextField(dlgInput, { dlgInput = it }) },
                            confirmButton = {
                                TextButton(onClick = {
                                    val dir = target.substringBeforeLast("/", "")
                                    scope.launch {
                                        val r = RootManager.rename(target, RootManager.join(dir, dlgInput))
                                        status = if (r.success) "已改名" else "失败：${r.err}"
                                        dlgRename = null; refresh(panes[active])
                                    }
                                }) { Text("确定") }
                            },
                            dismissButton = { TextButton(onClick = { dlgRename = null }) { Text("取消") } },
                        )
                    }

                    if (dlgMkdir) AlertDialog(
                        onDismissRequest = { dlgMkdir = false },
                        title = { Text("新建文件夹") },
                        text = { TextField(dlgInput, { dlgInput = it }, placeholder = { Text("文件夹名") }) },
                        confirmButton = {
                            TextButton(onClick = {
                                val pane = panes[active]
                                scope.launch {
                                    val r = RootManager.makeDir(RootManager.join(pane.path, dlgInput))
                                    status = if (r.success) "已新建" else "失败：${r.err}"
                                    dlgMkdir = false; refresh(pane)
                                }
                            }) { Text("确定") }
                        },
                        dismissButton = { TextButton(onClick = { dlgMkdir = false }) { Text("取消") } },
                    )
                }
            }
        }
    }

    @Composable
    private fun PaneColumn(
        modifier: Modifier,
        pane: Pane,
        active: Boolean,
        onActivate: () -> Unit,
        onOpen: (Entry) -> Unit,
        onUp: () -> Unit,
        onToggleSelect: (String) -> Unit,
        onFileClick: (String, Entry) -> Unit,
        onRefresh: () -> Unit,
    ) {
        Column(
            modifier
                .padding(4.dp)
                .background(if (active) Color(0xFFE8F0FE) else Color(0xFFF5F6F8))
                .clickable { onActivate() },
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 20.sp, modifier = Modifier.clickable { onUp() })
                Spacer(Modifier.width(8.dp))
                Text(pane.path, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f))
                Text("⟳", modifier = Modifier.clickable { onRefresh() })
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f)) {
                items(pane.entries) { e ->
                    val full = RootManager.join(pane.path, e.display.trimEnd('/'))
                    val checked = pane.selected.contains(full)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { if (e.isDir) onOpen(e) else onFileClick(full, e) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, { onToggleSelect(full) })
                        Text(if (e.isDir) "📁 ${e.display}" else "📄 ${e.display}", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun MenuRow(label: String, onClick: () -> Unit) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = Color(0xFF3F6FD8), fontSize = 15.sp)
        }
    }

    @Composable
    private fun ActionBtn(label: String, onClick: () -> Unit) {
        TextButton(onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text(label, fontSize = 13.sp)
        }
    }
}
