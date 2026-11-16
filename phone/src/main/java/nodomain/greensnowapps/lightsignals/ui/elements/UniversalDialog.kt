package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Displays a phone confirmation dialog with caller-supplied text and hoisted confirm/dismiss
 * events.
 *
 * An empty dismissText hides the dismiss button; the caller owns dialog visibility.
 *
 * @param title Heading shown by the dialog.
 * @param message Body text displayed in the dialog.
 * @param confirmText Label of the confirmation button.
 * @param dismissText Label of the optional dismiss button; an empty label hides it.
 * @param onConfirm Hoisted callback invoked by confirmation.
 * @param onDismiss Hoisted callback invoked when the dialog is dismissed.
 */
@Composable
fun UniversalDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String = "",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(text = title) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = if (dismissText.isNotEmpty()) {
            {
                TextButton(onClick = onDismiss) {
                    Text(dismissText)
                }
            }
        } else null
    )
}

