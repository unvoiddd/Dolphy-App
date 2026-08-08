package com.droid.dolphy

interface Spammer {
    fun isSpamming(): Boolean
    fun start()
    fun stop()
    fun setBlinkRunnable(runnable: Runnable?)
    fun getBlinkRunnable(): Runnable?
}
