package com.myuptm.domain.repository

import com.myuptm.domain.model.ClassSession
import kotlinx.coroutines.flow.Flow

// Sprint 7B: global class data. Lecturers modify their OWN classes; every device
// observes the same source so edits propagate to all students.
interface ClassRepository {

    // Live stream of the global class list, sorted by day then start time.
    fun observeClasses(): Flow<List<ClassSession>>

    // Creates a new global class. The implementation stamps ownerEmail so only
    // the creating lecturer may edit it later.
    suspend fun addClass(session: ClassSession): Result<Unit>

    // Updates an existing global class (session.id is the document id).
    // Fails if the current user is not allowed to modify it.
    suspend fun updateClass(session: ClassSession): Result<Unit>

    // Deletes a global class by document id.
    // Fails if the current user is not allowed to modify it.
    suspend fun removeClass(classId: String): Result<Unit>
}