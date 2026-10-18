package com.qy.pet.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qy.pet.root.RootShell

class TerminalActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoCmd = intent.getStringExtra("run_cmd")

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF101418)) {
                    val log = remember { mutableStateListOf<String>() }
                    var input by remember { mutableStateOf("") }
                    val history = remember { mutableStateListOf<String>() }
                    var histIdx by remember { mutableStateOf(-1) }
                    val listState = rememberLazyListState()

                    DisposableEffect(Unit) {
                        RootShell.start()
                        val listener: (String) -> Unit = { line ->
                            log.add(line)
                        }
                        RootShell.addListener(listener)
                        onDispose { RootShell.removeListener(listener) }
                    }

                    LaunchedEffect(Unit) {
                        log.add("交互式 root shell：普通命令和 read 数字菜单可用；到提示处直接输入数字回车")
                        if (!autoCmd.isNullOrBlank()) RootShell.send(autoCmd)
                    }

                    LaunchedEffect(log.size) {
                        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
                    }

                    fun send() {
                        val cmd = input.trim()
                        if (cmd.isEmpty()) return
                        history.add(cmd); histIdx = history.size
                        input = ""
                        RootShell.send(cmd)
                    }

                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Root 终端", color = Color(0xFF7FD4FF), fontSize = 18.sp)
                            Spacer(Modifier.weight(1f))
                            Text(if (RootShell.isReady) "已连接" else "连接中",
                                color = Color(0xFF8899AA), fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))

                        LazyColumn(Modifier.weight(1f), listState) {
                            items(log) { line ->
                                val bad = line.startsWith("[")
                                Text(
                                    line,
                                    color = if (bad) Color(0xFFFF8A80) else Color(0xFFD7E0E8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { RootShell.send("id") }) {
                                Text("id", color = Color(0xFF9FB3C8))
                            }
                            TextButton(onClick = { RootShell.send("pwd") }) {
                                Text("pwd", color = Color(0xFF9FB3C8))
                            }
                            TextButton(onClick = { log.clear() }) {
                                Text("清屏", color = Color(0xFF9FB3C8))
                            }
                            TextButton(onClick = {
                                if (histIdx > 0) { histIdx--; input = history[histIdx] }
                            }) { Text("▲", color = Color(0xFF9FB3C8)) }
                            TextButton(onClick = {
                                if (histIdx < history.size) { histIdx++; input = history.getOrNull(histIdx) ?: "" }
                            }) { Text("▼", color = Color(0xFF9FB3C8)) }
                        }

                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Text("nya#", color = Color(0xFF7FD4FF), fontFamily = FontFamily.Monospace)
                            },
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1B222B),
                                unfocusedContainerColor = Color(0xFF1B222B),
                                focusedTextColor = Color.White,
                                cursorColor = Color(0xFF7FD4FF),
                            ),
                        )
                    }
                }
            }
        }
    }
}
