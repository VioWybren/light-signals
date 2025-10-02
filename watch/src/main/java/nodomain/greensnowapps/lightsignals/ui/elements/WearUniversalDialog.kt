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
