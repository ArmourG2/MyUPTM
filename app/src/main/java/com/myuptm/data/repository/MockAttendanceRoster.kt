package com.myuptm.data.repository

// Sprint 9 (owner decision): rosters are NOT wiped — they are RE-BAKED here.
// Names + matric ids are REAL students from the owner's section lists
// (Section01..03_List.txt), but they have NO Firebase accounts and cannot log in.
// Exactly ONE roster entry is account-backed: AM2412018161 — AKMAL IRFAN BIN
// ABDUL MUQSITH (the owner's test account, email kl2412018161@...). It is RED
// per subject wherever a subject's base misses >= 5 (the demo class SWC3403/3713),
// and its counts are LIVE: APPROVED letters reduce misses; PENDING letters show
// as "Pending" occurrences in the attendance records until reviewed.
data class RosterStudent(
    val matric: String,
    val name: String,
    val verified: Int,
    val missed: Int
) {
    // Wireframe colour rule: 0 missed = Full Attendance; ≥5 missed = red; else yellow.
    val severity: Int get() = when {
        missed == 0 -> 0   // green — Full Attendance
        missed >= 5 -> 2   // red — high misses
        else -> 1          // yellow — moderate misses
    }
}

object MockAttendanceRoster {

    // The only account-backed demo student — the owner's test account.
    // Public so the inbox/letter flows can fall back to it when the student's
    // Firestore profile lacks a usable studentId (POC: exactly ONE real account).
    const val DEMO_MATRIC = "AM2412018161"
    private const val DEMO_NAME = "AKMAL IRFAN BIN ABDUL MUQSITH"

    // Public for the Profile statistics (one attendance row per subject/section).
    const val totalSessionsPerSubject = 15

    // Per-subject base misses for the demo student — deliberately varied (owner
    // request). The demo class SWC3403/3713 is the red-marked (>=5) one. Letters
    // peel misses off this map (head first → SWC), so the roster badge, the
    // warning gate and the Profile statistics all stay consistent.
    val DEMO_BASE_MISS_BY_SUBJECT: Map<String, Int> = linkedMapOf(
        "SWC3403/3713" to 6,
        "MAT2113" to 3,
        "NWC24132" to 2,
        "MPU2432" to 1,
        "FYP3024" to 0
    )

    // Position-based missed counts: deterministic across sessions, mixing green/yellow
    // and giving every section a few RED students so batch warning is demonstrable.
    private val MISSED_PATTERN = listOf(0, 3, 1, 0, 7, 2, 4, 6, 0, 2, 5, 1, 0, 3, 2, 0, 4, 1, 5, 2, 0)

    // Sprint 9: attendance numbers are LIVE for the demo student — an APPROVED
    // absence letter removes one missed class. PENDING letters no longer change
    // the count; they mark the occurrence as "Pending" in the attendance records
    // until the lecturer decides (see [sessionStatuses]).
    fun studentsFor(section: String, subjectName: String, approvedLetters: Int): List<RosterStudent> =
        build(sectionRoster(section), subjectName, approvedLetters)

    private fun build(
        roster: List<Pair<String, String>>,
        subjectName: String,
        approvedLetters: Int
    ): List<RosterStudent> {
        val rows = roster.mapIndexed { index, (name, matric) ->
            if (matric == DEMO_MATRIC) demoRoster(subjectName, approvedLetters, name)
            else {
                val missed = MISSED_PATTERN[index % MISSED_PATTERN.size]
                RosterStudent(matric, name, totalSessionsPerSubject - missed, missed)
            }
        }
        // Sections without the demo student get one appended so it is always reviewable.
        return if (rows.any { it.matric == DEMO_MATRIC }) rows else rows + demoRoster(
            subjectName, approvedLetters, DEMO_NAME
        )
    }

    private fun demoRoster(subjectName: String, approvedLetters: Int, name: String): RosterStudent {
        val baseMissed = DEMO_BASE_MISS_BY_SUBJECT[subjectName] ?: DEMO_BASE_MISS_BY_SUBJECT.values.first()
        val missed = (baseMissed - approvedLetters).coerceAtLeast(0)
        return RosterStudent(DEMO_MATRIC, name, totalSessionsPerSubject - missed, missed)
    }

    // Sprint 9: the three-way session status every attendance view shows —
    // approved letters excuse the most recent occurrences, pending letters mark
    // the NEXT ones as submitted-but-unreviewed, the rest stay missed.
    enum class SessionStatus { ATTEND, PENDING, MISSED }

    fun sessionStatuses(baseMissed: Int, approved: Int, pending: Int): List<SessionStatus> {
        val a = approved.coerceAtMost(baseMissed)
        val p = pending.coerceAtMost((baseMissed - a).coerceAtLeast(0))
        return List(totalSessionsPerSubject) { i ->
            when {
                i < a -> SessionStatus.ATTEND
                i < a + p -> SessionStatus.PENDING
                i < baseMissed -> SessionStatus.MISSED
                else -> SessionStatus.ATTEND
            }
        }
    }

    // Sprint 9: deterministic attendance RECORD dates for the lecturer's drill-in
    // (wireframe "View Attendance"). Generates the past `totalSessionsPerSubject`
    // occurrences of the class's weekday, most recent first. The LIVE missed count
    // marks that-many TOP rows "Missed"; the rest "Attend" — submitting an excuse
    // letter flips a row green consistently with the roster badge + Profile stats.
    // (POC seam: no real attendance recording yet — same documented family.)
    fun recordSlots(classSession: com.myuptm.domain.model.ClassSession): List<java.time.LocalDate> {
        val today = java.time.LocalDate.now()
        val dates = ArrayList<java.time.LocalDate>(totalSessionsPerSubject)
        var cursor = today
        while (dates.size < totalSessionsPerSubject) {
            if (cursor.dayOfWeek.value - 1 == classSession.dayIndex) dates.add(cursor)
            cursor = cursor.minusDays(1)
        }
        return dates
    }

    // SECTION 1 — 21 students (owner-provided list).
    private val SECTION_1 = listOf(
        "ABI IRFAN BIN ABI ALI" to "AM2412018174",
        "AHMAD LUQMAN BIN MOHD FAUZI" to "AM2412018107",
        "AHMAD QHAIRYZUDDIN ADHA BIN AHMAD NAZRI" to "AM2412018189",
        "AHMED TAQIF JAMALUDDIN" to "AM2412018179",
        "AISYAH SYAFIAH BINTI SHAHARUDIN" to "AM2412018207",
        "AKMAL IRFAN BIN ABDUL MUQSITH" to DEMO_MATRIC,
        "BADRUL AIMAN BIN BAHTIAR" to "AM2412018207",
        "HASNIZATUL NATASYA BINTI ABDULLAH" to "AM2412017273",
        "JUNIOR JANN JAQIM JANGKING" to "AM2412018117",
        "KYRA MELATI NASIR" to "AM2412018110",
        "LAILATUL QALISHA BINTI ABDUL MUTALIB" to "AM2412017652",
        "MOHAMAD THAQIF BIN MOHAMAD ISMADI" to "AM2412018129",
        "MUHAMMAD AIMAN DANIAL BIN ROSLI" to "AM2412018184",
        "MUHAMMAD AKMAL HARIZ BIN RAZALI" to "AM2412018313",
        "MUHAMMAD ALIFF NADJMI BIN MOHD KASWADI" to "AM2412018092",
        "MUHAMMAD FAISAL BIN HAMEDI" to "AM2412018197",
        "MUHAMMAD ILMAN AIDIL BIN MASHURI" to "AM2412018196",
        "MUHAMMAD RIDUAN BIN JAFRI" to "AM2412018130",
        "NUR AISYAH NABILAH BINTI KHAIRI" to "AM2412017884",
        "NUR EINSYIRAH BINTI AHMAD" to "AM2412018151",
        "MUHAMMAD HAZIQ BIN MOHD JAHARI" to "AM2412017932"
    )

    // SECTION 2 — 20 students (owner-provided list).
    private val SECTION_2 = listOf(
        "ADRIANA KHAYRIN BINTI MOHD SIDEK" to "AM2412018306",
        "ASHRAF BIN AZNIJAR" to "AM2412018291",
        "IRDINA BATRISYA BINTI SUHAINI" to "AM2412018312",
        "IRMA HASNIDA BINTI AZHAR" to "AM2412018325",
        "LUQMAN HAKEEM SYAH BIN MOHAMAD NAZRIN" to "AM2412018256",
        "MUHAMMAD AFFANDI BIN SHARAN" to "AM2412018226",
        "MUHAMMAD DENISH ZIKRY BIN MOHD HISHAM" to "AM2412018276",
        "MUHAMMAD FAUZAN BIN DWI INDRATNO" to "AM2412018282",
        "MUHAMMAD SYAZWI DANIAL BIN SAIFULLIZAN" to "AM2412018264",
        "NUR AINA BATRISYA BINTI ZULFIKAR" to "AM2412018235",
        "NUR AINA SYAZIRA BINTI AHMAD HILMI" to "AM2412018317",
        "NUR BATRISYIA ATHIRAH BINTI MOHD FADZHIL" to "AM2412018326",
        "NUR SHUHADAH BINTI HASREZAM" to "AM2412018316",
        "NURSYAFIQAH ZULAIKHA BINTI ZILKAMAL" to "AM2412018113",
        "NURUN BAHIIYAH BINTI MOHAMAD HAJAZI" to "AM2412018158",
        "PUTRI SARAH BINTI MEGAT ZARIMAN" to "AM2412018123",
        "QISTINA BATRISYIA BINTI MOHD NAJIB" to "AM2412018077",
        "SAIDATUL NAZIRAH BINTI RAZAK" to "AM2412018163",
        "SAIRUL ALI BIN SAIFUL" to "AM2412018183",
        "SHARIFAH HAZWANI BINTI SYED MOHD HASBI" to "AM2412018167"
    )

    // SECTION 3 — 19 students (owner-provided list).
    private val SECTION_3 = listOf(
        "AHMAD HUMAIDI BIN PISAL" to "AM2412018331",
        "AKMAL ADIB ASYRAF BIN SHAHROM" to "AM2412018336",
        "ARYFF SULEIMAN BIN MOHAMMAD FAHMY" to "AM2412018335",
        "AZRI AMIRUL BIN MOHD ROZAINI" to "AM2412018327",
        "DAYANG DAMIA HEIDI BINTI ROSLEE" to "AM2412018395",
        "MUHAMMAD AMIRUL MUKMIN BIN SHARUN NIZAM" to "AM2412018217",
        "Muhammad Anaqi Qawy bin Ahmad Hazry" to "AM2412018293",
        "MUHAMMAD AQIL AMIRUDIN BIN HIRMASI" to "AM2412018227",
        "MUHAMMAD BIN AIDY ALDAHAR" to "AM2412018223",
        "MUHAMMAD IMRAN ZAIM BIN MOHD ZARONIZAM" to "AM2412018214",
        "NUR ALEYA IZZYANI BINTI AHMAD NUBLI" to "AM2412018251",
        "NUR ANIQAH ARDINI BINTI MOHD RIDZUAN" to "AM2412018301",
        "NUR 'AWADAH BINTI MUHAMMAD AZWA" to "AM2412018247",
        "NUR BATRISYA QAMELIA BINTI MOHD KAMIL" to "AM2412018302",
        "NUR FARZANA BINTI ZULKIFLI" to "AM2412018279",
        "NUR HUSNA AMIRAH BINTI MOHD KHIDZIR" to "AM2412018265",
        "NURADLINA DAMIA BINTI MOHD SAFELY" to "AM2412018254",
        "RAJA NURUL WARDAH BINTI RAJA OTHMAN" to "AM2412018305",
        "SITI NIURSAHIDA BINTI HANDA" to "AM2412018292"
    )

    private fun sectionRoster(section: String): List<Pair<String, String>> =
        when (Regex("\\d+").findAll(section).lastOrNull()?.value?.toIntOrNull()) {
            2 -> SECTION_2
            3 -> SECTION_3
            else -> SECTION_1 // blank/unknown sections fall back to Section 1
        }
}
