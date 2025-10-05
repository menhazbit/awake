package com.menhazbit.awake.manager

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

class WakeLockManager private constructor(private val context: Context) {
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val prefs: SharedPreferences = context.getSharedPreferences("wake_lock_prefs", Context.MODE_PRIVATE)
    private var partialWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var originalScreenTimeout: Int = -1
    
    companion object {
        private const val TAG = "WakeLockManager"
        private const val PARTIAL_WAKE_LOCK_TAG = "Awake::PartialWakeLock"
        private const val SCREEN_WAKE_LOCK_TAG = "Awake::ScreenWakeLock"
        private const val PREF_WAKE_LOCK_ACTIVE = "wake_lock_active"
        private const val PREF_ORIGINAL_TIMEOUT = "original_screen_timeout"
        private const val NEVER_TIMEOUT = Integer.MAX_VALUE
        
        @Volatile
        private var INSTANCE: WakeLockManager? = null
        
        fun getInstance(context: Context): WakeLockManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WakeLockManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    fun acquireWakeLock() {
        try {
            if (!isWakeLockHeld()) {
                Log.d(TAG, "Acquiring system-wide wake lock")
                
                // Step 1: Acquire partial wake lock to keep CPU alive
                acquirePartialWakeLock()
                
                // Step 2: Acquire screen wake lock for additional screen control
                acquireScreenWakeLock()
                
                // Step 3: Modify system screen timeout to never (most important step)
                modifyScreenTimeout()
                
                // Step 4: Mark as active in preferences
                prefs.edit().putBoolean(PREF_WAKE_LOCK_ACTIVE, true).apply()
                
                Log.d(TAG, "System-wide wake lock activated successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire system-wide wake lock", e)
        }
    }
    
    private fun acquirePartialWakeLock() {
        try {
            if (partialWakeLock?.isHeld != true) {
                partialWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    PARTIAL_WAKE_LOCK_TAG
                ).apply {
                    acquire()
                }
                Log.d(TAG, "Partial wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire partial wake lock", e)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun acquireScreenWakeLock() {
        try {
            if (screenWakeLock?.isHeld != true) {
                screenWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    SCREEN_WAKE_LOCK_TAG
                ).apply {
                    acquire()
                }
                Log.d(TAG, "Screen wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire screen wake lock", e)
        }
    }
    
    private fun modifyScreenTimeout() {
        try {
            if (Settings.System.canWrite(context)) {
                // Always read and save the current timeout before modifying
                val currentTimeout = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT
                )
                
                // Only save if we haven't saved one yet, or if this is different from what we saved
                val existingSaved = prefs.getInt(PREF_ORIGINAL_TIMEOUT, -1)
                if (existingSaved == -1 || existingSaved == NEVER_TIMEOUT) {
                    originalScreenTimeout = currentTimeout
                    prefs.edit().putInt(PREF_ORIGINAL_TIMEOUT, originalScreenTimeout).apply()
                    Log.d(TAG, "Original screen timeout captured: ${originalScreenTimeout}ms")
                } else {
                    originalScreenTimeout = existingSaved
                    Log.d(TAG, "Using previously saved timeout: ${originalScreenTimeout}ms")
                }
                
                // Set screen timeout to maximum value (essentially never)
                val success = Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    NEVER_TIMEOUT
                )
                
                if (success) {
                    Log.d(TAG, "Screen timeout set to never")
                } else {
                    Log.e(TAG, "Failed to modify screen timeout")
                }
            } else {
                Log.w(TAG, "No WRITE_SETTINGS permission, cannot modify screen timeout")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error modifying screen timeout", e)
        }
    }
    
    fun releaseWakeLock() {
        try {
            Log.d(TAG, "Releasing system-wide wake lock")
            
            // Step 1: Restore original screen timeout
            restoreScreenTimeout()
            
            // Step 2: Release screen wake lock
            releaseScreenWakeLock()
            
            // Step 3: Release partial wake lock
            releasePartialWakeLock()
            
            // Step 4: Clear preferences
            prefs.edit()
                .putBoolean(PREF_WAKE_LOCK_ACTIVE, false)
                .remove(PREF_ORIGINAL_TIMEOUT)
                .apply()
            
            originalScreenTimeout = -1
            
            Log.d(TAG, "System-wide wake lock released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }
    
    private fun restoreScreenTimeout() {
        try {
            if (Settings.System.canWrite(context)) {
                val savedTimeout = prefs.getInt(PREF_ORIGINAL_TIMEOUT, -1)
                
                if (savedTimeout == -1) {
                    Log.w(TAG, "No original timeout saved, cannot restore")
                    return
                }
                
                val success = Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    savedTimeout
                )
                
                if (success) {
                    Log.d(TAG, "Screen timeout restored to: ${savedTimeout}ms")
                } else {
                    Log.e(TAG, "Failed to restore screen timeout")
                }
            } else {
                Log.w(TAG, "No WRITE_SETTINGS permission, cannot restore screen timeout")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring screen timeout", e)
        }
    }
    
    private fun releasePartialWakeLock() {
        try {
            partialWakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Log.d(TAG, "Partial wake lock released")
                }
            }
            partialWakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing partial wake lock", e)
        }
    }
    
    private fun releaseScreenWakeLock() {
        try {
            screenWakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Log.d(TAG, "Screen wake lock released")
                }
            }
            screenWakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing screen wake lock", e)
        }
    }
    
    fun isWakeLockHeld(): Boolean {
        val partialHeld = partialWakeLock?.isHeld == true
        val screenHeld = screenWakeLock?.isHeld == true
        val isPersisted = prefs.getBoolean(PREF_WAKE_LOCK_ACTIVE, false)
        val hasModifiedTimeout = checkIfTimeoutIsModified()
        
        // Consider active if wake locks are held AND timeout is modified, OR if persisted state says it's active
        return (partialHeld && screenHeld && hasModifiedTimeout) || isPersisted
    }
    
    private fun checkIfTimeoutIsModified(): Boolean {
        return try {
            val currentTimeout = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                30000
            )
            // Check if timeout is set to our "never" value or very high value
            currentTimeout >= 2147483647 || currentTimeout > 1000000 // More than ~16 minutes is likely our setting
        } catch (e: Exception) {
            false
        }
    }
    
    fun isSystemWriteSettingsPermissionGranted(): Boolean {
        return Settings.System.canWrite(context)
    }
    
    fun getCurrentScreenTimeout(): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT
            )
        } catch (e: Exception) {
            Log.e(TAG, "Critical: Failed to read screen timeout", e)
            // This should rarely happen - return 30s as Android's common default
            30000
        }
    }
    
    fun getOriginalScreenTimeout(): Int {
        val saved = prefs.getInt(PREF_ORIGINAL_TIMEOUT, -1)
        return if (saved != -1) saved else {
            Log.w(TAG, "No original timeout saved, reading current")
            getCurrentScreenTimeout()
        }
    }
    
    fun cleanup() {
        Log.d(TAG, "Cleaning up wake lock manager")
        releaseWakeLock()
    }
    
    fun forceRestoreDefaults() {
        try {
            if (Settings.System.canWrite(context)) {
                // Restore to a reasonable default (30 seconds)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    30000
                )
                Log.d(TAG, "Force restored screen timeout to 30 seconds")
            }
            
            // Release any wake locks
            releasePartialWakeLock()
            releaseScreenWakeLock()
            
            // Clear preferences
            prefs.edit().clear().apply()
            originalScreenTimeout = -1
            
        } catch (e: Exception) {
            Log.e(TAG, "Error force restoring defaults", e)
        }
    }
}