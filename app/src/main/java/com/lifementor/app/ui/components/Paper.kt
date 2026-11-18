package com.lifementor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifementor.app.ui.theme.Cardboard
import com.lifementor.app.ui.theme.InkBlack
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.NoteCardShape
import com.lifementor.app.ui.theme.PaperBackground
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapBlue
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.theme.ScrapYellow
import com.lifementor.app.ui.theme.ScrapYellowDeep

/** Scrapbook paper card with an offset colored "shadow" and slight rotation for a handmade feel */
@Composable
fun ScrapCard(
    paperColor: Color,
    modifier: Modifier = Modifier,
    shadowColor: Color = Color(0x33121214),
    shadowOffset: Dp = 4.dp,
    rotation: Float = 0f,
    shape: Shape = NoteCardShape,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    fillWidth: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(modifier.rotate(rotation)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(shadowColor, shape),
        )
        Column(
            (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .background(paperColor, shape)
                .padding(contentPadding),
            content = content,
        )
    }
}

/** A small piece of tape stuck on a paper corner */
@Composable
fun Tape(
    modifier: Modifier = Modifier,
    color: Color = ScrapYellow.copy(alpha = 0.85f),
    width: Dp = 42.dp,
    height: Dp = 12.dp,
    rotation: Float = -4f,
) {
    Box(
        modifier
            .size(width, height)
            .rotate(rotation)
            .background(color, RoundedCornerShape(2.dp)),
    )
}

/** Sticker button: colored base + matching offset shadow + corner tape (screen 07 primary button recipe) */
@Composable
fun StickerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    baseColor: Color = ScrapYellowDeep,
    shadowColor: Color = ScrapBlue,
    contentColor: Color = InkBlack,
    enabled: Boolean = true,
    arrow: Boolean = false,
) {
    Row(
        modifier
            .rotate(if (enabled) -0.4f else 0f)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(x = 4.dp, y = 4.dp)
                    .background(shadowColor, RoundedCornerShape(16.dp)),
            )
            Row(
                Modifier
                    .background(baseColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
                if (arrow) {
                    Text(" →", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = contentColor)
                }
            }
        }
    }
}

/** Small paper-strip button for secondary actions (back / clear …) */
@Composable
fun PaperChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    baseColor: Color = PaperWhite,
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = 2.dp)
                .background(Cardboard, RoundedCornerShape(6.dp)),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) InkBlack else InkBlack.copy(alpha = 0.38f),
            modifier = Modifier
                .background(baseColor, RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

/** Scrapbook bottom nav: selected item = red paper pill + yellow tape (the design's NAV recipe) */
@Composable
fun PaperNav(
    items: List<Pair<String, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(PaperBackground)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEachIndexed { i, (emoji, label) ->
            val sel = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (sel) ScrapRed else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                if (sel) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-4).dp)
                            .size(width = 24.dp, height = 7.dp)
                            .rotate(-6f)
                            .background(ScrapYellow.copy(alpha = 0.9f), RoundedCornerShape(2.dp)),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(emoji, fontSize = 20.sp)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sel) PaperWhite else InkSoft,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/** Holtwood display serif page title (the design's header style) */
@Composable
fun DisplayTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.displaySmall, modifier = modifier)
}
