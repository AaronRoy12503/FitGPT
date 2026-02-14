package com.fitgpt.app.ui.additem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.viewmodel.WardrobeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    navController: NavController,
    viewModel: WardrobeViewModel = viewModel()
) {
    var category by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }

    var season by remember { mutableStateOf("All") }
    var seasonExpanded by remember { mutableStateOf(false) }

    var fit by remember { mutableStateOf("Regular") }
    var fitExpanded by remember { mutableStateOf(false) }

    var comfortLevel by remember { mutableStateOf(3f) }

    val seasons = listOf("All", "Winter", "Spring", "Summer", "Fall")
    val fits = listOf("Fitted", "Regular", "Relaxed", "Oversized")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Add Clothing Item") })
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                label = { Text("Color") },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = seasonExpanded,
                onExpandedChange = { seasonExpanded = !seasonExpanded }
            ) {
                OutlinedTextField(
                    value = season,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Season") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = seasonExpanded,
                    onDismissRequest = { seasonExpanded = false }
                ) {
                    seasons.forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = {
                                season = it
                                seasonExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = fitExpanded,
                onExpandedChange = { fitExpanded = !fitExpanded }
            ) {
                OutlinedTextField(
                    value = fit,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fit") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = fitExpanded,
                    onDismissRequest = { fitExpanded = false }
                ) {
                    fits.forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = {
                                fit = it
                                fitExpanded = false
                            }
                        )
                    }
                }
            }

            Text("Comfort Level: ${comfortLevel.toInt()}")
            Slider(
                value = comfortLevel,
                onValueChange = { comfortLevel = it },
                valueRange = 1f..5f,
                steps = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.addItem(
                        ClothingItem(
                            id = System.currentTimeMillis().toInt(),
                            category = category,
                            color = color,
                            season = season,
                            comfortLevel = comfortLevel.toInt(),
                            fit = fit
                        )
                    )
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = category.isNotBlank() && color.isNotBlank()
            ) {
                Text("Save Item")
            }
        }
    }
}