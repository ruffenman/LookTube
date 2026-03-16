package com.looktube.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class LookTubeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsMergedLibraryShell() {
        composeRule.onNodeWithText("LookTube").assertIsDisplayed()
        composeRule.onNodeWithText("Connect your Giant Bomb Premium feed").assertIsDisplayed()
        composeRule.onNodeWithText("Premium feed URL").assertIsDisplayed()
        composeRule.onNodeWithText("Sync Premium feed").assertIsDisplayed()
        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Videos").assertIsDisplayed()
        composeRule.onNodeWithText("Groups").performClick()
        composeRule.onNodeWithText("Sync a feed first to browse grouped videos.").assertIsDisplayed()
        composeRule.onNodeWithText("Player").performClick()
        composeRule.onNodeWithText("Choose something to watch").assertIsDisplayed()
    }
}
