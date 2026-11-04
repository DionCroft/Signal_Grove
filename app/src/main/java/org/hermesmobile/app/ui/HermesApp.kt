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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*

data class Destination(val word: Words, val icon: ImageVector) { val route get()=word.name }
val destinations=listOf(Destination(Words.Home,Icons.Outlined.Home),Destination(Words.Chat,Icons.Outlined.ChatBubbleOutline),Destination(Words.Tasks,Icons.Outlined.CheckCircleOutline),Destination(Words.Automation,Icons.Outlined.Schedule),Destination(Words.Voice,Icons.Outlined.MicNone),Destination(Words.Settings,Icons.Outlined.Tune))
@Composable fun HermesApp(vm: HermesViewModel,requestedRoute: String? = null,handled: ()->Unit = {},sharedImages:org.hermesmobile.app.data.SharedImages?=null,shareHandled:()->Unit={}) {
    val nav=rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val current=back?.destination?.route ?: "Home"
    val error by vm.error.collectAsStateWithLifecycle()
    val connection by vm.connectionStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val reduced=LocalReducedMotion.current
    val keyboardOpen=WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)>0
    fun go(route: String) { nav.navigate(route) { launchSingleTop=true; restoreState=true; popUpTo(nav.graph.startDestinationId) { saveState=true } } }
    LaunchedEffect(requestedRoute) {if(requestedRoute in setOf("Tasks","Chat")) {go(requestedRoute!!);handled()}}
    sharedImages?.let {ImageShareReview(vm,it,shareHandled) {go("Chat")}}
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pixelPaper(MaterialTheme.colorScheme.onBackground)) {
        val wide=maxWidth>=840.dp
        Row(Modifier.fillMaxSize()) {
            if(wide) NavigationRail(Modifier.fillMaxHeight().width(124.dp),containerColor=MaterialTheme.colorScheme.surface,header={ Text("H",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.padding(20.dp)) }) {
                destinations.forEach { d -> NavigationRailItem(colors=NavigationRailItemDefaults.colors(indicatorColor=androidx.compose.ui.graphics.Color.Transparent),selected=current==d.route,onClick={go(d.route)},icon={PixelNavIcon(d.route,current==d.route)},label={Text(label(d.word))},modifier=Modifier.padding(vertical=4.dp)) }
            }
            Scaffold(modifier=Modifier.weight(1f),containerColor=androidx.compose.ui.graphics.Color.Transparent,
                topBar={ Column(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {PixelIcon("Home",tint=MaterialTheme.colorScheme.primary);Text("HERMES",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onBackground)}
                        if(keyboardOpen) Icon(if(connection=="connected") Icons.Outlined.CloudDone else if(connection=="demo") Icons.Outlined.SmartToy else Icons.Outlined.CloudOff,label(when(connection) {"demo"->Words.Demo;"connected"->Words.Connected;else->Words.Offline}))
                        Text(org.hermesmobile.app.BuildConfig.VERSION_NAME,style=MaterialTheme.typography.labelSmall,maxLines=1)
                    }
                    if(!keyboardOpen) {
                        Spacer(Modifier.height(8.dp))
                        Text(label(when(connection) { "demo"->Words.Demo; "connected"->Words.Connected; else->Words.Offline }),style=MaterialTheme.typography.labelMedium)
                    }
                } },
                bottomBar={ if(!wide && !keyboardOpen) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                    destinations.forEach { d -> NavigationBarItem(colors=NavigationBarItemDefaults.colors(indicatorColor=androidx.compose.ui.graphics.Color.Transparent),selected=current==d.route,onClick={go(d.route)},icon={PixelNavIcon(d.route,current==d.route)},label={Text(d.word.en,maxLines=1,style=MaterialTheme.typography.labelSmall)},alwaysShowLabel=current==d.route) }
                } }) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if(error!=null) Surface(color=MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.fillMaxWidth().padding(start=16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(error.orEmpty(),Modifier.weight(1f),color=MaterialTheme.colorScheme.onErrorContainer,style=MaterialTheme.typography.bodyMedium)
                            IconButton(vm::dismissError) { Icon(Icons.Outlined.Close,label(Words.Dismiss),tint=MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                    if(busy && current!="Chat") TextButton({go("Chat")},Modifier.fillMaxWidth()) { Text(label(Words.Running)) }
                    NavHost(nav,"Home",Modifier.fillMaxSize(),enterTransition={if(reduced) EnterTransition.None else fadeIn(tween(180))+slideInHorizontally(tween(220)) {it/24}},exitTransition={if(reduced) ExitTransition.None else fadeOut(tween(120))},popEnterTransition={if(reduced) EnterTransition.None else fadeIn(tween(160))},popExitTransition={if(reduced) ExitTransition.None else fadeOut(tween(120))+slideOutHorizontally(tween(180)) {it/24}}) {
                        composable("Home") { HomeScreen(vm) { go(it) } }
                        composable("Chat") { ChatScreen(vm) }
                        composable("Tasks") { TasksScreen(vm) }
                        composable("Automation") { JobsScreen(vm) }
                        composable("Voice") { VoiceScreen(vm) { go("Chat") } }
                        composable("Settings") { SettingsScreen(vm) {go("ServerDetails")} }
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
