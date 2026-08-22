package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ActionButton
import com.example.ui.components.AssistantStatusSection
import com.example.ui.components.BrandingHeader
import com.example.ui.components.InfoMessageCard
import com.example.ui.theme.AtmosphericBackground
import com.example.ui.theme.LavenderGlow
import com.example.ui.theme.LavenderPrimary

@Composable
fun VoxoraMainScreen(
    viewModel: VoxoraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Sync state on composable launch
    LaunchedEffect(Unit) {
        viewModel.syncState(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordAudioGranted) {
            viewModel.toggleAssistant(context)
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for voice recognition",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val handleToggleAssistant = {
        if (uiState.isAssistantActive) {
            viewModel.stopAssistant(context)
        } else {
            val permissionsToRequest = mutableListOf<String>()
            
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (permissionsToRequest.isNotEmpty()) {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                viewModel.startAssistant(context)
            }
        }
    }

    VoxoraMainContent(
        uiState = uiState,
        onToggleAssistant = handleToggleAssistant,
        modifier = modifier
    )
}


@Composable
fun VoxoraMainContent(
    uiState: VoxoraUiState,
    onToggleAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "backgroundPulse")
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = if (uiState.isAssistantActive) 0.18f else 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AtmosphericBackground)
            .testTag("main_screen_container")
    ) {
        // Atmospheric Ambient Background Radial Glow Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LavenderPrimary.copy(alpha = ambientPulse),
                        LavenderGlow.copy(alpha = ambientPulse * 0.5f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = size.minDimension * 0.8f
                ),
                radius = size.minDimension * 0.8f,
                center = centerOffset
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: App Branding Header
            BrandingHeader(
                modifier = Modifier.padding(top = 24.dp)
            )

            // Center Section: Assistant Status Visualizer
            AssistantStatusSection(
                isActive = uiState.isAssistantActive,
                statusText = uiState.statusText,
                modifier = Modifier
                    .padding(vertical = 32.dp)
                    .weight(1f, fill = false)
            )

            // Bottom Section: Action Button and Info Message
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ActionButton(
                    isActive = uiState.isAssistantActive,
                    buttonText = uiState.buttonText,
                    onClick = onToggleAssistant
                )

                Spacer(modifier = Modifier.height(16.dp))

                InfoMessageCard(
                    isActive = uiState.isAssistantActive,
                    infoMessage = uiState.infoMessage,
                    lastRecognizedText = uiState.lastRecognizedText,
                    lastExecutionResult = uiState.lastExecutionResult
                )

            }
        }
    }
}


