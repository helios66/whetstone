package com.unpopulardev.whetstone.sample

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.unpopulardev.whetstone.Whetstone
import com.unpopulardev.whetstone.activity.ContributesActivityInjector
import com.unpopulardev.whetstone.compose.injectedViewModel
import com.unpopulardev.whetstone.sample.databinding.ActivityMainBinding
import com.unpopulardev.whetstone.sample.library.MainDependency
import com.unpopulardev.whetstone.sample.library.TodoRoot
import com.unpopulardev.whetstone.sample.library.TodoViewModel
import javax.inject.Inject

/**
 * The sample app's single screen. Whetstone is the DI engine for the whole app: this activity is
 * member-injected (@ContributesActivityInjector), it hosts the Compose Todo app whose ViewModel is
 * Whetstone-provided (injectedViewModel), and it also drives a Whetstone-injected Service, Worker,
 * and Fragment — so every Android entry point in the app resolves through the same generated graph.
 */
@ContributesActivityInjector
class MainActivity : AppCompatActivity() {
    private val serviceIntent by lazy { Intent(this, MainService::class.java) }

    @Inject
    internal lateinit var dependency: MainDependency

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so Whetstone can install its FragmentFactory and inject
        // this activity's @Inject members.
        Whetstone.inject(this)
        super.onCreate(savedInstanceState)
        Log.d("Activity", dependency.getMessage("MainActivity"))

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.composeView.setContent {
            MaterialTheme { TodoHost() }
        }

        // Exercise the @ContributesFragment path: the installed multibinding FragmentFactory
        // constructor-injects MainFragment.
        supportFragmentManager.fragmentFactory.instantiate(classLoader, MainFragment::class.java.name)

        // @ContributesServiceInjector + @ContributesWorker: both resolve through the same graph.
        ContextCompat.startForegroundService(this, serviceIntent)
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequest.from(MainWorker::class.java))
    }

    override fun onDestroy() {
        stopService(serviceIntent)
        WorkManager.getInstance(this).cancelAllWork()
        super.onDestroy()
    }
}

/** Hosts the Todo app, whose [TodoViewModel] is provided by Whetstone's generated graph. */
@Composable
fun TodoHost() {
    val viewModel: TodoViewModel = injectedViewModel()
    TodoRoot(viewModel)
}
