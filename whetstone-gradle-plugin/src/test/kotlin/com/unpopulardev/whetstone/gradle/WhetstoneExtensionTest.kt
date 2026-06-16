package com.unpopulardev.whetstone.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhetstoneExtensionTest {

    private fun extension() =
        ProjectBuilder.builder().build().objects.newInstance(WhetstoneExtension::class.java)

    @Test
    fun `daggerInterop add-on is off by default`() {
        // Default-off matters: interop is a migration aid, not the steady state, so a fresh
        // consumer must not silently opt into Dagger annotation/runtime recognition.
        assertFalse(extension().addOns.daggerInterop.get())
    }

    @Test
    fun `daggerInterop add-on can be turned on`() {
        val extension = extension()
        extension.addOns.daggerInterop.set(true)
        assertTrue(extension.addOns.daggerInterop.get())
    }
}
