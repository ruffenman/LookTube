package com.looktube.app

import androidx.compose.ui.test.assertIsDisplayed
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
        composeRule.onNodeWithText("Choose the sign-in strategy to validate first.").assertIsDisplayed()
    }
}
