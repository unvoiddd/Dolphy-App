package com.droid.dolphy.hid

interface HidController {
    fun keyDown(key: String)
    fun keyUp(key: String)
    fun modifierDown(key: String)
    fun modifierUp(key: String)
    fun releaseAllKeyboard()

    fun mouseMove(deltaX: Int, deltaY: Int)
    fun mouseDown(button: Int)
    fun mouseUp(button: Int)
    fun mouseClick(button: Int)
    fun releaseAllMouseButtons()
    fun mouseWheel(delta: Int, horizontal: Boolean = false)
    fun mediaDown(key: String)
    fun mediaUp(key: String)
    fun consumerDown(usageMask: Int) {}
    fun consumerUp(usageMask: Int) {}
    fun launchApplication(usage: Int) {}
    fun controlApplication(usage: Int) {}

    fun checkDevices(): Boolean = true
}

