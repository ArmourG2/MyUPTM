package com.myuptm.data.repository

import android.net.Uri
import com.myuptm.domain.repository.CloudinaryRepository
import com.myuptm.domain.repository.FileType
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class MockCloudinaryRepository : CloudinaryRepository {

    override suspend fun uploadFile(uri: Uri, fileType: FileType): Result<String> {
        // Simulate network upload delay
        delay(1500.milliseconds)

        // Return a dummy URL based on the file type
        val mockUrl = when (fileType) {
            FileType.IMAGE -> "https://res.cloudinary.com/mock/image/upload/v1/myuptm/avatar.jpg"
            FileType.PDF -> "https://res.cloudinary.com/mock/raw/upload/v1/myuptm/document.pdf"
        }

        return Result.success(mockUrl)
    }
}