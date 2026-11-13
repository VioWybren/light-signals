package nodomain.greensnowapps.lightsignals.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.PagerScaffoldDefaults
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.ui.viewmodels.WearMainViewModel
import nodomain.greensnowapps.lightsignals.ui.viewmodels.WearSoundViewModel

@Composable
fun WearScreenHost() {

    val mainVm: WearMainViewModel = hiltViewModel()
    val soundVm: WearSoundViewModel = hiltViewModel()
    val initialized by mainVm.isInitialized.collectAsStateWithLifecycle()
    // The Activity retains the splash screen while settings are loading.
    if (!initialized) return
    val screenIndex by mainVm.screenIndex.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = screenIndex) { 2 }
    val scrollScope = rememberCoroutineScope()

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

    Scaffold()
    {
        HorizontalPagerScaffold(
            pagerState = pagerState,
            pageIndicator = {
                HorizontalPageIndicator(
                    pagerState = pagerState,
                    selectedColor = Color.White,
                    unselectedColor = Color.Gray,
                    backgroundColor = Color.Transparent
                )
            },
        )
        {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                flingBehavior =
                    PagerScaffoldDefaults.snapWithSpringFlingBehavior(state = pagerState),
                modifier = Modifier
                    .fillMaxWidth()
            ) { pageIndex ->
                AnimatedPage(pageIndex = pageIndex, pagerState = pagerState) {
                    when (pageIndex) {
                        0 -> WearMainScreen(mainVm, soundVm, onNext = { scrollScope.launch { pagerState.animateScrollToPage(1) } })
                        1 -> WearSignalScreen(mainVm, soundVm, isActive = pagerState.currentPage == pageIndex, onBack = { scrollScope.launch { pagerState.animateScrollToPage(0) } })
                    }
                }
            }
        }
    }
}

