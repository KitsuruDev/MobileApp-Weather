package com.example.appweather.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.appweather.R
import com.example.appweather.data.SearchLocation
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDialog(
    searchResults: List<SearchLocation>,
    searchHistory: List<String>,
    onSearch: (String) -> Unit,
    onCitySelected: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        if (query.length >= 2) {
            delay(500.milliseconds)
            onSearch(query)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_city)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.enter_city_name)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // введено меньше 2 символов - выводится история поиска
                if (query.length < 2) {
                    if (searchHistory.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.search_history),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onClearHistory) {
                                Text(stringResource(R.string.clear_history), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(searchHistory) { historyCity ->
                                ListItem(
                                    headlineContent = { Text(historyCity) },
                                    leadingContent = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                                    modifier = Modifier.clickable {
                                        onCitySelected(historyCity)
                                        onDismiss()
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                } else {
                    // введено 2 и больше символов - выводятся результаты поиска
                    if (searchResults.isEmpty()) {
                        Text(stringResource(R.string.city_not_found), color = MaterialTheme.colorScheme.error)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(searchResults) { location ->
                                ListItem(
                                    headlineContent = { Text(location.name) },
                                    supportingContent = { Text("${location.region}, ${location.country}") },
                                    modifier = Modifier.clickable {
                                        onCitySelected(location.name)
                                        onDismiss()
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}