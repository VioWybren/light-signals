package nodomain.greensnowapps.lightsignals.ui.viewmodels

//class PhoneControlViewModel @Inject constructor(
//    private val controlRepository: PhoneSyncStateRepository
//): ViewModel() {
////    val phoneControlState: StateFlow<PhoneControlState> = controlRepository.stateFlow
//
//    fun updateState(newState: PhoneControlState) {
//        viewModelScope.launch {
//            controlRepository.sendState(newState)
//        }
//    }
//
//    override fun onCleared() {
//        super.onCleared()
//        controlRepository.clear()
//    }
//}