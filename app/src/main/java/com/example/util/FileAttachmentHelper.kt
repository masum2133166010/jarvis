package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.data.repository.PendingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

object FileAttachmentHelper {

    suspend fun loadImageAttachment(context: Context, uri: Uri): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val name = queryFileName(context, uri) ?: "image.jpg"
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unable to read selected image")

                val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: throw IllegalStateException("Unsupported image format")

                val maxDim = 1024
                val scale = if (max(bitmap.width, bitmap.height) > maxDim) {
                    maxDim.toFloat() / max(bitmap.width, bitmap.height).toFloat()
                } else {
                    1f
                }
                val scaled = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    bitmap
                }

                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

                PendingAttachment(
                    name = name,
                    type = "image",
                    uriString = uri.toString(),
                    mimeType = "image/jpeg",
                    imageBase64 = b64
                )
            }
        }

    suspend fun loadTextAttachment(context: Context, uri: Uri): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val name = queryFileName(context, uri) ?: "document.txt"
                val mime = resolver.getType(uri) ?: "text/plain"
                val text = resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val content = reader.readText()
                    if (content.length > 60_000) {
                        content.take(60_000) + "\n\n[Truncated at 60,000 characters]"
                    } else {
                        content
                    }
                } ?: throw IllegalStateException("Unable to read document")

                if (text.isBlank()) {
                    throw IllegalStateException("Selected document is empty")
                }

                PendingAttachment(
                    name = name,
                    type = "text",
                    uriString = uri.toString(),
                    mimeType = mime,
                    textContent = text
                )
            }
        }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }
}
