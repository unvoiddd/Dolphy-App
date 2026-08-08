package com.tutozz.blespam

interface Spammer {
    fun start()
    fun isSpamming(): Boolean
    fun stop()
    fun setBlinkRunnable(blinkRunnable: Runnable?)
    fun getBlinkRunnable(): Runnable?
}
