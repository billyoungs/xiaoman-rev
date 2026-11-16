package dev.xiaoman.app

import android.content.Intent
import dev.xiaoman.app.session.AgentSession
import dev.xiaoman.app.settings.AppSettings
import dev.xiaoman.hands.AccessibilityDevice
import dev.xiaoman.hands.XiaomanAccessibilityService
import dev.xiaoman.hands.StatusOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Shows the hands' [StatusOverlay] (step text + Stop) while the session is busy and the
 * accessibility service is connected; the overlay must be created with the connected service.
 * The pill's background alpha follows [settings] live, so adjusting transparency in Settings
 * takes effect on the next step even mid-task.
 *
 * [settings] is a provider, read only once the pill is actually shown: building the encrypted
 * store needs the Android keystore, which must not be touched while the app merely starts up.
 */
class OverlayController(
    private val session: AgentSession,
    private val device: AccessibilityDevice,
    private val services: StateFlow<XiaomanAccessibilityService?> = XiaomanAccessibilityService.instance,
    private val uiVisible: StateFlow<Boolean> = MutableStateFlow(false),
    private val settings: () -> StateFlow<AppSettings> = { MutableStateFlow(AppSettings()) },
) {
    private var overlay: StatusOverlay? = null
    private var owner: XiaomanAccessibilityService? = null

    private data class Input(val service: XiaomanAccessibilityService?, val show: Boolean, val status: String?)

    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.Main.immediate) {
        combine(services, session.busy, session.status, uiVisible) { service, busy, status, visible ->
            Input(service, busy && !visible, status)
        }.collect { (service, show, status) ->
            if (!show || service == null) {
                remove()
                return@collect
            }
            val alpha = settings().value.overlayAlpha
            if (owner !== service) {
                remove()
                overlay = StatusOverlay(service, alpha).also {
                    it.onStop = { session.stop() }
                    it.onOpen = { service.startActivity(Intent(service, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
                    device.overlay = it
                    it.show()
                }
                owner = service
            } else {
                overlay?.setBackgroundAlpha(alpha)
            }
            overlay?.update(status ?: "小满")
        }
    }

    private fun remove() {
        overlay?.hide()
        overlay = null
        owner = null
        device.overlay = null
    }
}