package com.medminder.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.model.FoodRelation
import com.medminder.app.domain.model.IntakeAction
import com.medminder.app.feature.today.TodayDoseUiModel
import com.medminder.app.feature.today.TodayScreen
import com.medminder.app.feature.today.TodayUiState
import org.junit.Rule
import org.junit.Test

class RtlLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rtlContentRenders() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                TodayScreen(
                    state = TodayUiState(
                        items = listOf(
                            TodayDoseUiModel(
                                id = "1",
                                timeLabel = "08:00",
                                medicationName = "תרופה לדוגמה",
                                doseLabel = "1 טבליה",
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
        }

        rule.onNodeWithText("תרופה לדוגמה").assertIsDisplayed()
    }
}
