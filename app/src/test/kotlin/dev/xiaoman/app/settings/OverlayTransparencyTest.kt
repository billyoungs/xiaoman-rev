package dev.xiaoman.app.settings

import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayTransparencyTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun store() = SettingsStore(
        context.getSharedPreferences("overlay-plain", Context.MODE_PRIVATE),
        context.getSharedPreferences("overlay-secret", Context.MODE_PRIVATE),
    )

    @Test fun defaultKeepsTheOriginalPillBackground() {
        assertEquals(6, AppSettings().overlayTransparency)
        assertEquals(0xF0, AppSettings().overlayAlpha)
    }

    @Test fun transparencyPercentMapsToBackgroundAlpha() {
        assertEquals(255, AppSettings(overlayTransparency = 0).overlayAlpha)
        assertEquals(128, AppSettings(overlayTransparency = 50).overlayAlpha)
        assertEquals(0, AppSettings(overlayTransparency = 100).overlayAlpha)
    }

    @Test fun outOfRangeValuesAreClampedNotCrashed() {
        assertEquals(255, AppSettings(overlayTransparency = -20).overlayAlpha)
        assertEquals(0, AppSettings(overlayTransparency = 150).overlayAlpha)
        assertEquals(100, SettingsForm(overlayTransparency = 150).toSettings()?.overlayTransparency)
        assertEquals(0, SettingsForm(overlayTransparency = -5).toSettings()?.overlayTransparency)
    }

    @Test fun formRoundTripsTransparency() {
        val settings = AppSettings(overlayTransparency = 42)
        assertEquals(42, SettingsForm.of(settings).overlayTransparency)
        assertEquals(42, SettingsForm.of(settings).toSettings()?.overlayTransparency)
    }

    @Test fun storePersistsTransparencyAcrossReload() {
        val store = store()
        store.save(store.current.copy(overlayTransparency = 73))
        val loaded = store().current
        assertEquals(73, loaded.overlayTransparency)
        assertEquals(69, loaded.overlayAlpha)
    }

    @Test fun storeClampsACorruptedStoredValue() {
        context.getSharedPreferences("overlay-plain", Context.MODE_PRIVATE)
            .edit().putInt("overlay.transparency", 999).commit()
        assertEquals(100, store().current.overlayTransparency)
    }
}