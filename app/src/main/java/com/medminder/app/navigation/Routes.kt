package com.medminder.app.navigation

object Routes {
    const val Onboarding = "onboarding"
    const val Today = "today"
    const val Medications = "medications"
    const val AsNeededMedications = "as_needed_medications"
    const val MedicationsRoot = "medications_root"
    const val MedicationEditor = "medication_editor"
    const val History = "history"
    const val Settings = "settings"
    const val Missed = "missed"
    const val AppLock = "app_lock"
    const val HiddenLog = "hidden_log"

    const val MedicationIdArg = "medicationId"
    const val ForceLockArg = "forceLock"

    fun medicationEditorRoute(medicationId: String? = null): String {
        return if (medicationId == null) "$MedicationEditor?$MedicationIdArg="
        else "$MedicationEditor?$MedicationIdArg=$medicationId"
    }

    fun appLockRoute(forceLock: Boolean): String = "$AppLock?$ForceLockArg=$forceLock"
}
