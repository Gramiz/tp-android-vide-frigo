package fr.mathieu.videfrigo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class Photos(private val context: Context) {
    private val directory get() = File(context.filesDir, "photos").apply { mkdirs() }

    // La capture temporaire est réduite hors du thread UI avant de rejoindre Room.
    suspend fun importCapture(file: File): String = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "La photo n’a pas pu être lue." }
        val sample = Integer.highestOneBit((maxOf(options.outWidth, options.outHeight) / 640).coerceAtLeast(1))
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
        val ratio = minOf(1f, 640f / maxOf(bitmap.width, bitmap.height))
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        val bytes = ByteArrayOutputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 72, it); it.toByteArray() }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        require(bytes.size <= 290_000) { "Photo trop volumineuse. Recommence avec un autre cadrage." }
        val result = File(directory, "${UUID.randomUUID()}.jpg")
        result.writeBytes(bytes)
        file.delete()
        result.path
    }

    fun encode(path: String?): String? = path?.let {
        val file = File(it)
        require(file.isFile) { "Photo locale introuvable." }
        "data:image/jpeg;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    fun decode(id: String, photo: String?): String? = photo?.let {
        require(it.startsWith("data:image/jpeg;base64,") && it.length <= 400_000)
        // Un fichier différent évite une image Compose périmée après un téléchargement.
        val file = File(directory, "$id-${UUID.randomUUID()}.jpg")
        file.writeBytes(Base64.decode(it.substringAfter(','), Base64.DEFAULT))
        file.path
    }
}
