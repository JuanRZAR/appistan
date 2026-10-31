package com.appistan.fixture

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class FixtureActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val preferences = getSharedPreferences("fixture", MODE_PRIVATE)
        val marker = preferences.getString("marker", null)?.takeIf { it.startsWith("fixture:") }
            ?: "fixture:${java.util.UUID.randomUUID()}".also { preferences.edit().putString("marker", it).commit() }
        setContentView(TextView(this).apply { text = "AppIstan · Prueba de actualización\n\nDatos conservados\n$marker"; textSize = 24f; setPadding(40, 80, 40, 40) })
    }
}
