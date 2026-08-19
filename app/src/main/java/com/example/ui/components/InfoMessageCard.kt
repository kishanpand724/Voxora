package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LavenderPrimary
import com.example.ui.theme.SurfaceGlass
import com.example.ui.theme.TextSubtle

@Composable
fun InfoMessageCard(
    isActive: Boolean,
    infoMessage: String,
    lastRecognizedText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("info_message_card"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!lastRecognizedText.isNullOrBlank() && isActive) {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceGlass)
                    .border(1.dp, LavenderPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("recognized_speech_card")
            ) {
                Text(
                    text = "\"$lastRecognizedText\"",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("recognized_speech_text")
                )
            }
        }

        AnimatedContent(
            targetState = infoMessage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "infoMessageTransition"
        ) { targetText ->
            Text(
                text = targetText,
                fontSize = 13.sp,
                color = TextSubtle,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("info_message_text")
            )
        }
    }
}


