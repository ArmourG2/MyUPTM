package com.myuptm.domain.repository

import com.myuptm.domain.model.AbsenceLetter
import com.myuptm.domain.model.LetterStatus
import kotlinx.coroutines.flow.Flow

// Sprint 8 Task 4 (part 2): students submit absence letters; lecturers review them
// (approve / decline with reason) inside Class Management.
interface AbsenceLetterRepository {
    // Live feed of ALL letters (lecturer view filters by student; student filters own).
    fun observeLetters(): Flow<List<AbsenceLetter>>

    suspend fun submitLetter(letter: AbsenceLetter): Result<Unit>

    // Approve / decline a letter; declineReason is required for DECLINED.
    suspend fun setLetterStatus(letterId: String, status: LetterStatus, declineReason: String?): Result<Unit>
}
