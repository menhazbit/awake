package com.menhazbit.awake.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.menhazbit.awake.MainActivity
import com.menhazbit.awake.R
import com.menhazbit.awake.manager.WakeLockManager

class WakeLockService : Service() {
    
    private lateinit var wakeLockManager: WakeLockManager
    
    companion object {
        private const val TAG = "WakeLockService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "wake_lock_channel"
        const val ACTION_START_WAKE_LOCK = "START_WAKE_LOCK"
        const val ACTION_STOP_WAKE_LOCK = "STOP_WAKE_LOCK"
        
        fun startWakeLock(context: Context) {
            val intent = Intent(context, WakeLockService::class.java).apply {
                action = ACTION_START_WAKE_LOCK
            }
            context.startForegroundService(intent)
        }
        
        fun stopWakeLock(context: Context) {
            val intent = Intent(context, WakeLockService::class.java).apply {
                action = ACTION_STOP_WAKE_LOCK
            }
            context.startService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        wakeLockManager = WakeLockManager.getInstance(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WAKE_LOCK -> {
                Log.d(TAG, "Starting wake lock service with screen timeout modification")
                wakeLockManager.acquireWakeLock()
                startForeground(NOTIFICATION_ID, createNotification())
                
                // Verify that screen timeout was actually modified
                android.os.Handler(mainLooper).postDelayed({
                    if (!wakeLockManager.isWakeLockHeld()) {
                        Log.w(TAG, "Wake lock verification failed, retrying...")
                        wakeLockManager.acquireWakeLock()
                    } else {
                        Log.d(TAG, "Wake lock verification successful")
                    }
                }, 2000)
            }
            ACTION_STOP_WAKE_LOCK -> {
                Log.d(TAG, "Stopping wake lock service and restoring screen timeout")
                wakeLockManager.releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // If service is restarted without explicit action, restore wake lock if it was active
                if (wakeLockManager.isWakeLockHeld()) {
                    Log.d(TAG, "Service restarted - restoring wake lock and screen timeout")
                    wakeLockManager.acquireWakeLock()
                    startForeground(NOTIFICATION_ID, createNotification())
                } else {
                    Log.d(TAG, "Service restarted - no active wake lock to restore")
                }
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed - cleaning up wake locks")
        wakeLockManager.cleanup()
        super.onDestroy()
    }
    
    override fun onLowMemory() {
        Log.d(TAG, "Low memory - maintaining wake lock")
        // Don't release wake lock on low memory
        super.onLowMemory()
    }
    
    override fun onTrimMemory(level: Int) {
        Log.d(TAG, "Trim memory level: $level - maintaining wake lock")
        // Don't release wake lock on memory pressure
        super.onTrimMemory(level)
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep service running even when app is removed from recent tasks
        Log.d(TAG, "Task removed - service continues running")
        super.onTaskRemoved(rootIntent)
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            // Prevent notification sound/vibration
            setSound(null, null)
            enableVibration(false)
        }
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, WakeLockService::class.java).apply {
            action = ACTION_STOP_WAKE_LOCK
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Screen timeout disabled - tap to view details")
            .setSmallIcon(R.drawable.coffee)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.coffee,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setSound(null)
            .setVibrate(null)
            .setAutoCancel(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}