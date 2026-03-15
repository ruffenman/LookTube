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
    fun showsTopLevelShell() {
        composeRule.onNodeWithText("LookTube").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in to Giant Bomb Premium").assertIsDisplayed()
        composeRule.onNodeWithText("Premium feed URL").assertIsDisplayed()
        composeRule.onNodeWithText("Sync Premium feed").assertIsDisplayed()
        composeRule.onNodeWithText("Player").performClick()
        composeRule.onNodeWithText("Choose something to watch").assertIsDisplayed()
        composeRule.onNodeWithText("Shows").performClick()
        composeRule.onNodeWithText("Sync a feed first to browse shows and jump into videos by series.").assertIsDisplayed()
    }
}
