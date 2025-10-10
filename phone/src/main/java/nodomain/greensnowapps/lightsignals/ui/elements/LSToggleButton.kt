package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nodomain.greensnowapps.lightsignals.COLOR_CHECKED_BUTTON
import nodomain.greensnowapps.lightsignals.COLOR_UNCHECKED_BUTTON

@Composable
fun LSToggleButton(
    toggleBtnWidth: Dp,
    toggleBtnHeight: Dp,
    checkedBtn: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier
)
{
    IconToggleButton(
        checked = checkedBtn,
        onCheckedChange = onCheckedChange,
            colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = Color(COLOR_UNCHECKED_BUTTON),
            contentColor = Color.White,
            checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
            checkedContentColor = Color.White
        ),
        modifier = Modifier
            .border(BorderStroke(width = 1.dp, color = Color.White), shape = IconButtonDefaults.standardShape)
            .width(toggleBtnWidth)
            .height(toggleBtnHeight),
        shape = IconButtonDefaults.outlinedShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Localized description",
            modifier = modifier
        )
    }
}