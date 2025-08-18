package com.example.sapier.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.*

class GooglePhotosService(private val context: Context) {
    companion object {
        private const val TAG = "GooglePhotosService"
        const val RC_SIGN_IN = 9001
        private const val PHOTOS_API_BASE_URL = "https://photoslibrary.googleapis.com/v1"
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/photoslibrary.readonly"))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    private var accessToken: String? = null
    private val httpClient = OkHttpClient()

    val isSignedIn: Boolean
        get() = GoogleSignIn.getLastSignedInAccount(context) != null

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(data: Intent?): Boolean {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            Log.d(TAG, "Signed in successfully with account: ${account.email}")
            
            // Check if we have the required scope
            if (GoogleSignIn.hasPermissions(account, Scope("https://www.googleapis.com/auth/photoslibrary.readonly"))) {
                Log.d(TAG, "User has granted Photos Library permission")
                // Try to get the access token
                getAccessTokenFromAccount(account)
                true
            } else {
                Log.e(TAG, "User has not granted Photos Library permission")
                false
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Sign in failed: ${e.statusCode}")
            false
        }
    }
    
    private fun getAccessTokenFromAccount(account: GoogleSignInAccount) {
        // For Google Photos API, we need to get the access token
        // This requires additional setup with Google Sign-In API
        Log.d(TAG, "Account signed in: ${account.email}")
        Log.d(TAG, "To use Google Photos API, you need to:")
        Log.d(TAG, "1. Use the OAuth Playground to get an access token")
        Log.d(TAG, "2. Or set up server-side OAuth flow")
        Log.d(TAG, "3. Or manually provide the access token")
        
        // For now, we'll use the manual approach
        // The user needs to get the access token from OAuth Playground
        Log.d(TAG, "Please get your access token from: https://developers.google.com/oauthplayground")
        Log.d(TAG, "Then use setAccessToken() method to configure it")
    }

    fun signOut() {
        googleSignInClient.signOut()
        accessToken = null
        Log.d(TAG, "Signed out from Google Photos")
    }

    fun setAccessToken(token: String) {
        accessToken = token
        Log.d(TAG, "Access token set successfully")
    }

    fun hasValidToken(): Boolean {
        return accessToken != null && accessToken!!.isNotEmpty()
    }

    suspend fun findAlbumByName(albumName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext null
                
                val request = Request.Builder()
                    .url("$PHOTOS_API_BASE_URL/albums")
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get albums: ${response.code}")
                    return@withContext null
                }

                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "")
                val albums = json.getJSONArray("albums")

                for (i in 0 until albums.length()) {
                    val album = albums.getJSONObject(i)
                    val title = album.getString("title")
                    if (title.equals(albumName, ignoreCase = true)) {
                        return@withContext album.getString("id")
                    }
                }

                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to find album: ${e.message}")
                null
            }
        }
    }

    suspend fun getImagesFromAlbum(albumId: String): List<Uri> {
        return withContext(Dispatchers.IO) {
            val imageUris = mutableListOf<Uri>()
            try {
                val token = accessToken ?: return@withContext emptyList()

                val request = Request.Builder()
                    .url("$PHOTOS_API_BASE_URL/mediaItems:search")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(
                        RequestBody.create(
                            "application/json".toMediaType(),
                            JSONObject().apply {
                                put("albumId", albumId)
                                put("pageSize", 100)
                            }.toString()
                        )
                    )
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get images from album: ${response.code}")
                    return@withContext emptyList()
                }

                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "")
                val mediaItems = json.getJSONArray("mediaItems")

                for (i in 0 until mediaItems.length()) {
                    val item = mediaItems.getJSONObject(i)
                    val mimeType = item.getString("mimeType")
                    if (mimeType.startsWith("image/")) {
                        val baseUrl = item.getString("baseUrl")
                        // Use the baseUrl with "=d" parameter to get the download URL
                        val downloadUrl = "$baseUrl=d"
                        imageUris.add(Uri.parse(downloadUrl))
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to get images from album: ${e.message}")
            }

            imageUris
        }
    }

    suspend fun findAndGetImagesFromAlbum(albumName: String): List<Uri> {
        val albumId = findAlbumByName(albumName) ?: return emptyList()
        return getImagesFromAlbum(albumId)
    }

    suspend fun getAllRecentPhotos(limit: Int = 100): List<Uri> {
        return withContext(Dispatchers.IO) {
            val imageUris = mutableListOf<Uri>()
            try {
                val token = accessToken ?: return@withContext emptyList()

                val request = Request.Builder()
                    .url("$PHOTOS_API_BASE_URL/mediaItems:search")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(
                        RequestBody.create(
                            "application/json".toMediaType(),
                            JSONObject().apply {
                                put("pageSize", limit)
                                put("filters", JSONObject().apply {
                                    put("mediaTypeFilter", JSONObject().apply {
                                        put("mediaTypes", JSONObject().apply {
                                            put("0", "PHOTO")
                                        })
                                    })
                                })
                            }.toString()
                        )
                    )
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get recent photos: ${response.code}")
                    return@withContext emptyList()
                }

                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "")
                val mediaItems = json.getJSONArray("mediaItems")

                for (i in 0 until mediaItems.length()) {
                    val item = mediaItems.getJSONObject(i)
                    val mimeType = item.getString("mimeType")
                    if (mimeType.startsWith("image/")) {
                        val baseUrl = item.getString("baseUrl")
                        // Use the baseUrl with "=d" parameter to get the download URL
                        val downloadUrl = "$baseUrl=d"
                        imageUris.add(Uri.parse(downloadUrl))
                    }
                }

                // Limit the results to the requested number
                imageUris.take(limit)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to get recent photos: ${e.message}")
            }

            imageUris
        }
    }

    fun initializeIfNeeded() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            Log.d(TAG, "Account found: ${account.email}")
        } else {
            Log.d(TAG, "No Google account found")
        }
    }
}