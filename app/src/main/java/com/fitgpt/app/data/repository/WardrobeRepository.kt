package com.fitgpt.app.data.repository

import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.PlannedOutfit
import com.fitgpt.app.data.model.SavedOutfit

interface WardrobeRepository {

    fun getWardrobeItems(): List<ClothingItem>
    fun addItem(item: ClothingItem)
    fun deleteItem(item: ClothingItem)
    fun updateItem(item: ClothingItem)
    fun archiveItem(item: ClothingItem)
    fun unarchiveItem(item: ClothingItem)
    fun getArchivedItems(): List<ClothingItem>

    // Saved outfits
    fun saveOutfit(outfit: SavedOutfit)
    fun getSavedOutfits(): List<SavedOutfit>

    // Planned outfits
    fun planOutfit(outfit: PlannedOutfit)
    fun getPlannedOutfits(): List<PlannedOutfit>
    fun removePlannedOutfit(outfitId: Int)
}