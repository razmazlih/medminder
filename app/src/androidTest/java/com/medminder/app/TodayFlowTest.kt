package com.medminder.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class TodayFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingScreenIsShownOnFreshStart() {
        rule.onNodeWithText("ברוכים הבאים").assertIsDisplayed()
    }
}
