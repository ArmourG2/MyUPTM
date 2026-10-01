package com.myuptm.data.repository

import com.myuptm.domain.model.Post
import com.myuptm.domain.repository.PostsRepository

class MockPostsRepository : PostsRepository {

    // Companion-backed so removals are visible to every ViewModel sharing this mock
    // (PostsViewModel, HomeViewModel announcements) within the same process.
    private companion object {
        val posts = mutableListOf(
            Post("1", "Mid-Semester Exam Timetable Released", "The mid-semester examination timetable for Semester 1 2026/2027 is now available. Check your schedule and venue.", "25 Sep 2026", "Academic Affairs"),
            Post("2", "Faculty of Computing Career Fair", "Annual career fair featuring 30+ tech companies. Bring your CV and dress professionally.", "24 Sep 2026", "Faculty of Computing"),
            Post("3", "Library Extended Hours During Exam Week", "Main library operates 24 hours from 1 Oct to 7 Oct to support exam preparation.", "23 Sep 2026", "Library Services"),
            Post("4", "Mobile App Development Workshop", "Hands-on workshop covering Jetpack Compose and Material 3. Open to Year 2 and 3 students.", "22 Sep 2026", "Faculty of Computing"),
            Post("5", "Campus Wi-Fi Maintenance Notice", "Scheduled maintenance Saturday 28 Sep, 2:00 AM to 6:00 AM. Expect intermittent connectivity.", "21 Sep 2026", "IT Services")
        )
    }

    override fun getPosts(): List<Post> = posts.toList()

    override fun removePost(postId: String): Boolean = posts.removeAll { it.id == postId }
}