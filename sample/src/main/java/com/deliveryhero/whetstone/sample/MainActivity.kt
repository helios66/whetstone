package com.deliveryhero.whetstone.sample

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.deliveryhero.whetstone.Whetstone
import com.deliveryhero.whetstone.activity.ContributesActivityInjector
import com.deliveryhero.whetstone.compose.injectedViewModel
import com.deliveryhero.whetstone.sample.databinding.ActivityMainBinding
import com.deliveryhero.whetstone.sample.library.MainDependency
import com.deliveryhero.whetstone.sample.library.MainViewModel
import com.deliveryhero.whetstone.sample.library.Priority
import com.deliveryhero.whetstone.sample.library.TodoDestination
import com.deliveryhero.whetstone.sample.library.TodoRoot
import com.deliveryhero.whetstone.sample.library.TodoViewModel
import com.deliveryhero.whetstone.viewmodel.injectedViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay

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
            TodoHost()
        }

        // Exercise the @ContributesFragment path: the installed multibinding FragmentFactory
        // constructor-injects MainFragment.
        supportFragmentManager.fragmentFactory.instantiate(classLoader, MainFragment::class.java.name)

        ContextCompat.startForegroundService(this, serviceIntent)
        val request = OneTimeWorkRequest.from(MainWorker::class.java)
        WorkManager.getInstance(this).enqueue(request)
    }

    override fun onDestroy() {
        stopService(serviceIntent)
        WorkManager.getInstance(this).cancelAllWork()
        super.onDestroy()
    }
}

@Composable
fun TodoHost() {
    val viewModel: TodoViewModel = injectedViewModel()
    // Scripted end-to-end flow that also navigates between screens, so the trace
    // covers list / detail / stats composition + the nav router, no manual taps.
    LaunchedEffect(Unit) {
        delay(600)
        viewModel.add("Email the client", categoryId = 0)        // Work
        delay(300)
        viewModel.add("Buy groceries", categoryId = 2)            // Shopping
        delay(300)
        viewModel.add("Call the plumber", categoryId = 1)         // Home
        delay(300)
        viewModel.navigateTo(TodoDestination.Detail(0))           // open detail screen
        delay(500)
        viewModel.editTodo(0, "Email the client back", "Re: Q3 invoice", 0, Priority.HIGH)
        delay(300)
        viewModel.navigateBack()                                  // back to list
        delay(300)
        viewModel.selectFilter(2)                                 // filter -> Shopping
        delay(300)
        viewModel.selectFilter(null)                             // back to All
        delay(300)
        viewModel.refreshStats()                                 // background suspend work (off main)
        delay(300)
        viewModel.navigateTo(TodoDestination.Stats)               // stats screen (also refreshes)
        delay(500)
        viewModel.navigateBack()                                  // back to list
        delay(300)
        viewModel.toggle(2)                                       // complete "Call the plumber"
        delay(300)
        viewModel.delete(1)                                       // delete the grocery item
        delay(300)
        viewModel.clearCompleted()                                // sweep completed
    }
    TodoRoot(viewModel)
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = injectedViewModel(),
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick?.invoke() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Compose")
        Text(text = viewModel.getHelloWorld())
    }
}

class BasicActivity : ComponentActivity() {

    private val viewModel by injectedViewModel<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = "${viewModel.getHelloWorld()} from Basic activity"
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }
}
