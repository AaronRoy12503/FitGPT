package com.fitgpt.app.ui.additem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fitgpt.app.data.model.ClothingCategory
import com.fitgpt.app.data.model.ClothingColor
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.viewmodel.WardrobeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    navController: NavController,
    viewModel: WardrobeViewModel = viewModel()
) {
    var category by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf("") }
    var colorExpanded by remember { mutableStateOf(false) }

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

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    ClothingCategory.ALL.forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = {
                                category = it
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = colorExpanded,
                onExpandedChange = { colorExpanded = !colorExpanded }
            ) {
                OutlinedTextField(
                    value = color,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Color") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = colorExpanded,
                    onDismissRequest = { colorExpanded = false }
                ) {
                    ClothingColor.ALL.forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = {
                                color = it
                                colorExpanded = false
                            }
                        )
                    }
                }
            }

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