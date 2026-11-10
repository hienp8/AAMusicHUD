package com.hud.aamusic

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
    }
}
