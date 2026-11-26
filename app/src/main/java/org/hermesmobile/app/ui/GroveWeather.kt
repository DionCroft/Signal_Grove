package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.hermesmobile.app.data.GroveWeatherArea
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val WeatherRefresh = 30 * 60_000L
private const val WeatherRetry = 5 * 60_000L
private const val WeatherCredits = "https://open-meteo.com/en/licence"

internal fun groveWeatherCode(code: Int): GroveSky? = when (code) {
    0 -> GroveSky.CLEAR
    1, 2, 3 -> GroveSky.CLOUDY
    45, 48 -> GroveSky.FOG
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> GroveSky.RAIN
    71, 73, 75, 77, 85, 86 -> GroveSky.SNOW
    95, 96, 99 -> GroveSky.STORM
    else -> null
}

internal fun forecastUrl(area: GroveWeatherArea): HttpUrl = "https://api.open-meteo.com/v1/forecast".toHttpUrl()
    .newBuilder()
    .addQueryParameter("latitude", area.latitude.toString())
    .addQueryParameter("longitude", area.longitude.toString())
    .addQueryParameter("current", "weather_code")
    .addQueryParameter("forecast_days", "1")
    .build()

private object GroveWeatherService {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile private var cached: Reading? = null

    suspend fun towns(query: String): List<GroveWeatherArea>? {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("count", "5")
            .addQueryParameter("language", "en")
            .build()
        val row = json(url) ?: return null
        return (row["results"] as? JsonArray).orEmpty().take(5).mapNotNull { element ->
            val town = element as? JsonObject ?: return@mapNotNull null
            val name = (town["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val region = (town["admin1"] as? JsonPrimitive)?.contentOrNull
            val country = (town["country"] as? JsonPrimitive)?.contentOrNull
            val lat = (town["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lon = (town["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            GroveWeatherArea.create(listOfNotNull(name, region, country).distinct().joinToString(", "), lat, lon)
        }.distinct()
    }

    suspend fun conditions(area: GroveWeatherArea): GroveSky? {
        val now = android.os.SystemClock.elapsedRealtime()
        cached?.takeIf { it.area == area && now - it.checkedAt < (if (it.sky == null) WeatherRetry else WeatherRefresh) }?.let { return it.sky }
        val current = json(forecastUrl(area))?.get("current") as? JsonObject
        val sky = (current?.get("weather_code") as? JsonPrimitive)?.intOrNull?.let(::groveWeatherCode)
        cached = Reading(area, sky, android.os.SystemClock.elapsedRealtime())
        return sky
    }

    fun clear() { cached = null }

    private data class Reading(val area: GroveWeatherArea, val sky: GroveSky?, val checkedAt: Long)

    private suspend fun json(url: HttpUrl): JsonObject? = suspendCancellableCoroutine { waiter ->
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
        waiter.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = waiter.resume(null)

            override fun onResponse(call: Call, response: Response) {
                val row = response.use {
                    if (!it.isSuccessful) return@use null
                    runCatching {
                        val bytes = it.peekBody(65_537).bytes()
                        if (bytes.size > 65_536) null
                        else Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
                    }.getOrNull()
                }
                waiter.resume(row)
            }
        })
    }
}

@Composable
internal fun GroveHomeArt(vm: HermesViewModel) {
    val area by vm.groveWeatherArea.collectAsStateWithLifecycle(initialValue = null)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    var showWeather by remember { mutableStateOf(false) }
    val forecast by produceState<Pair<GroveWeatherArea, GroveSky?>?>(null, area, visible) {
        val town = area ?: return@produceState
        while (visible) {
            val conditions = GroveWeatherService.conditions(town)
            value = town to conditions
            delay(if (conditions == null) WeatherRetry else WeatherRefresh)
        }
    }
    val sky = forecast?.takeIf { it.first == area }?.second
    val uri = LocalUriHandler.current
    Box {
        PixelHomestead(latitude = area?.latitude, sky = sky)
        if (sky != null) {
            FilledTonalButton(
                onClick = { uri.openUri(WeatherCredits) },
                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
                shape = PixelPanelShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) { Text("Open-Meteo") }
        }
        FilledTonalIconButton(
            onClick = { showWeather = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).testTag("grove-weather-settings"),
            shape = PixelPanelShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(Icons.Outlined.Cloud, label(Words.GroveWeather))
        }
    }
    if (showWeather) GroveWeatherDialog(area, sky, onClose = { showWeather = false }) {
        GroveWeatherService.clear()
        vm.setGroveWeatherArea(it)
        showWeather = false
    }
}

@Composable
private fun GroveWeatherDialog(
    area: GroveWeatherArea?,
    sky: GroveSky?,
    onClose: () -> Unit,
    select: (GroveWeatherArea?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<List<GroveWeatherArea>?>(null) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clean = query.trim()
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(label(Words.GroveWeather)) },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label(Words.GroveWeatherPrivacy))
                area?.let {
                    Text(it.name)
                    if (sky == null) Text(label(Words.GroveWeatherFallback))
                    TextButton(onClick = { select(null) }) { Text(label(Words.GroveWeatherLocal)) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it.take(80).filterNot { character -> character.isISOControl() }
                        searched = false
                        result = null
                    },
                    label = { Text(label(Words.GroveWeatherTown)) },
                    singleLine = true,
                    enabled = !busy
                )
                TextButton(
                    enabled = clean.length >= 2 && !busy,
                    onClick = {
                        busy = true
                        result = null
                        searched = false
                        scope.launch {
                            try {
                                result = GroveWeatherService.towns(clean)
                                searched = true
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(label(if (busy) Words.Loading else Words.GroveWeatherSearch)) }
                if (searched && result.isNullOrEmpty()) {
                    Text(label(if (result == null) Words.GroveWeatherUnavailable else Words.GroveWeatherNoResults))
                }
                if (!result.isNullOrEmpty() || area != null) {
                    TextButton(onClick = { uri.openUri(WeatherCredits) }) { Text(label(Words.GroveWeatherSource)) }
                }
                result.orEmpty().forEach { town ->
                    TextButton(onClick = { select(town) }) { Text(town.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(label(Words.Close)) } }
    )
}
