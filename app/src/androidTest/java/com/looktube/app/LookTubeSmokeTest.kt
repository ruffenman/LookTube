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
        composeRule.onNodeWithText("By show").assertIsDisplayed()
        composeRule.onNodeWithText("Sort: Latest").assertIsDisplayed()
        composeRule.onNodeWithText("Sync your Premium feed on Auth to load your library.").assertIsDisplayed()
        composeRule.onNodeWithText("Player").performClick()
        composeRule.onNodeWithText("Nothing queued yet").assertIsDisplayed()
    }
}
