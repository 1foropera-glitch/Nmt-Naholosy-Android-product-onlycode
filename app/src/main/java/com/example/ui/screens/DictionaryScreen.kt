package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Word
import com.example.data.WordState
import com.example.ui.components.WordItemRow
import com.example.ui.layout.AppWindowWidthSizeClass
import com.example.ui.layout.adaptiveGridColumnCount
import com.example.viewmodel.DictionaryViewModel

import androidx.compose.foundation.border

@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel,
    widthSizeClass: AppWindowWidthSizeClass = AppWindowWidthSizeClass.Compact,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedLetter by viewModel.selectedLetter.collectAsStateWithLifecycle()
    val words by viewModel.dictionaryWords.collectAsStateWithLifecycle()
    val allStates by viewModel.allWordStates.collectAsStateWithLifecycle()

    DictionaryContent(searchQuery, selectedLetter, words, allStates,
        viewModel::onSearchQueryChanged, viewModel::onLetterSelected, widthSizeClass, modifier)
}

@Composable
fun DictionaryContent(
    searchQuery: String,
    selectedLetter: String?,
    words: List<Word>,
    allStates: List<WordState>,
    onSearchQueryChanged: (String) -> Unit,
    onLetterSelected: (String?) -> Unit,
    widthSizeClass: AppWindowWidthSizeClass = AppWindowWidthSizeClass.Compact,
    modifier: Modifier = Modifier
) {

    // Map of states for constant lookup times
    val statesMap = remember(allStates) { allStates.associateBy { it.wordId } }

    val letters = listOf("Всі", "А", "Б", "В", "Г", "Д", "Е", "Є", "Ж", "З", "І", "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Я")

    // Header and controls must scroll too: a short window can be smaller than
    // their combined height, especially with accessibility font scaling.
    LazyVerticalGrid(
        columns = GridCells.Fixed(adaptiveGridColumnCount(widthSizeClass)),
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
            .testTag("dictionary_grid"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "dictionary_header", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                // Headline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.emoji_books),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).size(28.dp)
                    )
                    Text(
                        text = "Словник наголосів для підготовки до НМТ",
                    color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Filter and search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Пошук слова...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dictionary_search_input"),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Пошук", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистити пошук", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal letter selection index for instant filtering (user comfort!)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems(letters) { letter ->
                        val isSelected = (letter == "Всі" && selectedLetter == null) || (letter == selectedLetter)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (letter == "Всі") {
                                        onLetterSelected(null)
                                    } else {
                                        onLetterSelected(letter)
                                    }
                                }
                                .semantics { role = Role.Button }
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("letter_filter_$letter")
                        ) {
                            Text(
                                text = letter,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        if (words.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = "Немає результатів", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Жодного слова не знайдено за цими параметрами.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            gridItems(words, key = { it.id }) { word ->
                val state = statesMap[word.id]
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dictionary_word_${word.id}")
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    WordItemRow(word = word, wordState = state)
                }
            }
        }
    }
}
