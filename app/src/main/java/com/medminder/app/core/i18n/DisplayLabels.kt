package com.medminder.app.core.i18n

import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.model.FoodRelation
import com.medminder.app.domain.model.ScheduleMode

fun FoodRelation.displayLabelHe(): String = when (this) {
    FoodRelation.BEFORE_FOOD -> "לפני אוכל"
    FoodRelation.AFTER_FOOD -> "אחרי אוכל"
    FoodRelation.WITH_FOOD -> "עם אוכל"
    FoodRelation.NONE -> "ללא קשר לאוכל"
}

fun DoseStatus.displayLabelHe(): String = when (this) {
    DoseStatus.PENDING -> "ממתין"
    DoseStatus.MISSED -> "הוחמץ"
    DoseStatus.TAKEN -> "נלקח"
}

fun ActionSource.displayLabelHe(): String = when (this) {
    ActionSource.APP -> "באפליקציה"
    ActionSource.NOTIFICATION -> "מהתראה"
    ActionSource.SYSTEM -> "סנכרון מערכת"
}

fun ScheduleMode.displayLabelHe(): String = when (this) {
    ScheduleMode.FIXED_TIMES -> "שעות קבועות"
    ScheduleMode.EVERY_X_HOURS -> "כל כמה שעות"
    ScheduleMode.EVERY_X_DAYS -> "כל כמה ימים"
}
