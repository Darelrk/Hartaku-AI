package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.AppContainer
import com.example.data.MonthlyResetScheduler
import com.example.ui.MainScreen
import com.example.ui.theme.MidnightAbyss
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  // ponytail: AppContainer warms SQLCipher + ObjectBox + WorkManager, ~3s measured. On the
  // main thread that blocks the first frame (Davey 3226ms, 183 skipped frames), so warm it
  // on IO and gate the UI on it instead.
  private val containerReady = MutableStateFlow(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    lifecycleScope.launch(Dispatchers.IO) {
      AppContainer.getInstance(applicationContext)
      containerReady.value = true
    }
    // Reset Bill.isPaidThisMonth on month rollover (Gap 6).
    MonthlyResetScheduler.resetIfNewMonth(this)
    setContent {
      val ready by containerReady.collectAsState()
      MyApplicationTheme {
        if (ready) MainScreen() else StartupGate()
      }
    }
  }
}

@Composable
private fun StartupGate() {
  Box(Modifier.fillMaxSize().background(MidnightAbyss))
}
