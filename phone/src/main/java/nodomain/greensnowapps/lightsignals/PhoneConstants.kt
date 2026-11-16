package nodomain.greensnowapps.lightsignals

import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

/**
 * Stable ID used to post and remove the active flashlight notification.
 */
const val NOTIF_ID = 55555777


/**
 * Feedback token that selects the watch-installation explanation in the phone UI.
 */
const val APP_IS_NOT_INSTALLED_ON_WATCH = "The app isn't installed on connected watch. Do you want to install it?"
/**
 * Feedback token for discovery without a connected watch candidate.
 */
const val NO_CONNECTED_WATCHES = "No connected watches found"
/**
 * Feedback token for a watch control handoff that was not confirmed.
 */
const val WATCH_CONNECTION_UNCONFIRMED = "Watch connection unconfirmed"
/**
 * Feedback token for an unsuccessful remote watch-app startup.
 */
const val APP_FAILED_TO_LAUNCH_ON_WATCH = "Failed to launch the remote app on node"
/**
 * Feedback token for an accepted watch installation request, without confirming completed
 * installation.
 */
const val INSTALLATION_ON_WATCH_STARTED = "Installation on watch has been started"
/**
 * Feedback token for a failed watch installation request.
 */
const val INSTALLATION_ON_WATCH_FAILED = "Installation on watch has been failed. Please, try later"
/**
 * Feedback token for failed Google Play services or Wearable API availability checks.
 */
const val WEARABLE_API_UNAVAILABLE = "Wearable API unavailable"

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
 * Stable preferences key for the selected colour/output ordinal.
 */
const val PREFERENCE_PREF_ACTIVE_COLOR_BUTTON = "pref_active_color_button"
/**
 * Stable preferences key for the selected pager page.
 */
const val PREFERENCE_ACTIVE_SCREEN = "active_screen"
/**
 * Stable preferences key for the saved signal-screen colour.
 */
const val PREFERENCE_SIGNAL_SCREEN_COLOR = "signal_screen_color"

/**
 * Shared default encoded position for the custom-colour control.
 */
const val DEFAULT_COLOR_PROGRESS = SignalColorPalette.DEFAULT_PROGRESS

/**
 * Companion operation identifier for starting the remote app.
 */
const val COMMAND_START_ON_WATCH = "start"
/**
 * Companion operation identifier for requesting remote installation.
 */
const val COMMAND_INSTALL_ON_WATCH = "install"

/**
 * Legacy literal app title retained alongside localized screen resources.
 */
const val TITLE_ON_SIGNAL_SCREEN = "Light Signals"
/**
 * Legacy literal SOS label retained alongside localized screen resources.
 */
const val SOS_ON_SIGNAL_SCREEN = "SOS"

/**
 * Case-insensitive display-name or debug-text hint used when choosing a watch candidate.
 */
const val TARGET_ROLE_WATCH = "Watch"
/**
 * Case-insensitive display-name or debug-text hint used when choosing a watch candidate.
 */
const val TARGET_ROLE_WEAR = "Wear"

/**
 * Legacy literal SOS text; localized UI labels are defined in resources.
 */
const val SOS_TEXT= "SOS"
/**
 * Empty label used when a control has no visible text.
 */
const val EMPTY_STRING = ""

 /**
  * ARGB container colour for a selected phone control.
  */
 const val COLOR_CHECKED_BUTTON = 0xFFc43e00
 /**
  * ARGB container colour for an unselected phone control.
  */
 const val COLOR_UNCHECKED_BUTTON = 0xffff6f00
