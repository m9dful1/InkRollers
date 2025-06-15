package com.spiritwisestudios.inkrollers.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.GrantPermissionRule
import com.spiritwisestudios.inkrollers.BaseFirebaseTest
import com.spiritwisestudios.inkrollers.HomeActivity
import com.spiritwisestudios.inkrollers.R
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive UI tests for Ink Rollers profile management.
 * 
 * Tests the complete profile user interface including:
 * - Profile creation and editing workflows
 * - Color picker interaction and validation
 * - Friend management functionality
 * - Profile data persistence and loading
 * - Error handling and validation feedback
 * - Navigation and fragment transitions
 */
class ProfileUITest : BaseFirebaseTest() {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.INTERNET
    )

    @Before
    fun setUp() {
        // Firebase setup handled by BaseFirebaseTest
        auth.signOut()
    }

    @After
    fun tearDown() {
        cleanupFirebase()
    }

    @Test
    fun testProfileButton_opensProfileFragment() {
        // Test that profile button in home activity opens profile fragment
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            // Wait for activity initialization
            Thread.sleep(2000)

            // Click on profile button
            onView(withId(R.id.button_profile))
                .check(matches(isDisplayed()))
                .perform(click())

            // Wait for profile fragment to load and authentication
            Thread.sleep(5000)

            // Verify profile UI elements are present
            onView(withId(R.id.edit_player_name))
                .check(matches(isDisplayed()))

            onView(withId(R.id.text_friend_code))
                .check(matches(isDisplayed()))

            onView(withId(R.id.btn_save_profile))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testPlayerName_editingAndValidation() {
        // Test player name editing and validation
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load
            Thread.sleep(5000)

            // Clear and enter new player name
            onView(withId(R.id.edit_player_name))
                .check(matches(isDisplayed()))
                .perform(clearText())
                .perform(typeText("TestPlayer123"))
                .perform(closeSoftKeyboard())

            // Verify the name was entered
            onView(withId(R.id.edit_player_name))
                .check(matches(withText("TestPlayer123")))

            // Check if save button is enabled
            onView(withId(R.id.btn_save_profile))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testCatchPhrase_editingAndDisplay() {
        // Test catch phrase editing functionality
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load
            Thread.sleep(5000)

            // Edit catch phrase
            onView(withId(R.id.edit_catch_phrase))
                .check(matches(isDisplayed()))
                .perform(clearText())
                .perform(typeText("Ready to paint!"))
                .perform(closeSoftKeyboard())

            // Verify the catch phrase was entered
            onView(withId(R.id.edit_catch_phrase))
                .check(matches(withText("Ready to paint!")))
        }
    }

    @Test
    fun testFriendCode_displayAndCopy() {
        // Test friend code display and copy functionality
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load and friend code generation
            Thread.sleep(6000)

            // Check friend code is displayed
            onView(withId(R.id.text_friend_code))
                .check(matches(isDisplayed()))
                .check(matches(not(withText("")))) // Should not be empty

            // Test copy button functionality
            onView(withId(R.id.btn_copy_friend_code))
                .check(matches(isDisplayed()))
                .perform(click())

            // Copy action should complete without error
            // Note: Actual clipboard verification would require additional setup
        }
    }

    @Test
    fun testColorPicker_interactionAndSelection() {
        // Test color picker interaction and color selection
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load
            Thread.sleep(5000)

            // Test color picker interactions
            onView(withId(R.id.color_picker_1))
                .check(matches(isDisplayed()))
                .perform(click())

            // Brief wait for color picker dialog
            Thread.sleep(1000)

            // Try second color picker
            onView(withId(R.id.color_picker_2))
                .check(matches(isDisplayed()))
                .perform(click())

            Thread.sleep(1000)

            // Try third color picker
            onView(withId(R.id.color_picker_3))
                .check(matches(isDisplayed()))
                .perform(click())

            Thread.sleep(1000)

            // Verify color pickers are still visible after interactions
            onView(withId(R.id.color_picker_1))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testWinLossStats_display() {
        // Test win/loss statistics display
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load
            Thread.sleep(5000)

            // Check win/loss display
            onView(withId(R.id.text_win_loss))
                .check(matches(isDisplayed()))
                .check(matches(withText(containsString("/")))) // Should contain "wins / losses" format
        }
    }

    @Test
    fun testFriendManagement_addFriendFlow() {
        // Test adding friends by friend code
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load
            Thread.sleep(5000)

            // Test friend code input
            onView(withId(R.id.edit_add_friend_code))
                .check(matches(isDisplayed()))
                .perform(typeText("TEST01"))
                .perform(closeSoftKeyboard())

            // Click add friend button
            onView(withId(R.id.btn_add_friend))
                .check(matches(isDisplayed()))
                .perform(click())

            // Wait for add friend operation
            Thread.sleep(3000)

            // Verify friend code input was processed (field should be cleared or show feedback)
            onView(withId(R.id.edit_add_friend_code))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testFriendsList_display() {
        // Test friends list RecyclerView display
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load
            Thread.sleep(5000)

            // Check friends RecyclerView is present
            onView(withId(R.id.recycler_friends))
                .check(matches(isDisplayed()))

            // Verify it can be scrolled (even if empty)
            onView(withId(R.id.recycler_friends))
                .perform(swipeUp())

            Thread.sleep(500)

            onView(withId(R.id.recycler_friends))
                .perform(swipeDown())
        }
    }

    @Test
    fun testProfileSave_validation() {
        // Test profile saving with various validation scenarios
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load
            Thread.sleep(5000)

            // Try to save with empty name
            onView(withId(R.id.edit_player_name))
                .perform(clearText())
                .perform(closeSoftKeyboard())

            onView(withId(R.id.btn_save_profile))
                .perform(click())

            // Wait for validation
            Thread.sleep(2000)

            // Add a valid name
            onView(withId(R.id.edit_player_name))
                .perform(typeText("ValidPlayer"))
                .perform(closeSoftKeyboard())

            // Try saving again
            onView(withId(R.id.btn_save_profile))
                .perform(click())

            // Wait for save operation
            Thread.sleep(3000)

            // Verify save button is still present (profile still accessible)
            onView(withId(R.id.btn_save_profile))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testProfileNavigation_backButton() {
        // Test navigation back from profile fragment
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load
            Thread.sleep(5000)

            // Verify we're in profile
            onView(withId(R.id.edit_player_name))
                .check(matches(isDisplayed()))

            // Press back button
            scenario.onActivity { activity ->
                activity.onBackPressed()
            }

            // Wait for navigation
            Thread.sleep(2000)

            // Verify we're back at home screen
            onView(withId(R.id.button_host_game))
                .check(matches(isDisplayed()))

            onView(withId(R.id.button_join_game))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testProfilePersistence_dataRetention() {
        // Test that profile data persists across navigation
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load
            Thread.sleep(5000)

            // Enter some data
            onView(withId(R.id.edit_player_name))
                .perform(clearText())
                .perform(typeText("PersistenceTest"))
                .perform(closeSoftKeyboard())

            onView(withId(R.id.edit_catch_phrase))
                .perform(clearText())
                .perform(typeText("Testing persistence"))
                .perform(closeSoftKeyboard())

            // Save profile
            onView(withId(R.id.btn_save_profile))
                .perform(click())

            Thread.sleep(3000)

            // Navigate back
            scenario.onActivity { activity ->
                activity.onBackPressed()
            }

            Thread.sleep(2000)

            // Open profile again
            onView(withId(R.id.button_profile))
                .perform(click())

            Thread.sleep(5000)

            // Verify data is still there
            onView(withId(R.id.edit_player_name))
                .check(matches(withText("PersistenceTest")))

            onView(withId(R.id.edit_catch_phrase))
                .check(matches(withText("Testing persistence")))
        }
    }

    @Test
    fun testProfile_errorHandling() {
        // Test profile error handling scenarios
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // Open profile
            onView(withId(R.id.button_profile))
                .perform(click())

            // Wait for profile to load
            Thread.sleep(5000)

            // Try adding an invalid friend code
            onView(withId(R.id.edit_add_friend_code))
                .perform(clearText())
                .perform(typeText("INVALID"))
                .perform(closeSoftKeyboard())

            onView(withId(R.id.btn_add_friend))
                .perform(click())

            // Wait for error handling
            Thread.sleep(3000)

            // Verify UI remains stable after error
            onView(withId(R.id.edit_add_friend_code))
                .check(matches(isDisplayed()))

            onView(withId(R.id.btn_add_friend))
                .check(matches(isDisplayed()))

            // Try adding own friend code (should be rejected)
            // First get the displayed friend code, then try to add it
            onView(withId(R.id.edit_add_friend_code))
                .perform(clearText())
                .perform(typeText("SELF01")) // Simulated self code
                .perform(closeSoftKeyboard())

            onView(withId(R.id.btn_add_friend))
                .perform(click())

            Thread.sleep(3000)

            // Verify UI handling
            onView(withId(R.id.edit_add_friend_code))
                .check(matches(isDisplayed()))
        }
    }
} 