package com.medminder.app.domain.model

enum class DoseStatus { PENDING, MISSED, TAKEN }

enum class IntakeAction { TAKEN }

enum class FoodRelation { BEFORE_FOOD, AFTER_FOOD, WITH_FOOD, NONE }

enum class ActionSource { APP, NOTIFICATION, SYSTEM }

enum class ScheduleMode { FIXED_TIMES, EVERY_X_HOURS, EVERY_X_DAYS }
