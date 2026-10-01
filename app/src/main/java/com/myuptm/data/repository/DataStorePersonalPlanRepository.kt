package com.myuptm.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myuptm.domain.model.AppNotification
import com.myuptm.domain.model.NotificationType
import com.myuptm.domain.model.PersonalPlan
import com.myuptm.domain.repository.PersonalPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One DataStore file for the whole process (same pattern as DataStoreSettingsRepository).
private val Context.plansDataStore: DataStore<Preferences> by preferencesDataStore(name = "personal_plans_prefs")

// Sprint 7B: device-local student data. Plans and clash warnings are keyed by the
// signed-in user's email so switching accounts on one device never mixes data.
// No serialization dependency — each record is one delimited string inside a StringSet.
class DataStorePersonalPlanRepository(
    private val context: Context,
    userEmail: String
) : PersonalPlanRepository {

    private val plansKey = stringSetPreferencesKey("plans_$userEmail")
    private val warningsKey = stringSetPreferencesKey("warnings_$userEmail")

    override fun observePlans(): Flow<List<PersonalPlan>> =
        context.plansDataStore.data.map { prefs ->
            prefs[plansKey].orEmpty().mapNotNull(::decodePlan).sortedWith(
                compareBy({ it.dayIndex }, { it.startTime })
            )
        }

    override suspend fun addPlan(plan: PersonalPlan) {
        context.plansDataStore.edit { prefs ->
            prefs[plansKey] = (prefs[plansKey].orEmpty() + encodePlan(plan)).toSet()
        }
    }

    override suspend fun updatePlan(plan: PersonalPlan) {
        context.plansDataStore.edit { prefs ->
            prefs[plansKey] = (prefs[plansKey].orEmpty()
                .filterNot { encoded -> decodePlan(encoded)?.id == plan.id } + encodePlan(plan)).toSet()
        }
    }

    override suspend fun removePlan(planId: String) {
        context.plansDataStore.edit { prefs ->
            prefs[plansKey] = prefs[plansKey].orEmpty()
                .filterNot { encoded -> decodePlan(encoded)?.id == planId }.toSet()
        }
    }

    override fun observeWarnings(): Flow<List<AppNotification>> =
        context.plansDataStore.data.map { prefs ->
            prefs[warningsKey].orEmpty().mapNotNull(::decodeWarning).sortedByDescending { it.createdAtEpochMs }
        }

    override suspend fun addWarning(warning: AppNotification) {
        context.plansDataStore.edit { prefs ->
            prefs[warningsKey] = prefs[warningsKey].orEmpty() + encodeWarning(warning)
        }
    }

    override suspend fun clearWarnings() {
        context.plansDataStore.edit { prefs ->
            prefs.remove(warningsKey)
        }
    }

    // ---- Encoding: id|dayIndex|start|end|title|venue ----
    // "|" is stripped from user text so records stay parseable.
    private fun encodePlan(plan: PersonalPlan): String =
        listOf(
            plan.id,
            plan.dayIndex.toString(),
            plan.startTime,
            plan.endTime,
            sanitize(plan.title),
            sanitize(plan.venue.orEmpty())
        ).joinToString(DELIMITER)

    private fun decodePlan(encoded: String): PersonalPlan? {
        val parts = encoded.split(DELIMITER)
        if (parts.size != 6) return null
        return PersonalPlan(
            id = parts[0],
            title = parts[4],
            dayIndex = parts[1].toIntOrNull() ?: return null,
            startTime = parts[2],
            endTime = parts[3],
            venue = parts[5].ifEmpty { null }
        )
    }

    // ---- Encoding: id|title|message|sender|createdAt ----
    private fun encodeWarning(notification: AppNotification): String =
        listOf(
            notification.id,
            sanitize(notification.title),
            sanitize(notification.message),
            sanitize(notification.senderName),
            notification.createdAtEpochMs.toString()
        ).joinToString(DELIMITER)

    private fun decodeWarning(encoded: String): AppNotification? {
        val parts = encoded.split(DELIMITER)
        if (parts.size != 5) return null
        return AppNotification(
            id = parts[0],
            type = NotificationType.CLASH_WARNING,
            title = parts[1],
            message = parts[2],
            senderName = parts[3],
            createdAtEpochMs = parts[4].toLongOrNull() ?: 0L
        )
    }

    private fun sanitize(text: String) = text.replace(DELIMITER, " ").trim()

    private companion object {
        const val DELIMITER = "|"
    }
}