package com.cerniera.ferro

import android.app.Application
import com.cerniera.kit.Crash

class FerroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Crash.install(this, "Ferro")
    }
}
