package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogColors
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.Text

@Composable
fun WearFailureConfirmationDialog(
    showDialog: Boolean,
    message: String,
    onDismiss: () -> Unit
) {

    ConfirmationDialog(
        visible = showDialog,
        onDismissRequest = {
            onDismiss()
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Visible
            )
        },
        durationMillis = 5000L,
        content = {
            ConfirmationDialogDefaults.FailureIcon()
        },
        colors = ConfirmationDialogColors(
            iconColor = Color.Yellow,
            iconContainerColor = Color.Transparent,
            textColor = Color.White
        )
    )
}