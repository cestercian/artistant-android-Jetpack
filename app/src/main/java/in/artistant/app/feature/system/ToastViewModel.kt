package `in`.artistant.app.feature.system

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The composition's handle on [ToastController].
 *
 * A four-line ViewModel rather than a Hilt `EntryPoint` read from the
 * composition, because `hiltViewModel()` is how every other singleton reaches a
 * screen in this app and an `EntryPointAccessors` call inside a composable would
 * be the only one of its kind. It holds no state of its own — the controller is
 * a `@Singleton` and outlives this — so it costs nothing.
 *
 * Any feature that wants to raise a toast injects [ToastController] directly
 * into ITS ViewModel; this exists only for the host.
 */
@HiltViewModel
class ToastViewModel @Inject constructor(
    private val controller: ToastController,
) : ViewModel() {
    val current: StateFlow<ToastMessage?> = controller.current

    fun show(text: String) = controller.show(text)

    fun dismiss(id: Long?) = controller.dismiss(id)
}
