package com.qy.pet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qy.pet.files.DualPaneActivity
import com.qy.pet.pet.FloatingPetService
import com.qy.pet.pet.PetState
import com.qy.pet.terminal.TerminalActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val prefs = remember { getSharedPreferences("settings", MODE_PRIVATE) }
                var scale by remember { mutableFloatStateOf(prefs.getFloat("scale", 0.35f)) }
                var showBubble by remember { mutableStateOf(prefs.getBoolean("bubble", true)) }
                var clickReaction by remember { mutableStateOf(prefs.getBoolean("click", true)) }
                var running by remember { mutableStateOf(PetState.running) }

                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("奶蛙桌宠", fontSize = 26.sp)
                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp)
                                .background(if (running) Color(0xFF2E7D32) else Color.Gray, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (running) "桌宠运行中" else "桌宠未运行")
                    }
                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            PetState.scale = scale
                            PetState.showBubble = showBubble
                            PetState.clickReaction = clickReaction
                            startService(Intent(this@MainActivity, FloatingPetService::class.java))
                            running = true
                        }, Modifier.weight(1f).height(56.dp)) { Text("放出来") }

                        Button(onClick = {
                            stopService(Intent(this@MainActivity, FloatingPetService::class.java))
                            running = false
                        }, Modifier.weight(1f).height(56.dp)) { Text("收起来") }
                    }
                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { openOverlaySettings() },
                        Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("授予「显示在其他应用上层」") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { requestNotify() },
                        Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("授予通知权限") }
                    Spacer(Modifier.height(16.dp))

                    Text("大小：${(scale * 100).toInt()}%")
                    Slider(
                        value = scale,
                        onValueChange = {
                            scale = it
                            PetState.scale = it
                            prefs.edit().putFloat("scale", it).apply()
                        },
                        valueRange = 0.2f..1f,
                    )
                    Spacer(Modifier.height(8.dp))

                    ToggleRow("显示说话气泡", showBubble) {
                        showBubble = it; PetState.showBubble = it
                        prefs.edit().putBoolean("bubble", it).apply()
                    }
                    ToggleRow("点击桌宠有反应", clickReaction) {
                        clickReaction = it; PetState.clickReaction = it
                        prefs.edit().putBoolean("click", it).apply()
                    }
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { startActivity(Intent(this@MainActivity, TerminalActivity::class.java)) },
                        Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Root 终端") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { startActivity(Intent(this@MainActivity, DualPaneActivity::class.java)) },
                        Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("双窗格文件管理") }
                }
            }
        }
    }

    @Composable
    private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 16.sp)
            Switch(checked, onChange)
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun requestNotify() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
