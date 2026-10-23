package com.fitlens.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitlens.ai.data.DefaultHealthRepository
import com.fitlens.ai.data.DemoActivitySource
import com.fitlens.ai.data.DemoNutritionSource
import com.fitlens.ai.ui.DashboardScreen
import com.fitlens.ai.ui.DashboardViewModel
import com.fitlens.ai.ui.FitLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = DashboardViewModel.Factory(DefaultHealthRepository(DemoActivitySource(), DemoNutritionSource()))
        setContent {
            FitLensTheme {
                val model: DashboardViewModel = viewModel(factory = factory)
                DashboardScreen(model.state.collectAsStateWithLifecycle().value, model::refresh)
            }
        }
    }
}
