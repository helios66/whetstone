package com.unpopulardev.whetstone.gradle

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

public abstract class WhetstoneExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * No longer used. Whetstone now generates Metro contributions via KSP, which run uniformly
     * across application and library modules — there is no Dagger/Anvil factory-generation toggle.
     *
     * Kept as a no-op for source compatibility; it has no effect and will be removed in a future
     * release.
     */
    @Deprecated("Whetstone no longer uses Dagger/Anvil. This property is ignored.")
    public abstract val generateDaggerFactories: Property<Boolean>

    /**
     * No longer used. Metro generates the dependency graph in-place via its compiler plugin, so
     * there are no Anvil-generated sources to sync into the IDE.
     *
     * Kept as a no-op for source compatibility; it has no effect and will be removed in a future
     * release.
     */
    @Deprecated("Whetstone no longer uses Dagger/Anvil. This property is ignored.")
    public abstract val syncGeneratedSources: Property<Boolean>

    /**
     * Allows configuring extra Whetstone add-ons.
     *
     * Currently, this only includes turning on/off Jetpack Compose and/or WorkManager support.
     */
    public val addOns: AddOnsHandler = objects.newInstance()

    /**
     * DSL function to help configure extra Whetstone add-ons
     */
    public fun addOns(action: Action<AddOnsHandler>): Unit = action.execute(addOns)
}

public abstract class AddOnsHandler @Inject constructor(objects: ObjectFactory) {
    /**
     * Turns on/off Whetstone's Jetpack Compose integration.
     *
     * When enabled, `whetstone-compose` will be automatically added to the project's dependencies.
     */
    public val compose: Property<Boolean> = objects.property<Boolean>().convention(false)

    /**
     * Turns on/off Whetstone's work manager integration.
     *
     * When enabled, `whetstone-worker` will be automatically added to the project's dependencies.
     */
    public val workManager: Property<Boolean> = objects.property<Boolean>().convention(false)
}
