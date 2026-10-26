package fr.printmonitor

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import fr.printmonitor.data.ConfigStore

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        MonitorHub.init(this, ConfigStore.load(this))
    }
}

/** Service au premier plan : garde les connexions ouvertes écran éteint pour recevoir les alertes. */
class MonitorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.serviceNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.SERVICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.SERVICE_ID, n)
        }
        return START_STICKY
    }

    companion object {
        fun update(ctx: Context, enabled: Boolean) {
            val i = Intent(ctx, MonitorService::class.java)
            if (enabled) ctx.startForegroundService(i) else ctx.stopService(i)
        }
    }
}
