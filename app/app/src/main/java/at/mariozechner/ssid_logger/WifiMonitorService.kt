package at.mariozechner.ssid_logger

import android.app.Service
import android.content.Intent
import android.os.IBinder

class WifiMonitorService : Service() {
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Stub implementation for now
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
}