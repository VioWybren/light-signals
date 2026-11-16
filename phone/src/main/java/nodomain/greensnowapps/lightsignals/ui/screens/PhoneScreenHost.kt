package nodomain.greensnowapps.lightsignals.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel

/**
 * Hosts the phone controls/signal pager and its navigation, lifecycle-aware state and snackbar
 * feedback.
 */
@Composable
fun PhoneScreenHost() {
    val mainVm: PhoneMainViewModel = hiltViewModel()
    val isReady by mainVm.isReady.collectAsStateWithLifecycle()
    // The Activity retains the splash screen while settings are loading.
    if (!isReady) return
    val screenIndex by mainVm.screenIndex.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val watchConnectedMessage = stringResource(R.string.snackbar_watch_control_available)

    LaunchedEffect(mainVm, lifecycleOwner, watchConnectedMessage) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            mainVm.watchConnectedEvents.collect {
                snackbarHostState.showSnackbar(watchConnectedMessage, duration = SnackbarDuration.Short)
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = screenIndex,
        pageCount = { 2 }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                mainVm.updateScreenIndex(page)
            }
    }

    LaunchedEffect(screenIndex) {
        if (pagerState.currentPage != screenIndex) {
            pagerState.animateScrollToPage(screenIndex)
        }
    }


    LaunchedEffect(pagerState, mainVm) {
        mainVm.openControlsRequests.collect {
            pagerState.scrollToPage(0)
        }
    }

    val scrollScope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> PhoneMainScreen(
                    viewModel = mainVm,
                    onNext = { scrollScope.launch { pagerState.animateScrollToPage(1) } },
                    snackbarHostState = snackbarHostState,
                )
                1 -> PhoneSignalScreen(
                    viewModel = mainVm,
                    onBack = { scrollScope.launch { pagerState.animateScrollToPage(0) } },
                )
            }
        }

        val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

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

        SnackbarHost(
            hostState = snackbarHostState,
            snackbar = { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = Color.Black,
                    contentColor = Color.White,
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 10.dp, end = 10.dp),
        )
    }
}
