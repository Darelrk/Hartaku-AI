package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.data.AppContainer
import com.example.data.MonthlyResetScheduler
import com.example.ui.MainScreen
import com.example.ui.theme.GhostWhite
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
        Crossfade(targetState = ready, animationSpec = tween(150), label = "startup") { isReady ->
          if (isReady) MainScreen() else StartupGate()
        }
      }
    }
  }
}

@Composable
private fun StartupGate() {
  Column(
    modifier = Modifier.fillMaxSize().background(MidnightAbyss),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text("Menyiapkan data aman…", color = GhostWhite)
  }
}
