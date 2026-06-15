package com.unpopulardev.whetstone.event

public interface ComponentInjectionListener {
    public fun onInjectStart(injectedComponent: InjectedComponent)
    public fun onInjectFinish(injectedComponent: InjectedComponent)
}
