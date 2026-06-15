package com.unpopulardev.whetstone.injector

import dev.zacsweers.metro.MembersInjector
import kotlin.reflect.KClass

internal typealias MembersInjectorMap = Map<KClass<*>, MembersInjector<*>>
