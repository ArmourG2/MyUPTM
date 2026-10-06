package com.myuptm.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One DataStore instance for the whole process (same pattern as the plans store).
private val Context.inboxDataStore: DataStore<Preferences> by preferencesDataStore(name = "inbox_seen_prefs")

// Sprint 9: per-account inbox reading position.
//  - lastSeen: timestamp for the "compact period" of the unread dot / popups.
//  - readIds: notifications the user has OPENED (auto-disappear from the inbox)
//    or dismissed with the Clear button. Firestore docs are NEVER deleted — they
//    carry the demo payload (warning letters, letter submissions) — hiding is
//    per-account and local.
class DataStoreInboxSeenRepository(
    private val context: Context,
    userEmail: String
) {
    private val key = longPreferencesKey("lastSeen_$userEmail")
    private val readKey = stringSetPreferencesKey("readIds_$userEmail")

    fun observeLastSeen(): Flow<Long> =
        context.inboxDataStore.data.map { prefs -> prefs[key] ?: 0L }

    fun observeReadIds(): Flow<Set<String>> =
        context.inboxDataStore.data.map { prefs -> prefs[readKey].orEmpty().toSet() }

    suspend fun markSeen(epochMs: Long) {
        context.inboxDataStore.edit { prefs ->
            prefs[key] = epochMs
        }
    }

    suspend fun markRead(id: String, allIds: Collection<String> = listOf(id)) {
        context.inboxDataStore.edit { prefs ->
            val current = prefs[readKey].orEmpty().toSet()
            prefs[readKey] = (current + allIds).toSet()
        }
    }
}
