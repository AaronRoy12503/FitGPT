@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.fitgpt.app.ui.recommendation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fitgpt.app.data.model.OutfitRecommendation
import com.fitgpt.app.viewmodel.RecommendationUiState
import com.fitgpt.app.viewmodel.WardrobeViewModel

@Composable
fun RecommendationScreen(
    navController: NavController,
    viewModel: WardrobeViewModel = viewModel()
) {
    val uiState by viewModel.recommendationState.collectAsState()
    val wardrobeItems by viewModel.wardrobeItems.collectAsState()

    // Re-generate recommendations whenever the wardrobe changes (e.g. item deleted)
    LaunchedEffect(wardrobeItems) {
        viewModel.refreshRecommendations()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Outfit Recommendation") },
                actions = {
                    IconButton(onClick = { viewModel.refreshRecommendations() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                is RecommendationUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Getting AI recommendations...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is RecommendationUiState.Unauthenticated -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign in to get personalized recommendations.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is RecommendationUiState.Success -> {
                    if (state.recommendations.isEmpty()) {
                        Text(
                            text = "Add items to your wardrobe to get recommendations.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recommended for you",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            if (state.isAiGenerated) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("AI-powered") }
                                )
                            } else {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Offline") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.recommendations) { recommendation ->
                                RecommendationCard(recommendation = recommendation)
                            }
                        }
                    }
                }

                is RecommendationUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI unavailable. Showing offline recommendations.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.refreshRecommendations() }) {
                                Text("Retry")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (state.fallbackRecommendations.isNotEmpty()) {
                        Text(
                            text = "Recommended for you",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.fallbackRecommendations) { recommendation ->
                                RecommendationCard(recommendation = recommendation)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Wardrobe")
            }
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: OutfitRecommendation) {
    var expanded by remember { mutableStateOf(false) }

    val scoreLabel = when {
        recommendation.score >= 2.5 -> "Great match"
        recommendation.score >= 1.5 -> "Good match"
        else -> "Worth trying"
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Outfit title
            Text(
                text = recommendation.items.joinToString(" + ") {
                    "${it.color} ${it.category}"
                },
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Color swatches
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                recommendation.items.forEach { item ->
                    val swatchColor = colorNameToColor(item.color)
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = recommendation.items.joinToString(" / ") { it.color },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Score bar with label
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { (recommendation.score / 3.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = scoreLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scoring factor chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val seasons = recommendation.items.map { it.season.lowercase() }.toSet()
                val hasSeasonMatch = seasons.any { it == "all" } || seasons.size <= 2
                SuggestionChip(
                    onClick = {},
                    label = { Text(if (hasSeasonMatch) "Season \u2713" else "Season Mix") }
                )

                val avgComfort = recommendation.items.sumOf { it.comfortLevel }.toDouble() / recommendation.items.size
                SuggestionChip(
                    onClick = {},
                    label = { Text(if (avgComfort >= 3) "Comfort \u2713" else "Comfort ~") }
                )

                SuggestionChip(
                    onClick = {},
                    label = { Text(if (recommendation.score >= 1.5) "Style \u2713" else "Style ~") }
                )

                val harmonyScore = recommendation.score // approximation: high score = good harmony
                SuggestionChip(
                    onClick = {},
                    label = { Text(if (harmonyScore >= 1.5) "Color \u2713" else "Color ~") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Overall explanation (prominent)
            Text(
                text = recommendation.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Expandable per-item explanations
            if (recommendation.itemExplanations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Hide item details" else "Show item details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recommendation.items.forEach { item ->
                            val itemExplanation = recommendation.itemExplanations[item.id]
                            if (itemExplanation != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(colorNameToColor(item.color))
                                            .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${item.color} ${item.category}:",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = itemExplanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun colorNameToColor(name: String): Color {
    return when (name.lowercase()) {
        "black" -> Color(0xFF212121)
        "white" -> Color(0xFFF5F5F5)
        "red" -> Color(0xFFE53935)
        "blue" -> Color(0xFF1E88E5)
        "green" -> Color(0xFF43A047)
        "yellow" -> Color(0xFFFDD835)
        "orange" -> Color(0xFFFB8C00)
        "purple" -> Color(0xFF8E24AA)
        "pink" -> Color(0xFFEC407A)
        "brown" -> Color(0xFF795548)
        "gray", "grey" -> Color(0xFF9E9E9E)
        "navy" -> Color(0xFF283593)
        "beige" -> Color(0xFFD7CCC8)
        "tan" -> Color(0xFFBCAAA4)
        "cream", "ivory" -> Color(0xFFFFF8E1)
        "teal" -> Color(0xFF00897B)
        "coral" -> Color(0xFFFF7043)
        "gold" -> Color(0xFFFFD54F)
        "olive" -> Color(0xFF827717)
        "rust" -> Color(0xFFBF360C)
        "khaki" -> Color(0xFFC5B358)
        "indigo" -> Color(0xFF3949AB)
        "magenta" -> Color(0xFFAD1457)
        "mint" -> Color(0xFF80CBC4)
        "peach" -> Color(0xFFFFAB91)
        else -> Color(0xFF78909C)
    }
}
