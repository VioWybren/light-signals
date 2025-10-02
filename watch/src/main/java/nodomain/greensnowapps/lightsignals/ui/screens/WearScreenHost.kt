package nodomain.greensnowapps.lightsignals.ui.screens

//import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val soundVm: WearSoundViewModel = hiltViewModel() //don't delete !! need for sound
    val screenIndex by mainVm.screenIndex.collectAsState()
    // We have exactly two pages: index 0 = Control, index 1 = Signal
    val pagerState = rememberPagerState(initialPage = screenIndex) { 2 }
    val scrollScope = rememberCoroutineScope()

    // Whenever pager swipe changes page, update VM’s screenIndex
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                mainVm.updateScreenIndex(page)
            }
    }

    // Whenever screenIndex in VM changes, animate pager to that page
    LaunchedEffect(screenIndex) {
        if (pagerState.currentPage != screenIndex) {
            pagerState.animateScrollToPage(screenIndex)
        }
    }

   Scaffold (
//        timeText = {
//            if (pagerState.currentPage == 1) {
//                TimeText()
//            }
//        },
    ) {
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
                beyondViewportPageCount = 1,    // <— keep one page on each side alive; very important !!
                flingBehavior =
                    PagerScaffoldDefaults.snapWithSpringFlingBehavior(state = pagerState),
                modifier = Modifier
                    .fillMaxWidth()
            ) { pageIndex ->
                AnimatedPage(pageIndex = pageIndex, pagerState = pagerState) {
                    when (pageIndex) {
//                    0 -> WearMainScreen(mainVm, onNext = { mainVm.updateScreenIndex(1) })
//                    1 -> WearSignalScreen(mainVm, onBack = { mainVm.updateScreenIndex(0) })
//                    0 -> WearMainScreen(mainVm, soundVm)
//                    1 -> WearSignalScreen(mainVm, soundVm)
                        0 -> WearMainScreen(mainVm, soundVm, onNext = { scrollScope.launch { pagerState.animateScrollToPage(1) } })
                        1 -> WearSignalScreen(mainVm, soundVm, onBack = { scrollScope.launch { pagerState.animateScrollToPage(0) } })
//                        0 -> WearMainScreen(mainVm, soundVm, onNext = { })
//                        1 -> WearSignalScreen(mainVm, soundVm, onBack = { })
                    }
                }
            }
        }
    }
}

//@Composable
//fun WearScreenHost() {
//    val mainVm: WearMainViewModel = hiltViewModel()
//    val soundVm: WearSoundViewModel = hiltViewModel()
//
//    val screenIndex by mainVm.screenIndex.collectAsState()
//    val pagerState = rememberPagerState(initialPage = screenIndex) { 2 }
//    val scope = rememberCoroutineScope()
//
//    // 1) Pager -> VM: only update VM when user finished scrolling (idle)
//    LaunchedEffect(pagerState) {
//        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
//            .map { (page, inProgress) -> page to inProgress }
//            .filter { (_, inProgress) -> !inProgress }       // only when idle
//            .map { (page, _) -> page }
//            .distinctUntilChanged()
//            .collect { page -> mainVm.updateScreenIndex(page) }
//    }
//
//    // 2) VM -> Pager: when VM screenIndex changes, wait till pager is idle then animate
//    LaunchedEffect(screenIndex) {
//        // Wait until pager is not being scrolled by user (or already idle)
//        snapshotFlow { pagerState.isScrollInProgress }
//            .filter { inProgress -> !inProgress }
//            .first() // suspend until idle (immediate if already idle)
//
//        if (pagerState.currentPage != screenIndex) {
//            // animate to target — this won't run if user is actively swiping
//            pagerState.animateScrollToPage(screenIndex)
//        }
//    }
//
//    Scaffold(
//        timeText = {
//            if (pagerState.currentPage == 1) TimeText()
//        },
//    ) {
//        HorizontalPagerScaffold(
//            pagerState = pagerState,
//            pageIndicator = {
//                HorizontalPageIndicator(
//                    pagerState = pagerState,
//                    selectedColor = Color.White,
//                    unselectedColor = Color.Gray,
//                    backgroundColor = Color.Transparent
//                )
//            },
//        ) {
//            HorizontalPager(
//                state = pagerState,
//                flingBehavior =
//                    PagerScaffoldDefaults.snapWithSpringFlingBehavior(state = pagerState),
//                modifier = Modifier.fillMaxSize() // use full size for smoother layout
//            ) { pageIndex ->
//                when (pageIndex) {
//                    0 -> WearMainScreen(mainVm, soundVm)
//                    1 -> WearSignalScreen(mainVm, soundVm)
//                }
//            }
//        }
//    }
//}
//

//@Composable
//fun WearScreenHost() {
//    val mainVm: WearMainViewModel = hiltViewModel()
//    val soundVm: WearSoundViewModel = hiltViewModel()
//
//    val screenIndex by mainVm.screenIndex.collectAsState()
//    val pagerState = rememberPagerState(initialPage = screenIndex) { 2 }
//    val scope = rememberCoroutineScope()
//
//    // Track whether we've performed the initial sync -> use scrollToPage (no animation)
//    val initialSyncDone = remember { mutableStateOf(false) }
//
//    // Pager -> VM: update only when user finished scrolling (idle)
//    LaunchedEffect(pagerState) {
//        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
//            .map { it.first to it.second }
//            .filter { (_, inProgress) -> !inProgress }
//            .map { (page, _) -> page }
//            .distinctUntilChanged()
//            .collect { page -> mainVm.updateScreenIndex(page) }
//    }
//
//    // VM -> Pager: for the very first time, jump (no animation); afterwards animate
//    LaunchedEffect(screenIndex) {
//        // Wait until pager is idle (so we don't interrupt a swipe)
//        snapshotFlow { pagerState.isScrollInProgress }
//            .filter { inProgress -> !inProgress }
//            .first()
//
//        if (pagerState.currentPage != screenIndex) {
//            if (!initialSyncDone.value) {
//                // First sync on startup: jump instantly so there's no conflicting animation/jank
//                pagerState.scrollToPage(screenIndex)
//                initialSyncDone.value = true
//            } else {
//                // Normal behavior after startup: animate
//                pagerState.animateScrollToPage(screenIndex)
//            }
//        } else {
//            // If currentPage already equals screenIndex, still mark initial done
//            initialSyncDone.value = true
//        }
//    }
//
//    Scaffold(
//        timeText = { if (pagerState.currentPage == 1) TimeText() },
//    ) {
//        HorizontalPagerScaffold(
//            pagerState = pagerState,
//            pageIndicator = {
//                HorizontalPageIndicator(
//                    pagerState = pagerState,
//                    selectedColor = Color.White,
//                    unselectedColor = Color.Gray,
//                    backgroundColor = Color.Transparent
//                )
//            },
//        ) {
//            HorizontalPager(
//                state = pagerState,
//                // keep pages composed while swiping so destination is ready (API name varies)
//                flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(state = pagerState),
//                modifier = Modifier.fillMaxSize()
//            ) { pageIndex ->
//                when (pageIndex) {
//                    0 -> WearMainScreen(mainVm, soundVm)
//                    1 -> WearSignalScreen(mainVm, soundVm)
//                }
//            }
//        }
//    }
//}
//
