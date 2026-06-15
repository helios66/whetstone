package com.unpopulardev.whetstone.sample

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.unpopulardev.whetstone.Whetstone
import com.unpopulardev.whetstone.sample.library.MainDependency
import com.unpopulardev.whetstone.view.ContributesViewInjector
import javax.inject.Inject

@ContributesViewInjector
public class MainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {

    @Inject
    public lateinit var dependency: MainDependency

    init {
        if (!isInEditMode) {
            Whetstone.inject(view = this)
            text = dependency.getMessage("MainView")
        }
    }
}
