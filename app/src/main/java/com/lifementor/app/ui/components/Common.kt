package com.lifementor.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.unit.sp
import com.lifementor.app.ui.theme.Cardboard
import com.lifementor.app.ui.theme.InkBlack
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.theme.ScrapYellowDeep
import com.lifementor.app.ui.theme.DisplayFontFamily

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

/** Holtwood display serif kicker (the design's "My Guides" style) */
@Composable
fun KickerTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontFamily = DisplayFontFamily, fontSize = 18.sp, modifier = modifier)
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 10,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PaperWhite,
            unfocusedContainerColor = PaperWhite,
            focusedBorderColor = InkBlack,
            unfocusedBorderColor = ScrapYellowDeep,
            errorBorderColor = ScrapRed,
            cursorColor = ScrapRed,
            focusedTextColor = InkBlack,
            unfocusedTextColor = InkBlack,
            focusedLabelColor = InkBlack,
            unfocusedLabelColor = InkSoft,
        ),
    )
}
