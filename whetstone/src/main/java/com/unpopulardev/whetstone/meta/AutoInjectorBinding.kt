package com.unpopulardev.whetstone.meta

import com.unpopulardev.whetstone.InternalWhetstoneApi
import kotlin.reflect.KClass

@InternalWhetstoneApi
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class AutoInjectorBinding(val scope: KClass<*>)
