package com.qy.pet.pet

object PetState {
    @Volatile var running = false
    @Volatile var scale = 0.35f
    var showBubble = true
    var clickReaction = true

    val idleLines = listOf(
        "呱~ 主人今天也要加油呀",
        "无聊啦，摸摸我头嘛",
        "Zzz…奶蛙打瞌睡中…",
        "root 已就位，随时可以干活",
    )

    val pettedLines = listOf(
        "呱呱…再摸一下也可以哦",
        "主人的手好暖和~",
        "舒服到鼓腮帮子了",
    )
}
