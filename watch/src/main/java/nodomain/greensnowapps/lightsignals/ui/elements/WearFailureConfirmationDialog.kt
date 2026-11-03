package nodomain.greensnowapps.lightsignals.ui.elements


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AlertDialogDefaults.ConfirmIcon
import androidx.wear.compose.material3.Text


@Composable
fun WearFailureConfirmationDialog(
    showDialog: Boolean,
    message: String,
    onDismiss: () -> Unit
) {

    AlertDialog(
        visible = showDialog,
        onDismissRequest = { onDismiss() },
        title = {},
        text = { Text(text = message, style = androidx.wear.compose.material3.MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) },
        icon = { Icon(Icons.Outlined.PhonelinkErase, contentDescription = null, tint = Color(0xffff6f00)) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = onDismiss,
                content = ConfirmIcon
            )
        },
        dismissButton = {},
    )
}
