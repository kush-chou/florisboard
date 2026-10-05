/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors / Foldboard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.voice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard3.LocalImeController

@Composable
fun VoiceInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imeController = LocalImeController.current
    val voiceManager = remember { VoiceInputManager(context) }

    val isListening by voiceManager.isListening.collectAsState()
    val transcript by voiceManager.transcript.collectAsState()
    val partialText by voiceManager.partialText.collectAsState()
    val statusText by voiceManager.statusText.collectAsState()
    val rmsDb by voiceManager.rmsDb.collectAsState()

    DisposableEffect(Unit) {
        voiceManager.startListening()
        onDispose {
            voiceManager.destroy()
        }
    }

    // Mic pulsing animation when listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.22f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micPulse",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight())
            .background(Color(0xFF1E1F22))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // --- Top Bar: Switch to Keyboard, Status, Clear ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Return to keyboard
            IconButton(
                onClick = {
                    voiceManager.stopListening()
                    imeController.updateStateBlocking {
                        state = state.copy(flags = state.flags.withImeUiMode(ImeUiMode.TEXT))
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Switch to keyboard",
                    tint = Color(0xFFC4C7C5),
                )
            }

            // Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isListening) Color(0xFF4A2828) else Color(0xFF2B2D30))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = statusText,
                    color = if (isListening) Color(0xFFFF8B80) else Color(0xFFA8ABB0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Clear button
            IconButton(
                onClick = { voiceManager.clearTranscript() },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear transcript",
                    tint = Color(0xFFC4C7C5),
                )
            }
        }

        // --- Center: Live Text Card ---
        val displayText = if (partialText.isNotBlank()) {
            if (transcript.isNotBlank()) "$transcript $partialText" else partialText
        } else {
            transcript
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2B2D30))
                .border(1.dp, Color(0xFF3E4146), RoundedCornerShape(16.dp))
                .padding(12.dp),
            contentAlignment = if (displayText.isBlank()) Alignment.Center else Alignment.TopStart,
        ) {
            if (displayText.isBlank()) {
                Text(
                    text = if (isListening) "Listening for speech..." else "Tap microphone to begin speaking",
                    color = Color(0xFF8E9196),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = displayText,
                    color = Color(0xFFE3E3E3),
                    fontSize = 16.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        // --- Waveform & Big Mic Button ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Audio wave indicators left
            WaveformBars(isListening = isListening, rmsDb = rmsDb)

            Spacer(modifier = Modifier.width(16.dp))

            // Big Center Mic Button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(if (isListening) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(if (isListening) Color(0xFFEA4335) else Color(0xFF383A40))
                    .clickable { voiceManager.toggleListening() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "Toggle microphone",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Audio wave indicators right
            WaveformBars(isListening = isListening, rmsDb = rmsDb)
        }

        // --- Bottom Actions: AI Polish, AVF Send, Insert ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // AI Polish Button
            CapsuleButton(
                modifier = Modifier.weight(1f),
                text = "✨ AI Polish",
                backgroundColor = Color(0xFF383A40),
                textColor = Color(0xFFA8C7FA),
                onClick = { voiceManager.applyAiPolish() },
            )

            // AVF Terminal Send Button
            CapsuleButton(
                modifier = Modifier.weight(1f),
                text = ">_ AVF Send",
                backgroundColor = Color(0xFF2C394B),
                textColor = Color(0xFF8AB4F8),
                onClick = { voiceManager.sendToAvf() },
            )

            // Insert / Done Button
            CapsuleButton(
                modifier = Modifier.weight(1.2f),
                text = "✓ Insert",
                backgroundColor = Color(0xFF004A77),
                textColor = Color(0xFFD3E3FD),
                onClick = {
                    voiceManager.commitToInputConnection(autoPolish = true)
                    imeController.updateStateBlocking {
                        state = state.copy(flags = state.flags.withImeUiMode(ImeUiMode.TEXT))
                    }
                },
            )
        }
    }
}

@Composable
private fun WaveformBars(isListening: Boolean, rmsDb: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val baseHeights = listOf(8, 14, 22, 14, 8)
        val boost = (rmsDb * 1.5f).coerceIn(0f, 18f)
        for (h in baseHeights) {
            val barHeight = if (isListening) (h + boost).coerceIn(6f, 32f) else 6f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isListening) Color(0xFFF28B82) else Color(0xFF4E5157)),
            )
        }
    }
}

@Composable
private fun CapsuleButton(
    modifier: Modifier = Modifier,
    text: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
