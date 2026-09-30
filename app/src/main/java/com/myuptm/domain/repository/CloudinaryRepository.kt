package com.myuptm.domain.repository

import android.net.Uri

enum class FileType {
    IMAGE,
    PDF
}

interface CloudinaryRepository {
    /**
     * Uploads a file and returns the resulting URL.
     */
    suspend fun uploadFile(uri: Uri, fileType: FileType): Result<String>
}