package com.appistan.installer

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*

class AppIstan : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var catalog: Catalog; private set
    lateinit var engine: InstallEngine; private set
    override fun onCreate() {
        super.onCreate()
        catalog = Catalog(this)
        val db = Room.databaseBuilder(this, QueueDatabase::class.java, "queue.db").build()
        engine = InstallEngine(this, db.queue(), catalog)
        scope.launch { engine.recover(); while (isActive) { engine.tick(); delay(1500) } }
    }
}
