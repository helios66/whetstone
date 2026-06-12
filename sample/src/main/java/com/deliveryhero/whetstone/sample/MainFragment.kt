package com.deliveryhero.whetstone.sample

import android.util.Log
import androidx.fragment.app.Fragment
import com.deliveryhero.whetstone.fragment.ContributesFragment
import com.deliveryhero.whetstone.sample.library.MainDependency
import javax.inject.Inject

/**
 * Exercises the `@ContributesFragment` path: the fragment is constructor-injected and instantiated
 * by Whetstone's multibinding [androidx.fragment.app.FragmentFactory].
 */
@ContributesFragment
class MainFragment @Inject constructor(
    private val dependency: MainDependency,
) : Fragment() {

    init {
        Log.d("Fragment", dependency.getMessage("MainFragment"))
    }
}
