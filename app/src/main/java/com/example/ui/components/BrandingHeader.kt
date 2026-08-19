package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LavenderPrimary

@Composable
fun BrandingHeader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("branding_header"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Name with white-to-translucent gradient fill
        Text(
            text = "Voxora",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White,
                        Color.White.copy(alpha = 0.6f)
                    )
                )
            ),
            letterSpacing = (-0.5).sp,
            modifier = Modifier.testTag("app_title")
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Subtitle: YOUR VOICE. YOUR CONTROL.
        Text(
            text = "YOUR VOICE. YOUR CONTROL.",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = LavenderPrimary,
            letterSpacing = 2.5.sp,
            modifier = Modifier.testTag("app_subtitle")
        )
    }
}

