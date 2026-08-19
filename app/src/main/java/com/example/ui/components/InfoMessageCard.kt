package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextSubtle

@Composable
fun InfoMessageCard(
    isActive: Boolean,
    infoMessage: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("info_message_card"),
        contentAlignment = Alignment.Center
    ) {
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

