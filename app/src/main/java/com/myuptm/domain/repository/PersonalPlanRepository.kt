package com.myuptm.domain.repository

import com.myuptm.domain.model.AppNotification
import com.myuptm.domain.model.PersonalPlan
import kotlinx.coroutines.flow.Flow

// Sprint 7B: device-local student data. Never touches Firestore, so personal
// plans and clash warnings are visible only on the student's own device.
interface PersonalPlanRepository {

    // Live stream of the current user's personal plans, sorted by day then time.
    fun observePlans(): Flow<List<PersonalPlan>>

    suspend fun addPlan(plan: PersonalPlan)

    suspend fun updatePlan(plan: PersonalPlan)

    suspend fun removePlan(planId: String)

    // Persisted clash warnings ("class moved onto one of your plans").
    fun observeWarnings(): Flow<List<AppNotification>>

    suspend fun addWarning(warning: AppNotification)

    suspend fun clearWarnings()
}