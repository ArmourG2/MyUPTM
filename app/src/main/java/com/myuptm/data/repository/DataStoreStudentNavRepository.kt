package com.myuptm.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One DataStore file for the whole process (same pattern as the plan prefs).
private val Context.studentNavDataStore: DataStore<Preferences> by preferencesDataStore(name = "student_nav_prefs")

// Sprint 14 (owner ask): a student whose timetable EVER had a class keeps the
// full 5-tab bottom nav forever — even after a one-off class expires and the
// timetable goes empty again. The upgrade flag is device-local and per-email
// (account switches never mix it), set automatically the first time a class is
// visible for their week.
class DataStoreStudentNavRepository(
    private val context: Context,
    private val userEmail: String
) {

    private val flagKey = stringSetPreferencesKey("nav_upgraded_$userEmail")

    // The signed-in email sits in the set once this account has been upgraded.
    fun observeUpgraded(): Flow<Boolean> =
        context.studentNavDataStore.data.map { prefs ->
            userEmail in prefs[flagKey].orEmpty()
        }

    suspend fun markUpgraded() {
        context.studentNavDataStore.edit { prefs ->
            prefs[flagKey] = prefs[flagKey].orEmpty() + userEmail
        }
    }
}
