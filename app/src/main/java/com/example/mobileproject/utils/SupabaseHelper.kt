package com.example.mobileproject.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import java.io.InputStream

object SupabaseHelper {

    private const val SUPABASE_URL = "https://bssigmqrtsddhcwsajxl.supabase.co"
    private const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJzc2lnbXFydHNkZGhjd3NhanhsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjE1MDE4MTMsImV4cCI6MjA3NzA3NzgxM30.cbO2rqfkoVloOu5-47Qo4QLWc7V4fTWB_AtAcx2Jir4"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }

    fun logConnection() {
        Log.d(
            "Supabase",
            "✅ Supabase client initialized. Session status: ${client.auth.sessionStatus.value}"
        )
    }

    /** Upload public (legacy support) */
    fun uploadFile(
        bucket: String,
        fileName: String,
        fileUri: Uri,
        context: Context,
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val storage = client.storage.from(bucket)
                val inputStream = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        onFailure(IllegalStateException("Unable to open file input stream"))
                    }
                    return@launch
                }

                val bytes = inputStream.readBytes()
                inputStream.close()

                storage.upload(path = fileName, data = bytes, upsert = true)
                val publicUrl = storage.publicUrl(fileName)
                Log.d("Supabase", "✅ Uploaded file (public): $publicUrl")

                withContext(Dispatchers.Main) {
                    onSuccess(publicUrl)
                }
            } catch (e: Exception) {
                Log.e("Supabase", "❌ Upload failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onFailure(e)
                }
            }
        }
    }

    /** Upload private (return path only) */
    fun uploadFileReturnPath(
        bucket: String,
        filePath: String,
        fileUri: Uri,
        context: Context,
        onSuccessPath: (String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val storage = client.storage.from(bucket)
                val inputStream = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    Log.e("Supabase", "⚠️ InputStream is null for URI: $fileUri")
                    withContext(Dispatchers.Main) {
                        onFailure(IllegalStateException("Cannot open file — invalid URI or permission"))
                    }
                    return@launch
                }

                val bytes = inputStream.readBytes()
                inputStream.close()

                storage.upload(path = filePath, data = bytes, upsert = true)
                Log.d("Supabase", "✅ Uploaded file (path only): $filePath")

                withContext(Dispatchers.Main) {
                    onSuccessPath(filePath)
                }
            } catch (e: Exception) {
                Log.e("Supabase", "❌ Upload failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onFailure(e)
                }
            }
        }
    }

    /** Create short-lived signed URL (1 hour default) */
    suspend fun getSignedUrl(
        bucket: String,
        path: String,
        expiresInSeconds: Long = 3600
    ): String {
        return withContext(Dispatchers.IO) {
            val storage = client.storage.from(bucket)
            storage.createSignedUrl(path, expiresInSeconds.seconds)
        }
    }

    /** 🗑️ Delete file from Supabase Storage */
    suspend fun deleteFile(bucket: String, path: String) {
        withContext(Dispatchers.IO) {
            val storage = client.storage.from(bucket)
            storage.delete(path)
            Log.d("Supabase", "🗑️ Deleted file from Supabase: $path")
        }
    }
}
