@file:OptIn(ExperimentalMaterial3Api::class)

package com.fitgpt.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitgpt.app.data.model.PreferenceOptions
import com.fitgpt.app.data.model.UserPreferences

@Composable
fun OnboardingScreen(
    onComplete: (UserPreferences) -> Unit,
    onSkip: () -> Unit
) {
    var bodyType by remember { mutableStateOf("Average") }
    var bodyTypeExpanded by remember { mutableStateOf(false) }

    var stylePreference by remember { mutableStateOf("Casual") }
    var styleExpanded by remember { mutableStateOf(false) }

    var comfortPreference by remember { mutableStateOf(3f) }

    var selectedSeasons by remember {
        mutableStateOf(PreferenceOptions.allSeasons.toSet())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome to FitGPT",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Let's set up your style preferences so we can recommend outfits just for you.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Body type dropdown
        ExposedDropdownMenuBox(
            expanded = bodyTypeExpanded,
            onExpandedChange = { bodyTypeExpanded = !bodyTypeExpanded }
        ) {
            OutlinedTextField(
                value = bodyType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Body Type") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = bodyTypeExpanded,
                onDismissRequest = { bodyTypeExpanded = false }
            ) {
                PreferenceOptions.bodyTypes.forEach {
                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = {
                            bodyType = it
                            bodyTypeExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Style preference dropdown
        ExposedDropdownMenuBox(
            expanded = styleExpanded,
            onExpandedChange = { styleExpanded = !styleExpanded }
        ) {
            OutlinedTextField(
                value = stylePreference,
                onValueChange = {},
                readOnly = true,
                label = { Text("Style Preference") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = styleExpanded,
                onDismissRequest = { styleExpanded = false }
            ) {
                PreferenceOptions.styles.forEach {
                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = {
                            stylePreference = it
                            styleExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Comfort level slider
        Text(
            text = "Comfort Preference: ${comfortPreference.toInt()}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth()
        )
        Slider(
            value = comfortPreference,
            onValueChange = { comfortPreference = it },
            valueRange = 1f..5f,
            steps = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Season checkboxes
        Text(
            text = "Preferred Seasons",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth()
        )
        PreferenceOptions.allSeasons.forEach { season ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = season in selectedSeasons,
                    onCheckedChange = { checked ->
                        selectedSeasons = if (checked) {
                            selectedSeasons + season
                        } else {
                            selectedSeasons - season
                        }
                    }
                )
                Text(text = season)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Get Started button
        Button(
            onClick = {
                val seasons = selectedSeasons.toList().ifEmpty {
                    PreferenceOptions.allSeasons
                }
                onComplete(
                    UserPreferences(
                        bodyType = bodyType,
                        stylePreference = stylePreference,
                        comfortPreference = comfortPreference.toInt(),
                        preferredSeasons = seasons
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Skip button
        TextButton(onClick = onSkip) {
            Text("Skip")
        }
    }
}
