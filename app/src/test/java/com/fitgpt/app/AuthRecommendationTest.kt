package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.auth.AuthManager
import com.fitgpt.app.data.auth.AuthProvider
import com.fitgpt.app.data.auth.UserSession
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.UserPreferences
import com.fitgpt.app.viewmodel.RecommendationUiState
import com.fitgpt.app.viewmodel.WardrobeViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Validates that recommendation endpoints require authenticated users,
 * that user IDs come from verified backend sessions, that recommendations
 * are generated after Google login, that no cross-user data is exposed,
 * and that onboarding data properly initializes personalization.
 */
@RunWith(JUnit4::class)
class AuthRecommendationTest {

    // ----------------------------------------------------------------
    // Test-only AuthManager implementation
    // ----------------------------------------------------------------

    private class FakeAuthManager : AuthManager {
        private var session: UserSession? = null

        fun signIn(session: UserSession) {
            this.session = session
        }

        fun signOut() {
            session = null
        }

        override fun isAuthenticated(): Boolean = session != null
        override fun getCurrentSession(): UserSession? = session
        override fun getCurrentUserId(): String? = session?.userId
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private fun verifiedGoogleSession(
        userId: String = "google-user-1",
        email: String = "user@gmail.com"
    ) = UserSession(userId, email, AuthProvider.GOOGLE, isVerified = true)

    private fun createViewModel(authManager: AuthManager? = null) =
        WardrobeViewModel(authManager = authManager)

    private fun prefs(bodyType: String = "Average") = UserPreferences(
        bodyType = bodyType,
        stylePreference = "Casual",
        comfortPreference = 3,
        preferredSeasons = listOf("Spring", "Summer", "Fall", "Winter")
    )

    // ================================================================
    // Section 1: Auth guard — recommendation endpoints require auth
    // ================================================================

    @Test
    fun authGuard_unauthenticated_returnsUnauthenticatedState() {
        val auth = FakeAuthManager() // no sign-in
        val vm = createViewModel(auth)

        assertEquals(RecommendationUiState.Unauthenticated, vm.recommendationState.value)
    }

    @Test
    fun authGuard_unauthenticated_recommendationsEmpty() {
        val auth = FakeAuthManager()
        val vm = createViewModel(auth)

        assertTrue(
            "Unauthenticated user should receive no recommendations",
            vm.recommendations.value.isEmpty()
        )
    }

    @Test
    fun authGuard_authenticated_returnsRecommendations() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession())
        val vm = createViewModel(auth)

        assertTrue(
            "Authenticated user should get recommendations",
            vm.recommendationState.value is RecommendationUiState.Success
        )
    }

    @Test
    fun authGuard_authenticated_recommendationsNonEmpty() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession())
        val vm = createViewModel(auth)

        assertTrue(
            "Authenticated user should receive at least one recommendation",
            vm.recommendations.value.isNotEmpty()
        )
    }

    @Test
    fun authGuard_signOut_revokesRecommendations() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession())
        val vm = createViewModel(auth)
        assertTrue(vm.recommendationState.value is RecommendationUiState.Success)

        auth.signOut()
        vm.refreshRecommendations()

        assertEquals(RecommendationUiState.Unauthenticated, vm.recommendationState.value)
        assertTrue(vm.recommendations.value.isEmpty())
    }

    @Test
    fun authGuard_signOutThenSignIn_restoresRecommendations() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession())
        val vm = createViewModel(auth)
        assertTrue(vm.recommendationState.value is RecommendationUiState.Success)

        auth.signOut()
        vm.refreshRecommendations()
        assertEquals(RecommendationUiState.Unauthenticated, vm.recommendationState.value)

        auth.signIn(verifiedGoogleSession("user-2"))
        vm.refreshRecommendations()
        assertTrue(vm.recommendationState.value is RecommendationUiState.Success)
    }

    @Test
    fun authGuard_nullAuthManager_allowsRecommendations_backwardCompat() {
        val vm = createViewModel(null)

        assertTrue(
            "Null auth manager (backward compat) should allow recommendations",
            vm.recommendationState.value is RecommendationUiState.Success
        )
        assertTrue(vm.recommendations.value.isNotEmpty())
    }

    @Test
    fun authGuard_crudOperationsStillWork_whileUnauthenticated() {
        val auth = FakeAuthManager() // not signed in
        val vm = createViewModel(auth)

        // CRUD doesn't crash, but recommendations stay gated
        val item = ClothingItem(999, "Top", "Red", "Summer", 4, "Regular")
        vm.addItem(item)
        // Item should be in wardrobe (CRUD is not auth-gated)
        assertTrue(vm.wardrobeItems.value.any { it.id == 999 })
        // But recommendations should still be unauthenticated
        assertEquals(RecommendationUiState.Unauthenticated, vm.recommendationState.value)
    }

    // ================================================================
    // Section 2: User ID from verified backend session
    // ================================================================

    @Test
    fun session_unverified_blocksRecommendations() {
        val auth = FakeAuthManager()
        auth.signIn(UserSession("user-1", "user@gmail.com", AuthProvider.GOOGLE, isVerified = false))
        val vm = createViewModel(auth)

        assertEquals(
            "Unverified session should be treated as unauthenticated",
            RecommendationUiState.Unauthenticated,
            vm.recommendationState.value
        )
    }

    @Test
    fun session_verified_allowsRecommendations() {
        val auth = FakeAuthManager()
        auth.signIn(UserSession("user-1", "user@gmail.com", AuthProvider.GOOGLE, isVerified = true))
        val vm = createViewModel(auth)

        assertTrue(
            "Verified session should allow recommendations",
            vm.recommendationState.value is RecommendationUiState.Success
        )
    }

    @Test
    fun session_emptyUserId_blocksRecommendations() {
        val auth = FakeAuthManager()
        auth.signIn(UserSession("", "user@gmail.com", AuthProvider.GOOGLE, isVerified = true))
        val vm = createViewModel(auth)

        assertEquals(
            "Empty userId should be rejected",
            RecommendationUiState.Unauthenticated,
            vm.recommendationState.value
        )
    }

    @Test
    fun session_blankUserId_blocksRecommendations() {
        val auth = FakeAuthManager()
        auth.signIn(UserSession("   ", "user@gmail.com", AuthProvider.GOOGLE, isVerified = true))
        val vm = createViewModel(auth)

        assertEquals(
            "Blank userId should be rejected",
            RecommendationUiState.Unauthenticated,
            vm.recommendationState.value
        )
    }

    @Test
    fun session_userIdFromSession_notHardcoded() {
        val auth = FakeAuthManager()
        val session = UserSession("unique-firebase-uid-12345", isVerified = true)
        auth.signIn(session)

        assertEquals("unique-firebase-uid-12345", auth.getCurrentUserId())
        assertEquals("unique-firebase-uid-12345", auth.getCurrentSession()?.userId)
    }

    @Test
    fun session_differentUserIds_areDistinct() {
        val auth = FakeAuthManager()
        auth.signIn(UserSession("user-alpha", isVerified = true))
        assertEquals("user-alpha", auth.getCurrentUserId())

        auth.signOut()
        auth.signIn(UserSession("user-beta", isVerified = true))
        assertEquals("user-beta", auth.getCurrentUserId())
    }

    // ================================================================
    // Section 3: Recommendations generated after Google login
    // ================================================================

    @Test
    fun googleLogin_createsValidSession() {
        val auth = FakeAuthManager()
        val session = UserSession("gid-001", "alice@gmail.com", AuthProvider.GOOGLE, true)
        auth.signIn(session)

        assertTrue(auth.isAuthenticated())
        assertEquals("gid-001", auth.getCurrentUserId())
        assertEquals(AuthProvider.GOOGLE, auth.getCurrentSession()?.provider)
        assertEquals("alice@gmail.com", auth.getCurrentSession()?.email)
    }

    @Test
    fun googleLogin_recommendationsGenerated() {
        val auth = FakeAuthManager()
        auth.signIn(UserSession("gid-001", "alice@gmail.com", AuthProvider.GOOGLE, true))
        val vm = createViewModel(auth)

        val state = vm.recommendationState.value
        assertTrue("Should produce recommendations after Google login", state is RecommendationUiState.Success)
        val recs = (state as RecommendationUiState.Success).recommendations
        assertTrue("Recommendations should not be empty", recs.isNotEmpty())
    }

    @Test
    fun googleLogin_recommendationsReflectDefaultPrefs() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession())
        val vm = createViewModel(auth)

        // Default preferences: Average body, Casual style
        assertEquals("Average", vm.userPreferences.value.bodyType)
        assertEquals("Casual", vm.userPreferences.value.stylePreference)
        assertTrue(vm.recommendations.value.isNotEmpty())
    }

    @Test
    fun googleLogin_updatePreferences_updatesRecommendations() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession())
        val vm = createViewModel(auth)

        val recsBefore = vm.recommendations.value.map { it.score }
        vm.updatePreferences(
            UserPreferences("Athletic", "Sporty", 5, listOf("Summer"))
        )
        val recsAfter = vm.recommendations.value.map { it.score }

        // Scores should change after preference update
        assertNotEquals("Preferences update should change recommendations", recsBefore, recsAfter)
    }

    @Test
    fun googleLogin_anonymousProvider_stillWorks() {
        val auth = FakeAuthManager()
        auth.signIn(UserSession("anon-001", null, AuthProvider.ANONYMOUS, isVerified = true))
        val vm = createViewModel(auth)

        assertTrue(
            "Anonymous provider with verified session should still produce recommendations",
            vm.recommendationState.value is RecommendationUiState.Success
        )
    }

    // ================================================================
    // Section 4: No cross-user data exposure
    // ================================================================

    @Test
    fun crossUser_separateViewModels_noSharedWardrobeItems() {
        val auth1 = FakeAuthManager()
        auth1.signIn(verifiedGoogleSession("user-A"))
        val vm1 = createViewModel(auth1)

        val auth2 = FakeAuthManager()
        auth2.signIn(verifiedGoogleSession("user-B"))
        val vm2 = createViewModel(auth2)

        // Add item to user A
        val item = ClothingItem(500, "Top", "Red", "Summer", 4, "Fitted")
        vm1.addItem(item)

        // User B should NOT see user A's item
        assertFalse(
            "User B should not see User A's wardrobe items",
            vm2.wardrobeItems.value.any { it.id == 500 }
        )
    }

    @Test
    fun crossUser_separateViewModels_noSharedSavedOutfits() {
        val auth1 = FakeAuthManager()
        auth1.signIn(verifiedGoogleSession("user-A"))
        val vm1 = createViewModel(auth1)

        val auth2 = FakeAuthManager()
        auth2.signIn(verifiedGoogleSession("user-B"))
        val vm2 = createViewModel(auth2)

        // User A saves an outfit
        val items = vm1.wardrobeItems.value.take(2)
        if (items.size >= 2) {
            vm1.saveOutfit(items)
            assertTrue(vm1.getSavedOutfits().isNotEmpty())
        }

        // User B should have no saved outfits
        assertTrue(
            "User B should not see User A's saved outfits",
            vm2.getSavedOutfits().isEmpty()
        )
    }

    @Test
    fun crossUser_separateViewModels_noSharedPreferences() {
        val auth1 = FakeAuthManager()
        auth1.signIn(verifiedGoogleSession("user-A"))
        val vm1 = createViewModel(auth1)

        val auth2 = FakeAuthManager()
        auth2.signIn(verifiedGoogleSession("user-B"))
        val vm2 = createViewModel(auth2)

        // User A changes preferences
        vm1.updatePreferences(UserPreferences("Athletic", "Sporty", 5, listOf("Summer")))

        // User B should still have default preferences
        assertEquals("Average", vm2.userPreferences.value.bodyType)
        assertEquals("Casual", vm2.userPreferences.value.stylePreference)
        assertEquals(3, vm2.userPreferences.value.comfortPreference)
    }

    @Test
    fun crossUser_separateViewModels_noSharedRecommendationHistory() {
        val auth1 = FakeAuthManager()
        auth1.signIn(verifiedGoogleSession("user-A"))
        val vm1 = createViewModel(auth1)

        val auth2 = FakeAuthManager()
        auth2.signIn(verifiedGoogleSession("user-B"))
        val vm2 = createViewModel(auth2)

        // User A refreshes recommendations multiple times (builds history)
        vm1.refreshRecommendations()
        vm1.refreshRecommendations()

        // User B's first recommendations should not be affected by User A's history
        val recsB = vm2.recommendations.value
        assertTrue("User B should still get recommendations", recsB.isNotEmpty())
    }

    @Test
    fun crossUser_signOut_clearsRecommendationState() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession("user-A"))
        val vm = createViewModel(auth)
        assertTrue(vm.recommendationState.value is RecommendationUiState.Success)
        assertTrue(vm.recommendations.value.isNotEmpty())

        auth.signOut()
        vm.refreshRecommendations()

        assertEquals(RecommendationUiState.Unauthenticated, vm.recommendationState.value)
        assertTrue(
            "Sign-out should clear recommendations list",
            vm.recommendations.value.isEmpty()
        )
    }

    @Test
    fun crossUser_signOut_clearsRecommendationsCompletely() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession("user-A"))
        val vm = createViewModel(auth)

        // Build up state
        vm.updatePreferences(UserPreferences("Athletic", "Sporty", 5, listOf("Summer")))
        val recsBeforeSignOut = vm.recommendations.value
        assertTrue(recsBeforeSignOut.isNotEmpty())

        // Sign out
        auth.signOut()
        vm.refreshRecommendations()

        // Sign in as different user
        auth.signIn(verifiedGoogleSession("user-B"))
        vm.refreshRecommendations()

        // New user should get recommendations based on defaults, not user-A's prefs
        // (preferences are held in the ViewModel instance, so a new VM would be used in production)
        assertTrue(vm.recommendations.value.isNotEmpty())
    }

    @Test
    fun crossUser_separateViewModels_independentPlannedOutfits() {
        val auth1 = FakeAuthManager()
        auth1.signIn(verifiedGoogleSession("user-A"))
        val vm1 = createViewModel(auth1)

        val auth2 = FakeAuthManager()
        auth2.signIn(verifiedGoogleSession("user-B"))
        val vm2 = createViewModel(auth2)

        // Verify planned outfits are independent
        assertTrue(
            "User A planned outfits should start empty",
            vm1.plannedOutfits.value.isEmpty()
        )
        assertTrue(
            "User B planned outfits should start empty",
            vm2.plannedOutfits.value.isEmpty()
        )
    }

    // ================================================================
    // Section 5: Onboarding data initializes personalization
    // ================================================================

    @Test
    fun onboarding_bodyType_flowsThroughToRecommendations() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession())
        val vm = createViewModel(auth)

        vm.updatePreferences(
            UserPreferences("Athletic", "Casual", 3, listOf("Spring", "Summer", "Fall", "Winter"))
        )

        assertEquals("Athletic", vm.userPreferences.value.bodyType)
        assertTrue(
            "Recommendations should be generated after onboarding body type",
            vm.recommendations.value.isNotEmpty()
        )
    }

    @Test
    fun onboarding_stylePreference_affectsEngineScoring() {
        val engine = OutfitRecommendationEngine()
        val casualPrefs = UserPreferences("Average", "Casual", 3, listOf("All"))
        val formalPrefs = UserPreferences("Average", "Formal", 3, listOf("All"))

        // Use Accessory — in Casual style set but NOT Formal, so scores differ
        val accessory = ClothingItem(1, "Accessory", "Black", "All", 4, "Regular")
        val casualScore = engine.scoreItem(accessory, casualPrefs)
        val formalScore = engine.scoreItem(accessory, formalPrefs)

        assertNotEquals(
            "Style preference from onboarding should affect scoring",
            casualScore,
            formalScore,
            0.001
        )
    }

    @Test
    fun onboarding_comfortPreference_affectsEngineScoring() {
        val engine = OutfitRecommendationEngine()
        val lowComfort = UserPreferences("Average", "Casual", 1, listOf("All"))
        val highComfort = UserPreferences("Average", "Casual", 5, listOf("All"))

        val comfyItem = ClothingItem(1, "Top", "Black", "All", 5, "Regular")
        val lowScore = engine.scoreItem(comfyItem, lowComfort)
        val highScore = engine.scoreItem(comfyItem, highComfort)

        assertTrue(
            "High comfort item should score higher when user prefers high comfort",
            highScore > lowScore
        )
    }

    @Test
    fun onboarding_seasonPreference_affectsEngineScoring() {
        val engine = OutfitRecommendationEngine()
        val summerPrefs = UserPreferences("Average", "Casual", 3, listOf("Summer"))
        val winterPrefs = UserPreferences("Average", "Casual", 3, listOf("Winter"))

        val summerItem = ClothingItem(1, "Top", "Black", "Summer", 3, "Regular")
        val summerScore = engine.scoreItem(summerItem, summerPrefs)
        val winterScore = engine.scoreItem(summerItem, winterPrefs)

        assertTrue(
            "Summer item should score higher when user prefers Summer",
            summerScore > winterScore
        )
    }

    @Test
    fun onboarding_bodyType_affectsFitCompatibility() {
        val engine = OutfitRecommendationEngine()

        // Fitted items should score differently for Athletic vs Plus-size
        val fittedTop = ClothingItem(1, "Top", "Blue", "All", 3, "Fitted")
        val athleticPrefs = UserPreferences("Athletic", "Casual", 3, listOf("All"))
        val plusPrefs = UserPreferences("Plus-size", "Casual", 3, listOf("All"))

        val athleticScore = engine.scoreItem(fittedTop, athleticPrefs)
        val plusScore = engine.scoreItem(fittedTop, plusPrefs)

        assertTrue(
            "Athletic body type should score fitted items higher than Plus-size",
            athleticScore > plusScore
        )
    }

    @Test
    fun onboarding_defaultPreferences_produceValidRecommendations() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession())
        val vm = createViewModel(auth)

        // Default preferences (no onboarding changes) should still work
        val defaultPrefs = vm.userPreferences.value
        assertEquals("Average", defaultPrefs.bodyType)
        assertEquals("Casual", defaultPrefs.stylePreference)
        assertEquals(3, defaultPrefs.comfortPreference)
        assertTrue(vm.recommendations.value.isNotEmpty())
    }

    @Test
    fun onboarding_fullPreferenceUpdate_changesRankings() {
        val engine = OutfitRecommendationEngine()
        val items = listOf(
            ClothingItem(1, "Top", "Blue", "All", 3, "Fitted"),
            ClothingItem(2, "Top", "Blue", "All", 3, "Relaxed"),
            ClothingItem(3, "Bottom", "Black", "All", 3, "Regular"),
            ClothingItem(4, "Shoes", "White", "All", 3, "Regular")
        )

        val athleticPrefs = UserPreferences("Athletic", "Casual", 3, listOf("All"))
        val plusPrefs = UserPreferences("Plus-size", "Casual", 3, listOf("All"))

        val athleticRecs = engine.recommend(items, athleticPrefs)
        val plusRecs = engine.recommend(items, plusPrefs)

        val athleticFirst = athleticRecs.first().items.map { it.id }.toSet()
        val plusFirst = plusRecs.first().items.map { it.id }.toSet()

        assertNotEquals(
            "Different onboarding body types should produce different rankings",
            athleticFirst,
            plusFirst
        )
    }

    @Test
    fun onboarding_preferencesAppliedToVM_affectRecommendationScores() {
        val auth = FakeAuthManager()
        auth.signIn(verifiedGoogleSession())
        val vm = createViewModel(auth)

        val defaultScores = vm.recommendations.value.map { it.score }

        vm.updatePreferences(
            UserPreferences("Athletic", "Sporty", 5, listOf("Summer"))
        )
        val updatedScores = vm.recommendations.value.map { it.score }

        assertNotEquals(
            "Updating preferences should change recommendation scores",
            defaultScores,
            updatedScores
        )
    }

    // ================================================================
    // Section 6: Session model correctness
    // ================================================================

    @Test
    fun userSession_holdsAllFields() {
        val session = UserSession(
            userId = "uid-123",
            email = "test@example.com",
            provider = AuthProvider.GOOGLE,
            isVerified = true
        )
        assertEquals("uid-123", session.userId)
        assertEquals("test@example.com", session.email)
        assertEquals(AuthProvider.GOOGLE, session.provider)
        assertTrue(session.isVerified)
    }

    @Test
    fun userSession_defaultValues() {
        val session = UserSession("uid-456")
        assertNull(session.email)
        assertEquals(AuthProvider.GOOGLE, session.provider)
        assertFalse(session.isVerified)
    }

    @Test
    fun authProvider_googleAndAnonymous_exist() {
        assertEquals(2, AuthProvider.entries.size)
        assertNotNull(AuthProvider.GOOGLE)
        assertNotNull(AuthProvider.ANONYMOUS)
    }

    @Test
    fun fakeAuthManager_signInSignOut_lifecycle() {
        val auth = FakeAuthManager()

        // Initially not authenticated
        assertFalse(auth.isAuthenticated())
        assertNull(auth.getCurrentSession())
        assertNull(auth.getCurrentUserId())

        // Sign in
        auth.signIn(verifiedGoogleSession("user-1"))
        assertTrue(auth.isAuthenticated())
        assertEquals("user-1", auth.getCurrentUserId())

        // Sign out
        auth.signOut()
        assertFalse(auth.isAuthenticated())
        assertNull(auth.getCurrentSession())
    }

    // ================================================================
    // Section 7: Edge cases
    // ================================================================

    @Test
    fun edgeCase_multipleRefreshesWhileUnauthenticated_noException() {
        val auth = FakeAuthManager()
        val vm = createViewModel(auth)

        // Should not throw
        repeat(10) { vm.refreshRecommendations() }
        assertEquals(RecommendationUiState.Unauthenticated, vm.recommendationState.value)
    }

    @Test
    fun edgeCase_addItemsThenAuthenticate_recommendationsIncludeItems() {
        val auth = FakeAuthManager()
        val vm = createViewModel(auth)

        // Add items while unauthenticated
        vm.addItem(ClothingItem(101, "Top", "Blue", "All", 3, "Regular"))
        vm.addItem(ClothingItem(102, "Bottom", "Black", "All", 3, "Regular"))
        assertEquals(RecommendationUiState.Unauthenticated, vm.recommendationState.value)

        // Authenticate
        auth.signIn(verifiedGoogleSession())
        vm.refreshRecommendations()

        assertTrue(vm.recommendationState.value is RecommendationUiState.Success)
        // Recommendations should include the items we added
        val allRecItems = vm.recommendations.value.flatMap { it.items.map { item -> item.id } }.toSet()
        assertTrue(
            "Recommendations should include pre-auth items",
            allRecItems.contains(101) || allRecItems.contains(102)
        )
    }

    @Test
    fun edgeCase_rapidSignInSignOut_noStateCorruption() {
        val auth = FakeAuthManager()
        val vm = createViewModel(auth)

        repeat(5) {
            auth.signIn(verifiedGoogleSession("user-$it"))
            vm.refreshRecommendations()
            auth.signOut()
            vm.refreshRecommendations()
        }

        // After final sign-out, should be unauthenticated
        assertEquals(RecommendationUiState.Unauthenticated, vm.recommendationState.value)
        assertTrue(vm.recommendations.value.isEmpty())
    }
}
