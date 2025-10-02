package nodomain.greensnowapps.lightsignals.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
//import androidx.hilt.navigation.compose.hiltViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel
import nodomain.greensnowapps.lightsignals.R

private const val TAG = "PhoneScreenHost"

@Composable
fun PhoneScreenHost(
    onToggleClick: () -> Unit,

// Pre-explain
    showPreExplain: Boolean,
    preExplainMessage: String,
    onPreExplainContinue: () -> Unit,
    onDismissPreExplain: () -> Unit,
// Rationale dialog
    showRationale: Boolean,
    rationaleMessage: String,
    onRequestAgain: () -> Unit,
    onDismissRationale: () -> Unit,
// Settings dialog
    showSettings: Boolean,
    settingsMessage: String,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
) {
    val mainVm: PhoneMainViewModel = hiltViewModel()
    val screenIndex by mainVm.screenIndex.collectAsState()

    // Create a PagerState with the initial page matching screenIndex
    // Remember a pagerState for 2 pages
    val pagerState = rememberPagerState(
        initialPage = screenIndex,
        pageCount = { 2 }
    )

    // Whenever pager swipe changes page, update VM’s screenIndex
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                Log.d(TAG,"pagerState.currentPage(${page}) ")
                mainVm.updateScreenIndex(page)
            }
    }

    // Whenever screenIndex in VM changes, animate pager to that page
    LaunchedEffect(screenIndex) {
        Log.d(TAG,"LaunchedEffect($screenIndex) ")
        if (pagerState.currentPage != screenIndex) {
            pagerState.animateScrollToPage(screenIndex)
        }
    }

//    val view = LocalView.current
//    DisposableEffect(view) {
//        val activity = view.context as? Activity
//        val window = activity?.window
//        if (window != null) {
//            // let the app draw behind system bars
//            WindowCompat.setDecorFitsSystemWindows(window, false)
//            val controller = WindowInsetsControllerCompat(window, view)
//            // hide nav bar (and status bar if you want)
//            controller.hide(WindowInsetsCompat.Type.navigationBars())
//            // allow lightsignals to swipe to reveal transiently
//            controller.systemBarsBehavior =
//                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//
//            onDispose {
//                // show nav bar again and restore default behavior
//                controller.show(WindowInsetsCompat.Type.navigationBars())
//                WindowCompat.setDecorFitsSystemWindows(window, true)
//            }
//        } else {
//            onDispose { }
//        }
//    }

    val scrollScope = rememberCoroutineScope()
    // The pager itself
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,    // <— keep one page on each side alive; very important !!
        modifier = Modifier
            .fillMaxWidth()
    ) { page ->
        when (page) {
            0 -> PhoneMainScreen(mainVm, onNext = { scrollScope.launch { pagerState.animateScrollToPage(1) } })
            1 -> PhoneSignalScreen(mainVm, onBack = { scrollScope.launch { pagerState.animateScrollToPage(0) } })
        }
    }
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Indicator
    Column(
        Modifier
            .padding(top = (statusBarHeightDp + 30.dp))
            .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier
                .wrapContentHeight()
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color = if (pagerState.currentPage == iteration) Color.White else Color.DarkGray
                Box(
                    modifier = Modifier
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(10.dp)
                )
            }
        }
    }

    // PRE-EXPLAIN dialog overlays the UI before we call system dialog
    if (showPreExplain) {
        AlertDialog(
            onDismissRequest = onDismissPreExplain,
            title = { Text(stringResource(R.string.dialog_title_pre_explain)) },
            text = { Text(preExplainMessage) },
            confirmButton = { TextButton(onClick = onPreExplainContinue) { Text(stringResource(R.string._continue)) } },
            dismissButton = { TextButton(onClick = onDismissPreExplain) { Text(stringResource(R.string._cancel)) } }
        )
    }

    // RATIONALE dialog overlays the UI
    if (showRationale) {
        AlertDialog(
            onDismissRequest = onDismissRationale,
            title = { Text(stringResource(R.string.dialog_title_rationale_dialog)) },
            text = { Text(rationaleMessage) },
            confirmButton = {
                TextButton(onClick = {
                    onDismissRationale()
                    onRequestAgain()
                }) { Text(stringResource(R.string.confirm_button_rationale_dialog)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissRationale)
                { Text(stringResource(R.string._cancel)) }
            }
        )
    }

    // SETTINGS dialog overlays the UI
    if (showSettings) {
        AlertDialog(
            onDismissRequest = onDismissSettings,
            title = { Text(stringResource(R.string.dialog_title_setting_dialog)) },
            text = { Text(settingsMessage) },
            confirmButton = {
                TextButton(onClick = {
                    onDismissSettings()
                    onOpenSettings()
                }) { Text(stringResource(R.string.confirm_button_setting_dialog)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissSettings) { Text(stringResource(R.string._later)) }
            }
        )
    }
}