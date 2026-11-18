package com.lifementor.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lifementor.app.data.Mentor

/**
 * Mentor avatar: prefers the art-team cartoon sticker; falls back to a round
 * emoji chip for custom mentors that have no artwork.
 */
@Composable
fun MentorAvatar(
    mentor: Mentor,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackEmojiSize: TextUnit = 22.sp,
) {
    if (mentor.avatarRes == 0) {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(mentor.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(mentor.emoji, fontSize = fallbackEmojiSize)
        }
    } else {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(mentor.avatarRes),
                contentDescription = "${mentor.name} avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
