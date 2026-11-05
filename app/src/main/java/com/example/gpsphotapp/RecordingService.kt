package com.example.gpsphotapp

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground Service che mantiene viva l'auto-recording quando l'app è in
 * background. Mostra la notifica persistente.
 * Su iOS la sessione di cattura sopravvive grazie a UIBackgroundModes; su
 * Android il pattern equivalente è un Foreground Service.
 */
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == NotificationHelper.ACTION_STOP) {
            // Segnala al ViewModel di fermare l'auto-recording.
            RecordingBridge.requestStop()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val photoCount = intent?.getIntExtra(EXTRA_PHOTO_COUNT, 0) ?: 0
        val notif = NotificationHelper.buildRecordingNotification(this, photoCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notif)
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    companion object {
        const val EXTRA_PHOTO_COUNT = "photo_count"
    }
}

/**
 * Singleton bridge utilizzato dalla notifica/action STOP per richiedere
 * l'interruzione della sessione anche quando il ViewModel non è
 * istanziato (es. processo recuperato).
 */
object RecordingBridge {
    @Volatile private var stopHandler: (() -> Unit)? = null

    fun setStopHandler(handler: (() -> Unit)?) {
        stopHandler = handler
    }

    fun requestStop() {
        stopHandler?.invoke()
    }
}
