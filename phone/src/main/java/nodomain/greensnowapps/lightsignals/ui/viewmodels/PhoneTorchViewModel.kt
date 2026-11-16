package nodomain.greensnowapps.lightsignals.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.model.PhoneTorchAvailability
import javax.inject.Inject


/**
 * Queries torch availability on IO and exposes hasTorch to the phone controls.
 *
 * viewModelScope owns the query; the UI observes the result without blocking the main thread.
 *
 * @property availability Permission-free hardware availability query.
 */
@HiltViewModel
class PhoneTorchViewModel @Inject constructor(
    private val availability: PhoneTorchAvailability
) : ViewModel() {

    /**
     * Mutable rear-flash query result: null while pending, then true or false after the IO
     * query.
     */
    private val _hasTorch = MutableStateFlow<Boolean?>(null)
    /**
     * Read-only rear-flash availability: null before the IO query finishes, then its Boolean
     * result.
     */
    val hasTorch: StateFlow<Boolean?> = _hasTorch

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _hasTorch.value = availability.hasTorch()
        }
    }

}
