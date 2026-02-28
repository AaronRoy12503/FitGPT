package com.fitgpt.app

import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.PlannedOutfit
import com.fitgpt.app.data.repository.FakeWardrobeRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Verifies planned-outfit CRUD in FakeWardrobeRepository.
 *
 * Covers:
 *  - Add and retrieve planned outfits
 *  - Remove by ID
 *  - Removing a non-existent ID is a no-op
 *  - Planned and saved outfits are independent
 */
class PlannedOutfitRepositoryTest {

    private lateinit var repo: FakeWardrobeRepository

    private val sampleItems = listOf(
        ClothingItem(1, "Top", "Black", "Summer", 3),
        ClothingItem(2, "Bottom", "Blue", "Summer", 4)
    )

    @Before
    fun setUp() {
        repo = FakeWardrobeRepository()
    }

    @Test
    fun planOutfit_addAndRetrieve() {
        val planned = PlannedOutfit(
            id = 100,
            items = sampleItems,
            date = LocalDate.of(2026, 3, 1)
        )
        repo.planOutfit(planned)

        val all = repo.getPlannedOutfits()
        assertEquals(1, all.size)
        assertEquals(100, all[0].id)
        assertEquals(2, all[0].items.size)
    }

    @Test
    fun removePlannedOutfit_removesById() {
        repo.planOutfit(PlannedOutfit(1, sampleItems, LocalDate.of(2026, 3, 1)))
        repo.planOutfit(PlannedOutfit(2, sampleItems, LocalDate.of(2026, 3, 2)))

        repo.removePlannedOutfit(1)

        val remaining = repo.getPlannedOutfits()
        assertEquals(1, remaining.size)
        assertEquals(2, remaining[0].id)
    }

    @Test
    fun removePlannedOutfit_nonExistentId_isNoOp() {
        repo.planOutfit(PlannedOutfit(1, sampleItems, LocalDate.of(2026, 3, 1)))
        repo.removePlannedOutfit(999)

        assertEquals(1, repo.getPlannedOutfits().size)
    }

    @Test
    fun getPlannedOutfits_emptyByDefault() {
        assertTrue(repo.getPlannedOutfits().isEmpty())
    }

    @Test
    fun plannedAndSavedOutfits_areIndependent() {
        val planned = PlannedOutfit(1, sampleItems, LocalDate.of(2026, 3, 1))
        repo.planOutfit(planned)

        val saved = com.fitgpt.app.data.model.SavedOutfit(2, sampleItems)
        repo.saveOutfit(saved)

        assertEquals(1, repo.getPlannedOutfits().size)
        assertEquals(1, repo.getSavedOutfits().size)

        repo.removePlannedOutfit(1)
        assertEquals(0, repo.getPlannedOutfits().size)
        assertEquals(1, repo.getSavedOutfits().size)
    }
}
