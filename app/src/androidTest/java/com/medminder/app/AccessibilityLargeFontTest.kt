package com.medminder.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.model.FoodRelation
import com.medminder.app.domain.model.IntakeAction
import com.medminder.app.feature.today.TodayDoseUiModel
import com.medminder.app.feature.today.TodayScreen
import com.medminder.app.feature.today.TodayUiState
import org.junit.Rule
import org.junit.Test

class AccessibilityLargeFontTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun todayScreenActionsAreVisible() {
        rule.setContent {
            TodayScreen(
                state = TodayUiState(
                    items = listOf(
                        TodayDoseUiModel(
                            id = "1",
                            timeLabel = "08:00",
                            medicationName = "אספירין",
                            doseLabel = "1 כדור",
                            foodRelation = FoodRelation.NONE,
                            notes = "",
                            status = DoseStatus.PENDING,
                            canTakeNow = true
                        )
                    )
                ),
                onAction = { _, _: IntakeAction -> },
                onMarkAsNeededTaken = {},
                onAddOneTimeMedicationIntake = {},
                onEncouragementClick = {},
                onRequestNotificationPermission = {},
                onOpenNotificationSettings = {}
            )
        }

        rule.onNodeWithText("לקחתי").assertIsDisplayed()
        rule.onAllNodesWithText("דחיתי").assertCountEquals(0)
        rule.onAllNodesWithText("דילגתי").assertCountEquals(0)
    }
}
