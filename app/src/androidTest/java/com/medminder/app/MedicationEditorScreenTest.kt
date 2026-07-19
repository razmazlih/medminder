package com.medminder.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.medminder.app.domain.model.FoodRelation
import com.medminder.app.domain.model.Medication
import com.medminder.app.domain.model.ScheduleMode
import com.medminder.app.feature.medications.MedicationEditorScreen
import com.medminder.app.feature.medications.MedicationFormState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class MedicationEditorScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun switchingToIntervalModeShowsPreview() {
        rule.setContent {
            MedicationEditorScreen(
                existing = null,
                errorMessage = null,
                onBack = {},
                onSave = {}
            )
        }

        rule.onNodeWithText("כל כמה שעות").performClick()
        rule.onNodeWithText("שעות שייווצרו: 08:00, 16:00").assertIsDisplayed()
    }

    @Test
    fun fixedTimesChipRemovalAffectsSavedForm() {
        var saved: MedicationFormState? = null

        rule.setContent {
            MedicationEditorScreen(
                existing = existingMedication(),
                errorMessage = null,
                onBack = {},
                onSave = { saved = it }
            )
        }

        rule.onNodeWithText("20:00  הסר").performClick()
        rule.onNodeWithText("שמירה").performClick()

        rule.runOnIdle {
            assertNotNull(saved)
            assertEquals(ScheduleMode.FIXED_TIMES, saved?.scheduleMode)
            assertEquals(listOf(LocalTime.of(8, 0)), saved?.fixedTimes)
        }
    }

    private fun existingMedication(): Medication {
        return Medication(
            id = "med-1",
            ownerProfileId = "owner_primary",
            name = "תרופה לדוגמה",
            doseText = "1",
            doseUnit = "כדור",
            foodRelation = FoodRelation.NONE,
            scheduleMode = ScheduleMode.FIXED_TIMES,
            intervalHours = null,
            windowStartTime = null,
            windowEndTime = null,
            startDate = LocalDate.of(2026, 2, 13),
            endDate = null,
            notes = "",
            weekdays = (1..7).toList(),
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            isActive = true
        )
    }
}
