package com.unpopulardev.whetstone.compose

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.unpopulardev.whetstone.Whetstone
import com.unpopulardev.whetstone.app.ApplicationComponent

@Composable
internal fun applicationComponent(): ApplicationComponent {
    val context = LocalContext.current
    return remember(context) {
        val app = context.applicationContext as Application
        Whetstone.fromApplication(app)
    }
}
