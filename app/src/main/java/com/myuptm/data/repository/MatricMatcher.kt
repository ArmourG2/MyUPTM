package com.myuptm.data.repository

// Sprint 9: matric matching for the warning-letter / absence-letter chain.
// The owner's test student account (AKMAL IRFAN, matric AM2412018161) signs in with
// the UPTM email kl2412018161@... — same numeric id, different prefix/format.
// Matching is exact-ignoring-case first, then falls back to comparing the DIGIT run,
// so prefix differences never break the demo (e.g. "kl2412018161" ≈ "AM2412018161").
// NOTE: digit-run matching is a POC convenience — full string equality wins first.
object MatricMatcher {

    fun matches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a.equals(b, ignoreCase = true)) return true
        val digitsA = a.filter { it.isDigit() }
        val digitsB = b.filter { it.isDigit() }
        val run = minOf(digitsA.length, digitsB.length)
        // Ignore tiny overlaps ("1" vs "11") — only a meaningful digit run counts.
        if (run < 6) return false
        return digitsA.takeLast(run) == digitsB.takeLast(run)
    }
}

// String-oriented convenience for UI-side filters.
fun String?.matchesMatric(other: String?): Boolean = MatricMatcher.matches(this, other)
