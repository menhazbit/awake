package com.menhazbit.awake.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.menhazbit.awake.R
import com.menhazbit.awake.manager.WakeLockManager

class WakeLockTileService : TileService() {
    
    private lateinit var wakeLockManager: WakeLockManager
    
    override fun onCreate() {
        super.onCreate()
        wakeLockManager = WakeLockManager.getInstance(this)
    }
    
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }
    
    override fun onClick() {
        super.onClick()
        toggleWakeLock()
    }
    
    private fun toggleWakeLock() {
        if (wakeLockManager.isWakeLockHeld()) {
            // Stop wake lock
            WakeLockService.stopWakeLock(this)
        } else {
            // Start wake lock
            WakeLockService.startWakeLock(this)
        }
        
        // Update tile after a short delay to reflect the new state
        android.os.Handler(mainLooper).postDelayed({
            updateTile()
        }, 100)
    }
    
    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }
    
    override fun onTileRemoved() {
        super.onTileRemoved()
        // Clean up if needed
    }
    
    private fun updateTile() {
        val tile = qsTile ?: return
        val isActive = wakeLockManager.isWakeLockHeld()
        
        tile.apply {
            state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            icon = Icon.createWithResource(this@WakeLockTileService, R.drawable.coffee_tile)
            label = getString(R.string.tile_label)
            subtitle = getString(
                if (isActive) R.string.tile_active else R.string.tile_inactive
            )
        }
        
        tile.updateTile()
    }
}