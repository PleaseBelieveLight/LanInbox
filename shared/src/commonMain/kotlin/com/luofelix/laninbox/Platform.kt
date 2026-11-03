package com.luofelix.laninbox

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform