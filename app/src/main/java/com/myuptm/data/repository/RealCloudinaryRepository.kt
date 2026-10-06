package com.myuptm.data.repository

import android.content.Context
import android.net.Uri
import com.myuptm.BuildConfig
import com.myuptm.domain.repository.CloudinaryRepository
import com.myuptm.domain.repository.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Sprint 8 Task 3: real Cloudinary upload — POSTs the file as multipart form data to an
// UNSIGNED upload preset (no API secret ever ships in the APK). Failures surface the
// server's own error message so the user knows exactly why an action failed.
class RealCloudinaryRepository(private val context: Context) : CloudinaryRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun uploadFile(uri: Uri, fileType: FileType): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
                val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET
                if (cloudName.isBlank() || uploadPreset.isBlank()) {
                    error("Upload not configured: add cloudinaryCloudName and cloudinaryUploadPreset to local.properties")
                }

                val resolver = context.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Cannot read the selected file")
                val mime = resolver.getType(uri)
                    ?: when (fileType) {
                        FileType.IMAGE -> "image/jpeg"
                        FileType.PDF -> "application/pdf"
                    }
                val resourceType = if (fileType == FileType.IMAGE) "image" else "raw"
                val extension = when (val subtype = mime.substringAfter('/')) {
                    "jpeg" -> "jpg"
                    "plain" -> "txt"
                    else -> subtype
                }
                val fileName = "upload_${System.currentTimeMillis()}.$extension"

                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", uploadPreset)
                    .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaTypeOrNull()))
                    .build()

                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/$cloudName/$resourceType/upload")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        // Cloudinary wraps failures as {"error":{"message":"..."}}.
                        val serverMessage = runCatching {
                            JSONObject(text).getJSONObject("error").getString("message")
                        }.getOrNull()
                        error(serverMessage ?: "Upload failed (HTTP ${response.code})")
                    }
                    val secureUrl = JSONObject(text).optString("secure_url")
                    if (secureUrl.isBlank()) error("Upload succeeded but no URL was returned")
                    secureUrl
                }
            }
        }
}
