package com.myuptm.domain.model

data class SupportLink(
    val id: String,
    val title: String,
    val url: String
)

object SupportLinks {
    const val PORTAL_URL = "https://www.uptm.edu.my/index.php/students/student-portal"

    val ITEMS = listOf(
        SupportLink("lms", "LMS", "https://lms.uptm.edu.my/0526/login/index.php"),
        SupportLink("cms", "CMS", "https://mycms.kptm.edu.my:8000/"),
        SupportLink("calendar", "Calendar", "https://www.uptm.edu.my/index.php/students/academic-calendar"),
        SupportLink("epay", "E-Pay", "https://epay.kptm.edu.my/"),
        SupportLink("id", "Digital ID", "https://digitalid.kptm.edu.my:8443/")
    )
}