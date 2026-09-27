package com.myuptm.navigation

object MyUptmRoutes {
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val ATTENDANCE = "attendance"
    const val POSTS = "posts"
    const val PROFILE = "profile"
    const val SIGN_IN = "sign_in"
    const val SETTINGS = "settings"
    const val POST_DETAIL = "post_detail/{postId}"

    // Helper: substitutes the real id into the pattern
    fun postDetail(postId: String) = "post_detail/$postId"
}