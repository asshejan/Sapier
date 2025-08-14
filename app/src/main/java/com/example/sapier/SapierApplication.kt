package com.example.sapier

import android.app.Application
import android.util.Log
import com.example.sapier.util.ImageUtils

class SapierApplication : Application() {
    
    companion object {
        private const val TAG = "SapierApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Sapier application starting...")
        
        // Initialize any app-wide configurations here
        initializeApp()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Sapier application terminating...")
        
        // Cleanup temporary files
        ImageUtils.cleanupTempFiles(this)
    }
    
    private fun initializeApp() {
        try {
            // Any app-wide initialization can go here
            Log.d(TAG, "App initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing app", e)
        }
    }
}
