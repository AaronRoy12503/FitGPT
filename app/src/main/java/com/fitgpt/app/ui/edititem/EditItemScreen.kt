package com.fitgpt.app.ui.edititem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fitgpt.app.data.model.ClothingCategory
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.viewmodel.WardrobeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreen(
    navController: NavController,
    itemId: Int,
    viewModel: WardrobeViewModel = viewModel()
) {
    val items by viewModel.wardrobeItems.collectAsState()

    val item = items.find { it.id == itemId }

    if (item == null) {
        Text("Item not found")
        return
    }

    var category by remember { mutableStateOf(item.category) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(item.color) }
    var season by remember { mutableStateOf(item.season) }
    var comfort by remember { mutableStateOf(item.comfortLevel.toString()) }
    var fit by remember { mutableStateOf(item.fit) }
    var fitExpanded by remember { mutableStateOf(false) }
    val fits = listOf("Fitted", "Regular", "Relaxed", "Oversized")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Edit Item",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = color,
            onValueChange = { color = it },
            label = { Text("Color") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = season,
            onValueChange = { season = it },
            label = { Text("Season") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = comfort,
            onValueChange = { comfort = it },
            label = { Text("Comfort Level (1-5)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val updatedItem = item.copy(
                    category = category,
                    color = color,
                    season = season,
                    comfortLevel = comfort.toIntOrNull() ?: item.comfortLevel,
                    fit = fit
                )

                viewModel.updateItem(updatedItem)
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Changes")
        }
    }
}