package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun UniversalDialog(
        title: String,
        message: String,
        confirmText: String,
        dismissText: String = "", // Default to an empty string if not needed
        onConfirm: () -> Unit,
        onDismiss: () -> Unit = {} // Default to empty lambda
) {
    AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(text = title) },
            text = { Text(text = message, style =  MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = onConfirm, ) {
                    Text(confirmText)
                }
            },
            // Only show dismissButton if dismissText is not empty
            dismissButton = if (dismissText.isNotEmpty()) {
                {
                    TextButton(onClick = onDismiss) {
                        Text(dismissText)
                    }
                }
            } else null
    )
}

