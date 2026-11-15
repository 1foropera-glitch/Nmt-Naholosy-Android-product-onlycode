package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Word
import com.example.data.WordState
import com.example.ui.theme.AppTheme

@Composable
fun WordItemRow(word: Word, wordState: WordState? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Highlight stressed vowels inside the word
            Text(
                text = formatStressedWord(word.plain, word.stressed),
                modifier = Modifier.semantics {
                    contentDescription = com.example.util.StressUtils.accessibleStress(word.plain, word.stressed)
                },
                    color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!word.note.isNullOrBlank()) {
                Text(
                    text = "(${word.note})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        // Mini Badge showing current SRS status box
        if (wordState != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (wordState.status == "learned") AppTheme.colors.successContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (wordState.status == "learned") "Засвоєно" else "Вивчаєш",
                    color = if (wordState.status == "learned") {
                        AppTheme.colors.onSuccessContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Returns an AnnotatedString where stressed letters use the semantic success/correct color.
 */
@Composable
fun formatStressedWord(plain: String, stressed: String): androidx.compose.ui.text.AnnotatedString {
    val successColor = AppTheme.colors.success

    return buildAnnotatedString {
        val lowerPlain = plain.lowercase()
        val chars = lowerPlain.toCharArray()
        for (i in chars.indices) {
            // Stressed vowel in base representation is UPPERCASE
            val isStressed = i < stressed.length && stressed[i].isUpperCase()
            if (isStressed) {
                withStyle(style = SpanStyle(color = successColor, fontWeight = FontWeight.Black)) {
                    append(chars[i].toString())
                }
            } else {
                append(chars[i].toString())
            }
        }
    }
}
