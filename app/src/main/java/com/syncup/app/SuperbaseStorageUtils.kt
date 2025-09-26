package com.syncup.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.UUID

class SuperbaseStorageUtils(val context: Context) {

    val superbase = createSupabaseClient(
        supabaseUrl = "https://wrhufcycvlppcvnbfehb.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndyaHVmY3ljdmxwcGN2bmJmZWhiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTc4NTAzNDksImV4cCI6MjA3MzQyNjM0OX0.1K3-03FFnk_vnD4I8baBiiwchQCvX3jLoGX-EsmNmGE"
    ) {
        install(Storage)
    }

    // Is function ko hum nahi badlenge, taaki profile pictures delete na hon.
    suspend fun uploadProfilePicture(uri: Uri): String? {
        return try {
            val fileName = "image_${UUID.randomUUID()}.jpg"
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val compressedImageData = outputStream.toByteArray()
            superbase.storage.from(BUCKET_NAME_PROFILE).upload(fileName, compressedImageData)
            superbase.storage.from(BUCKET_NAME_PROFILE).publicUrl(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun uploadChatMedia(uri: Uri, mediaType: String): String? {
        val bucket = superbase.storage.from(BUCKET_NAME_MEDIA)
        return try {
            val fileExtension = if (mediaType == "image") "jpg" else "mp4"
            // **CHANGE**: Filename mein timestamp add kiya gaya hai
            val timestamp = System.currentTimeMillis()
            val fileName = "${mediaType}_${timestamp}_${UUID.randomUUID()}.$fileExtension"
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileBytes = inputStream?.readBytes()
            inputStream?.close()

            if (fileBytes != null) {
                if (mediaType == "image") {
                    val bitmap = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size)
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 65, outputStream)
                    val compressedData = outputStream.toByteArray()
                    bucket.upload(fileName, compressedData)
                } else {
                    bucket.upload(fileName, fileBytes)
                }
                bucket.publicUrl(fileName)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun uploadThumbnail(bitmap: Bitmap): String? {
        return try {
            // **CHANGE**: Filename mein timestamp add kiya gaya hai
            val timestamp = System.currentTimeMillis()
            val fileName = "thumb_${timestamp}_${UUID.randomUUID()}.jpg"
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val compressedData = outputStream.toByteArray()

            superbase.storage.from(BUCKET_NAME_MEDIA).upload(fileName, compressedData)
            superbase.storage.from(BUCKET_NAME_MEDIA).publicUrl(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveMediaToGallery(mediaUrl: String, mediaType: String, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(mediaUrl)
                val connection = url.openConnection()
                connection.connect()
                val inputStream = connection.getInputStream()

                val fileName = "${mediaType}_${System.currentTimeMillis()}"
                val mimeType = if (mediaType == "image") "image/jpeg" else "video/mp4"
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (mediaType == "image") MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    if (mediaType == "image") MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SyncUp")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(collection, contentValues)

                uri?.let {
                    val outputStream = resolver.openOutputStream(it)
                    outputStream?.use { stream -> inputStream.copyTo(stream) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(it, contentValues, null, null)
                    }
                }
                inputStream.close()
                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }


    suspend fun deleteImage(imageUrl: String) {
        try {
            val imageUri = Uri.parse(imageUrl)
            val pathSegments = imageUri.pathSegments
            val bucketNameIndex = pathSegments.indexOf(BUCKET_NAME_PROFILE)

            if (bucketNameIndex != -1 && bucketNameIndex + 1 < pathSegments.size) {
                val filePath = pathSegments.subList(bucketNameIndex + 1, pathSegments.size).joinToString("/")
                if (filePath.isNotBlank()) {
                    superbase.storage.from(BUCKET_NAME_PROFILE).delete(listOf(filePath))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    companion object {
        const val BUCKET_NAME_PROFILE = "syncup_images" // Profile pictures (safe)
        const val BUCKET_NAME_MEDIA = "syncup_media"   // Chat media (will be auto-deleted)
    }
}

