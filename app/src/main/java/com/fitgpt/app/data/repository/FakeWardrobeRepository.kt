package com.fitgpt.app.data.repository

import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.PlannedOutfit
import com.fitgpt.app.data.model.SavedOutfit

class FakeWardrobeRepository : WardrobeRepository {

    private val wardrobeItems = mutableListOf(
        ClothingItem(1, "Top", "Black", "Winter", 3, "Fitted"),
        ClothingItem(2, "Bottom", "Blue", "All", 4, "Regular"),
        ClothingItem(3, "Shoes", "White", "All", 4, "Regular"),
        ClothingItem(4, "Outerwear", "Navy", "Winter", 3, "Regular"),
        ClothingItem(5, "Accessory", "Brown", "All", 5, "Regular")
    )

    private val savedOutfits = mutableListOf<SavedOutfit>()
    private val plannedOutfits = mutableListOf<PlannedOutfit>()

    override fun getWardrobeItems(): List<ClothingItem> = wardrobeItems.filter { !it.isArchived }

    override fun addItem(item: ClothingItem) {
        wardrobeItems.add(item)
    }

    override fun deleteItem(item: ClothingItem) {
        wardrobeItems.remove(item)
    }

    override fun updateItem(item: ClothingItem) {
        val index = wardrobeItems.indexOfFirst { it.id == item.id }
        if (index != -1) {
            wardrobeItems[index] = item
        }
    }

    override fun archiveItem(item: ClothingItem) {
        val index = wardrobeItems.indexOfFirst { it.id == item.id }
        if (index != -1) {
            wardrobeItems[index] = wardrobeItems[index].copy(isArchived = true)
        }
    }

    override fun unarchiveItem(item: ClothingItem) {
        val index = wardrobeItems.indexOfFirst { it.id == item.id }
        if (index != -1) {
            wardrobeItems[index] = wardrobeItems[index].copy(isArchived = false)
        }
    }

    override fun getArchivedItems(): List<ClothingItem> = wardrobeItems.filter { it.isArchived }

    override fun saveOutfit(outfit: SavedOutfit) {
        savedOutfits.add(outfit)
    }

    override fun getSavedOutfits(): List<SavedOutfit> = savedOutfits

    override fun planOutfit(outfit: PlannedOutfit) {
        plannedOutfits.add(outfit)
    }

    override fun getPlannedOutfits(): List<PlannedOutfit> = plannedOutfits

    override fun removePlannedOutfit(outfitId: Int) {
        plannedOutfits.removeAll { it.id == outfitId }
    }
}