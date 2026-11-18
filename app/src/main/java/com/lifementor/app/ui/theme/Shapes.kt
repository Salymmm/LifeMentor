package com.lifementor.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Design radius system: tape / strips 1–4dp, note cards 16–18dp, page shells 28dp */
val LifeMentorShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),   // tape, paper strips
    small = RoundedCornerShape(4.dp),       // folded paper corner
    medium = RoundedCornerShape(8.dp),      // small scraps
    large = RoundedCornerShape(16.dp),      // sticker buttons
    extraLarge = RoundedCornerShape(28.dp), // page shells
)

/** Note-message cards (chat bubbles) */
val NoteCardShape = RoundedCornerShape(18.dp)
