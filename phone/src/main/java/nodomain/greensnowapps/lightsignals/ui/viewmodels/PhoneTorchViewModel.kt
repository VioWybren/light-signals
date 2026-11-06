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


@HiltViewModel
class PhoneTorchViewModel @Inject constructor(
    private val availability: PhoneTorchAvailability
) : ViewModel() {

    private val _hasTorch = MutableStateFlow<Boolean?>(null)
    val hasTorch: StateFlow<Boolean?> = _hasTorch

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _hasTorch.value = availability.hasTorch()
        }
    }

}
