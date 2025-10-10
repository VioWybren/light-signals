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

