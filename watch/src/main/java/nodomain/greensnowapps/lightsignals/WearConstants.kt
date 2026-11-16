package nodomain.greensnowapps.lightsignals

import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

/**
 * Defines watch preference keys, companion operation identifiers and presentation defaults.
 */
object WearConstants {


    /**
     * Legacy literal SOS text; localized UI labels are defined in resources.
     */
    const val SOS_TEXT = "SOS"
    /**
     * Empty label used when a control has no visible text.
     */
    const val EMPTY_STRING = ""

    /**
     * Companion operation identifier for starting the remote app.
     */
    const val COMMAND_START_ON_PHONE = "start"
    /**
     * Companion operation identifier for requesting remote installation.
     */
    const val COMMAND_INSTALL_ON_PHONE = "install"

    /**
     * Legacy phone/tablet display-name hint retained alongside current installed-node discovery.
     */
    const val TARGET_ROLE_PHONE = "Phone"
    /**
     * Legacy phone/tablet display-name hint retained alongside current installed-node discovery.
     */
    const val TARGET_ROLE_TABLET = "Tablet"

    /**
     * Feedback token that selects the phone-installation explanation in the watch UI.
     */
    const val APP_IS_NOT_INSTALLED_ON_PHONE = "The app isn't installed on connected phone. Do you want to install it?"
    /**
     * Feedback token for remote phone startup with selected modern or legacy control.
     */
    const val APP_LAUNCHED_ON_PHONE = "Light Signals has been launched on the phone. You can now control the app from the watch"
    /**
     * Feedback token for an unsuccessful remote phone-app startup.
     */
    const val APP_FAILED_TO_LAUNCH_ON_PHONE = "Failed to launch the remote app on node"
    /**
     * Feedback token when discovery or handoff cannot confirm phone control.
     */
    const val PHONE_CONNECTION_UNCONFIRMED = "Phone connection was not confirmed"
    /**
     * Feedback token for discovery without a connected phone candidate.
     */
    const val NO_CONNECTED_PHONES = "No connected phones found"
    /**
     * Feedback token for an accepted phone installation request, without confirming completion.
     */
    const val INSTALLATION_HAS_BEEN_STARTED_ON_PHONE = "Installation has been started on the phone"
    /**
     * Feedback token for a failed phone installation request.
     */
    const val INSTALLATION_ON_PHONE_FAILED = "Installation on the phone has been failed. Please, try later"

    /**
     * Stable preferences key for the torch selection.
     */
    const val PREFERENCE_FLASH_ON = "flash_on"
    /**
     * Stable preferences key for the blink half-period in milliseconds.
     */
    const val PREFERENCE_BLINK_DELAY = "blink_delay"
    /**
     * Stable preferences key for the Blink selection (legacy spelling retained).
     */
    const val PREFERENCE_BLINK_DON = "blink_don"
    /**
     * Stable preferences key for the selected sample number.
     */
    const val PREFERENCE_ACTIVE_SOUND = "active_sound"
    /**
     * Stable preferences key for the SOS selection.
     */
    const val PREFERENCE_SOS_ON = "sos_on"
    /**
     * Stable preferences key for the encoded custom-colour position.
     */
    const val PREFERENCE_PROGRESS_COLOR_PICKER = "progress_color_picker"
    /**
     * Stable preferences key for the frequency-slider interval.
     */
    const val PREFERENCE_PROGRESS_FREQUENCY_PICKER = "progress_frequency_picker"
    /**
     * Stable preferences key for the selected pager page.
     */
    const val PREFERENCE_ACTIVE_SCREEN = "active_screen"
    /**
     * Stable preferences key for the watch media-volume fraction.
     */
    const val PREFERENCE_VOLUME = "volume"

    /**
     * Legacy literal app title retained alongside localized screen resources.
     */
    const val TITLE_ON_SIGNAL_SCREEN = "Light Signals"
    /**
     * Legacy literal SOS label retained alongside localized screen resources.
     */
    const val SOS_ON_SIGNAL_SCREEN = "SOS"

    /**
     * Shared default encoded position for the custom-colour control.
     */
    const val DEFAULT_COLOR_PROGRESS = SignalColorPalette.DEFAULT_PROGRESS

    /**
     * Fallback watch media-volume fraction when no valid preference is stored.
     */
    const val DEFAULT_VOLUME = 0.5f

}
