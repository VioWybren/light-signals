package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
//import androidx.compose.material3.ToggleButton
//import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nodomain.greensnowapps.lightsignals.COLOR_CHECKED_BUTTON
import nodomain.greensnowapps.lightsignals.COLOR_UNCHECKED_BUTTON

//@OptIn(ExperimentalMaterial3ExpressiveApi::class)
//@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
//    ToggleButton(
    IconToggleButton(
        checked = checkedBtn,
        onCheckedChange = onCheckedChange,
//        colors = ToggleButtonDefaults.toggleButtonColors(
            colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = Color(COLOR_UNCHECKED_BUTTON),
            contentColor = Color.White,
            checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
            checkedContentColor = Color.White
        ),
//        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        modifier = Modifier
//            .padding(2.dp)
            .border(BorderStroke(width = 1.dp, color = Color.White), shape = IconButtonDefaults.standardShape)
            .width(toggleBtnWidth)
            .height(toggleBtnHeight),
//        shapes = ToggleButtonDefaults.shapes(
//        shape = IconButtonDefaults.standardShape,
        shape = IconButtonDefaults.outlinedShape,
//            (
//            shape = CircleShape,
//            pressedShape = CircleShape,
//            checkedShape = CircleShape
//        ),
//        border = BorderStroke(width = 1.dp, color = Color.White),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Localized description",
            modifier = modifier
        )
    }

}