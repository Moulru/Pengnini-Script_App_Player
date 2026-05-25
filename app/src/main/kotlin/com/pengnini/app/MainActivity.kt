package com.pengnini.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.pengnini.app.ui.PengniniApp
import com.pengnini.app.ui.theme.PengniniTheme

class MainActivity : ComponentActivity() {
    private val notiPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 결과 무시: 거부돼도 앱은 동작, 알림만 안 보임 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notiPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            PengniniTheme {
                PengniniApp()
            }
        }
    }
}
