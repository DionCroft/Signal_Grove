package org.hermesmobile.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.hermesmobile.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*

data class Destination(val word: Words, val icon: ImageVector) { val route get()=word.name }
val destinations=listOf(Destination(Words.Home,Icons.Outlined.Home),Destination(Words.Chat,Icons.Outlined.ChatBubbleOutline),Destination(Words.Tasks,Icons.Outlined.CheckCircleOutline),Destination(Words.Automation,Icons.Outlined.Schedule),Destination(Words.Voice,Icons.Outlined.MicNone),Destination(Words.Settings,Icons.Outlined.Tune))
@OptIn(ExperimentalLayoutApi::class)
@Composable fun HermesApp(vm: HermesViewModel,requestedRoute: String? = null,handled: ()->Unit = {},sharedImages:org.hermesmobile.app.data.SharedImages?=null,shareHandled:()->Unit={}) {
    val brand=stringResource(R.string.app_name)
    val nav=rememberNavController()
    val management: ManagementViewModel = viewModel()
    val back by nav.currentBackStackEntryAsState()
    val current=back?.destination?.route ?: "Home"
    val error by vm.error.collectAsStateWithLifecycle()
    val connection by vm.connectionStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val reduced=LocalReducedMotion.current
    val keyboardOpen=WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)>0
    DisposableEffect(nav) {
        val owner=System.identityHashCode(nav)
        UiRouteObservation.record(AppRouteEvent.CompositionEntered,owner,nav.currentDestination?.route)
        val listener=androidx.navigation.NavController.OnDestinationChangedListener {_,destination,_->
            UiRouteObservation.record(AppRouteEvent.RouteChanged,owner,destination.route)
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose {
            nav.removeOnDestinationChangedListener(listener)
            UiRouteObservation.record(AppRouteEvent.CompositionDisposed,owner,nav.currentDestination?.route)
        }
    }
    fun go(route: String) {
        val owner=System.identityHashCode(nav)
        UiRouteObservation.record(AppRouteEvent.NavigationRequested,owner,route)
        nav.navigate(route) { launchSingleTop=true; restoreState=true; popUpTo(nav.graph.startDestinationId) { saveState=true } }
        UiRouteObservation.record(AppRouteEvent.NavigationReturned,owner,nav.currentDestination?.route)
    }
    val navigationReady = back != null
    LaunchedEffect(requestedRoute, navigationReady) {
        if (navigationReady && (requestedRoute == "Tasks" || requestedRoute == "Chat")) {
            go(requestedRoute)
            handled()
        }
    }
    sharedImages?.let {ImageShareReview(vm,it,shareHandled) {go("Chat")}}
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pixelPaper(MaterialTheme.colorScheme.onBackground)) {
        val wide=maxWidth>=840.dp
        val compactChat = current == "Chat" && maxHeight < 620.dp
        val inlineConnection = keyboardOpen || compactChat
        Row(Modifier.fillMaxSize()) {
            if(wide) NavigationRail(
                modifier = Modifier.fillMaxHeight().width(124.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                header = {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher),
                        contentDescription = brand,
                        modifier = Modifier.padding(vertical = 12.dp).size(56.dp).clip(PixelPanelShape)
                    )
                }
            ) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    destinations.forEach { destination ->
                        NavigationRailItem(
                            selected = current == destination.route,
                            onClick = { go(destination.route) },
                            icon = { PixelNavIcon(destination.route, current == destination.route) },
                            label = { Text(label(destination.word)) },
                            colors = NavigationRailItemDefaults.colors(indicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.padding(vertical = 4.dp).testTag("nav-${destination.route.lowercase()}")
                        )
                    }
                }
            }
            Scaffold(modifier=Modifier.weight(1f),containerColor=androidx.compose.ui.graphics.Color.Transparent,
                topBar={ Column(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp)) {
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.padding(end=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {PixelIcon("Home",tint=MaterialTheme.colorScheme.primary);Text(brand,style=if(compactChat) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onBackground)}
                        if(inlineConnection) Icon(if(connection=="connected") Icons.Outlined.CloudDone else if(connection=="demo") Icons.Outlined.SmartToy else Icons.Outlined.CloudOff,label(when(connection) {"demo"->Words.Demo;"connected"->Words.Connected;else->Words.Offline}))
                        Text(org.hermesmobile.app.BuildConfig.VERSION_NAME,style=MaterialTheme.typography.labelSmall,maxLines=1)
                    }
                    if(!inlineConnection) {
                        Spacer(Modifier.height(8.dp))
                        Text(label(when(connection) { "demo"->Words.Demo; "connected"->Words.Connected; else->Words.Offline }),style=MaterialTheme.typography.labelMedium)
                    }
                } },
                bottomBar={ if(!wide && !keyboardOpen) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                    destinations.forEach { d -> NavigationBarItem(modifier=Modifier.testTag("nav-${d.route.lowercase()}"),colors=NavigationBarItemDefaults.colors(indicatorColor=androidx.compose.ui.graphics.Color.Transparent),selected=current==d.route,onClick={go(d.route)},icon={PixelNavIcon(d.route,current==d.route)},label={Text(d.word.en,maxLines=1,style=MaterialTheme.typography.labelSmall)},alwaysShowLabel=current==d.route) }
                } }) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if(error!=null) ErrorBanner(error.orEmpty(),vm::dismissError)
                    if(busy && current!="Chat") TextButton({go("Chat")},Modifier.fillMaxWidth()) { Text(label(Words.Running)) }
                    NavHost(nav,"Home",Modifier.fillMaxSize(),enterTransition={if(reduced) EnterTransition.None else fadeIn(tween(180))+slideInHorizontally(tween(220)) {it/24}},exitTransition={if(reduced) ExitTransition.None else fadeOut(tween(120))},popEnterTransition={if(reduced) EnterTransition.None else fadeIn(tween(160))},popExitTransition={if(reduced) ExitTransition.None else fadeOut(tween(120))+slideOutHorizontally(tween(180)) {it/24}}) {
                        composable("Home") { HomeScreen(vm, management) { go(it) } }
                        composable("Chat") { ChatScreen(vm, management) }
                        composable("Tasks") { TasksScreen(vm) }
                        composable("Automation") { JobsScreen(vm) }
                        composable("Voice") { VoiceScreen(vm) { go("Chat") } }
                        composable("Settings") { SettingsScreen(vm, management) {go("ServerDetails")} }
                        composable("ServerDetails") { ServerDetailsScreen(vm) {go("Settings")} }
                    }
                }
            }
        }
    }
}

@Composable private fun PixelNavIcon(route:String,selected:Boolean) {
    val lift by animateFloatAsState(if(selected) -2f else 0f,if(LocalReducedMotion.current) snap() else spring(dampingRatio=.8f,stiffness=600f),label="navigation-selection")
    val description=label(Words.valueOf(route))
    Box(Modifier.size(32.dp).background(if(selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,PixelPanelShape).semantics {contentDescription=description},contentAlignment=Alignment.Center) {PixelIcon(route,Modifier.graphicsLayer {translationY=lift*density},if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
}
