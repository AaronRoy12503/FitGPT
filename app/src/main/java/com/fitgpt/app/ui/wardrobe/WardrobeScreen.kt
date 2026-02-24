package com.fitgpt.app.ui.wardrobe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.navigation.Routes
import com.fitgpt.app.ui.preferences.UserPreferencesDialog
import com.fitgpt.app.viewmodel.WardrobeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: WardrobeViewModel = viewModel()
) {
    val items by viewModel.wardrobeItems.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    var itemToDelete by remember { mutableStateOf<ClothingItem?>(null) }
    var showPreferencesDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Wardrobe") },
                actions = {
                    IconButton(onClick = { showPreferencesDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Preferences")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.ADD_ITEM) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Clothing")
            }
        }
    ) { paddingValues ->

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            Button(
                onClick = {
                    viewModel.refreshRecommendations()
                    navController.navigate(Routes.RECOMMENDATIONS)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get Recommendations")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(items) { item ->
                    WardrobeItemCard(
                        item = item,
                        viewModel = viewModel,
                        onEdit = {
                            navController.navigate("${Routes.EDIT_ITEM}/${item.id}")
                        },
                        onDelete = {
                            itemToDelete = item
                        }
                    )
                }
            }
        }
    }

    if (showPreferencesDialog) {
        UserPreferencesDialog(
            currentPreferences = userPreferences,
            onDismiss = { showPreferencesDialog = false },
            onSave = { prefs ->
                viewModel.updatePreferences(prefs)
                showPreferencesDialog = false
            }
        )
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete this item?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(itemToDelete!!)
                        itemToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WardrobeItemCard(
    item: ClothingItem,
    viewModel: WardrobeViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val explanation = remember(item) {
        viewModel.generateExplanation(item)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.category} - ${item.color} (${item.season})",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Comfort: ${item.comfortLevel}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit item")
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete item")
            }
        }
    }
}