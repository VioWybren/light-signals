package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AlertDialogDefaults.ConfirmIcon
import androidx.wear.compose.material3.AlertDialogDefaults.DismissIcon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * Displays a watch confirmation dialog from visible and caller-supplied text.
 *
 * Hoists confirmation and dismissal; the caller owns whether the dialog remains visible.
 *
 * @param visible Whether the dialog should be rendered.
 * @param title Heading shown by the dialog.
 * @param message Body text displayed in the dialog.
 * @param onConfirm Hoisted callback invoked by confirmation.
 * @param onDismiss Hoisted callback invoked when the dialog is dismissed.
 */
@Composable
fun WearUniversalDialog(
    visible: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        visible = visible,
        onDismissRequest = { onDismiss() },
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color(0xffff6f00)) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    onConfirm()
                },
                content = ConfirmIcon
            )
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(
                onClick = onDismiss,
                content = DismissIcon
            )
        },
    )
}
