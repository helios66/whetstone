package com.unpopulardev.whetstone.sample

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.content.Context
import android.os.Build
import android.util.Log
import com.unpopulardev.whetstone.Whetstone
import com.unpopulardev.whetstone.app.ApplicationComponentOwner
import com.unpopulardev.whetstone.app.ContributesAppInjector
import com.unpopulardev.whetstone.sample.MainService.Companion.NOTIFICATION_CHANNEL_ID
import com.unpopulardev.whetstone.sample.library.MainDependency
import javax.inject.Inject

@ContributesAppInjector(generateAppComponent = true)
class MainApplication : Application(), ApplicationComponentOwner {

    @Inject
    internal lateinit var dependency: MainDependency

    override val applicationComponent = GeneratedApplicationComponent.create(this)

    override fun onCreate() {
        // Compose composition tracing auto-installs when mundus-compose-tracing is on the classpath.
        // Release is built with -Pmundus.compose.tracing=false (0.13.0 rename; was composeTracing) so
        // the plugin drops the dep; the runtime kill switch -Dmundus.composeTracing=false also exists
        // for a present-but-off case (the -D system property keeps its original name).
        Whetstone.inject(this)
        super.onCreate()
        Log.d("App", dependency.getMessage("Application"))
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
                createNotificationChannel(
                    NotificationChannel(NOTIFICATION_CHANNEL_ID, "Main Channel", IMPORTANCE_DEFAULT)
                )
            }
        }
    }
}
