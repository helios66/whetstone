package com.deliveryhero.whetstone.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.deliveryhero.whetstone.ForScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope

@ContributesTo(ApplicationScope::class)
public interface ApplicationModule {

    @Binds
    @ForScope(ApplicationScope::class)
    public val Application.bindContext: Context

    public companion object {

        @Provides
        @ForScope(ApplicationScope::class)
        public fun provideLifecycleOwner(): LifecycleOwner {
            return ProcessLifecycleOwner.get()
        }

        @Provides
        @ForScope(ApplicationScope::class)
        public fun provideCoroutineScope(
            @ForScope(ApplicationScope::class) lifecycleOwner: LifecycleOwner
        ): CoroutineScope {
            return lifecycleOwner.lifecycleScope
        }
    }
}
