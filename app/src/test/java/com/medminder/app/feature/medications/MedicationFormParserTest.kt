package com.medminder.app.feature.medications

import com.medminder.app.domain.model.FoodRelation
import com.medminder.app.domain.model.ScheduleMode
import com.medminder.app.domain.usecase.ScheduleEngine
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationFormParserTest {

    private val scheduleEngine = ScheduleEngine()

    @Test
    fun `fixed times mode keeps selected times`() {
        val form = MedicationFormState(
            name = "אקמול",
            doseText = "1",
            doseUnit = "כדור",
            foodRelation = FoodRelation.NONE,
            scheduleMode = ScheduleMode.FIXED_TIMES,
            fixedTimes = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0), LocalTime.of(8, 0)),
            startDate = LocalDate.of(2026, 2, 13),
            weekdays = listOf(1, 2, 3)
        )

        val parsed = parseMedicationForm(form, scheduleEngine) as ParsedMedicationResult.Success

        assertEquals(ScheduleMode.FIXED_TIMES, parsed.value.scheduleMode)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), parsed.value.times)
        assertEquals(null, parsed.value.intervalHours)
    }

    @Test
    fun `every x hours mode generates times and keeps config`() {
        val form = MedicationFormState(
            name = "ויטמין D",
            doseText = "1",
            doseUnit = "טיפה",
            foodRelation = FoodRelation.AFTER_FOOD,
            scheduleMode = ScheduleMode.EVERY_X_HOURS,
            intervalHours = 6,
            windowStartTime = LocalTime.of(6, 0),
            windowEndTime = LocalTime.of(18, 0),
            startDate = LocalDate.of(2026, 2, 13),
            weekdays = (1..7).toList()
        )

        val parsed = parseMedicationForm(form, scheduleEngine) as ParsedMedicationResult.Success

        assertEquals(ScheduleMode.EVERY_X_HOURS, parsed.value.scheduleMode)
        assertEquals(6, parsed.value.intervalHours)
        assertEquals(LocalTime.of(6, 0), parsed.value.windowStartTime)
        assertEquals(LocalTime.of(18, 0), parsed.value.windowEndTime)
        assertEquals(
            listOf(LocalTime.of(6, 0), LocalTime.of(12, 0), LocalTime.of(18, 0)),
            parsed.value.times
        )
    }

    @Test
    fun `invalid time window returns error in hebrew`() {
        val form = MedicationFormState(
            name = "אנטיביוטיקה",
            scheduleMode = ScheduleMode.EVERY_X_HOURS,
            intervalHours = 8,
            windowStartTime = LocalTime.of(22, 0),
            windowEndTime = LocalTime.of(8, 0)
        )

        val parsed = parseMedicationForm(form, scheduleEngine)

        assertTrue(parsed is ParsedMedicationResult.Error)
        assertEquals("שעת הסיום חייבת להיות אחרי שעת ההתחלה", (parsed as ParsedMedicationResult.Error).message)
    }

    @Test
    fun `every x days mode keeps fixed times and interval`() {
        val form = MedicationFormState(
            name = "פרוביוטיקה",
            scheduleMode = ScheduleMode.EVERY_X_DAYS,
            intervalDays = 3,
            fixedTimes = listOf(LocalTime.of(9, 0), LocalTime.of(9, 0), LocalTime.of(21, 0)),
            startDate = LocalDate.of(2026, 2, 13)
        )

        val parsed = parseMedicationForm(form, scheduleEngine) as ParsedMedicationResult.Success

        assertEquals(ScheduleMode.EVERY_X_DAYS, parsed.value.scheduleMode)
        assertEquals(3, parsed.value.intervalHours)
        assertEquals(listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)), parsed.value.times)
        assertEquals(null, parsed.value.windowStartTime)
        assertEquals(null, parsed.value.windowEndTime)
    }
}
