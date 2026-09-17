package my.github.MrxSiN.modeevolved.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.editor.EditorFacts
import my.github.MrxSiN.modeevolved.editor.GeocoderPlaceSearch
import my.github.MrxSiN.modeevolved.editor.Place
import my.github.MrxSiN.modeevolved.presentation.AreaPresentation
import my.github.MrxSiN.modeevolved.presentation.Distances
import my.github.MrxSiN.modeevolved.trigger.AreaTrigger
import my.github.MrxSiN.modeevolved.trigger.GeoPoint
import my.github.MrxSiN.modeevolved.trigger.Trigger

/** Search for a place or pan a map to the area's center, and set how far from it counts as inside. */
object AreaEditor : KindEditor<Trigger> {

    override val presentation = AreaPresentation

    override val fillsScreen: Boolean = true

    private const val DEFAULT_RADIUS = 150.0
    private const val MIN_RADIUS = 50f
    private const val MAX_RADIUS = 2_000f
    private const val RADIUS_STEP = 10

    @Composable
    override fun Content(facts: EditorFacts, initial: Trigger?, onChange: (Trigger?) -> Unit) {
        val original = initial as? AreaTrigger
        val start = original?.center ?: facts.lastLocation ?: WORLD_CENTER
        var latitude by rememberSaveable { mutableDoubleStateOf(start.latitude) }
        var longitude by rememberSaveable { mutableDoubleStateOf(start.longitude) }
        var radius by rememberSaveable { mutableDoubleStateOf(original?.radiusMeters ?: DEFAULT_RADIUS) }
        var focus by rememberSaveable { mutableStateOf<Pair<Double, Double>?>(null) }
        val center = GeoPoint(latitude, longitude)

        // With no saved area and no known position the map opens on the whole world; nothing is chosen yet.
        val chosen = original != null || facts.lastLocation != null || center != WORLD_CENTER
        LaunchedEffect(center, radius, chosen) { onChange(if (chosen) AreaTrigger(center, radius) else null) }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MapPicker(
                    center = center,
                    radiusMeters = radius,
                    focus = focus?.let { GeoPoint(it.first, it.second) },
                    onCenterChange = {
                        latitude = it.latitude
                        longitude = it.longitude
                    },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
                )
                PlaceFinder(
                    onPick = { focus = it.latitude to it.longitude },
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                )
            }
            RangeCard(
                radius = radius,
                onRadiusChange = { radius = it },
                canRecenter = facts.lastLocation != null,
                onRecenter = { facts.lastLocation?.let { focus = it.latitude to it.longitude } },
            )
        }
    }

    /** A search bar floating over the map, with the places it finds listed under it. */
    @Composable
    private fun PlaceFinder(onPick: (GeoPoint) -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val search = remember(context) { GeocoderPlaceSearch(context.applicationContext) }
        val focusManager = LocalFocusManager.current
        var query by rememberSaveable { mutableStateOf("") }
        var places by remember { mutableStateOf<List<Place>?>(emptyList()) }

        LaunchedEffect(query) {
            val typed = query.trim()
            if (typed.length < MIN_QUERY) {
                places = emptyList()
                return@LaunchedEffect
            }
            delay(SEARCH_DELAY_MILLIS)
            places = null
            places = search.find(typed)
        }

        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, shadowElevation = 3.dp, color = Color.Transparent) {
                SearchField(query, { query = it }, stringResource(R.string.area_search))
            }
            val found = places
            if (query.trim().length >= MIN_QUERY) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().heightIn(max = RESULTS_HEIGHT),
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        when {
                            found == null -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                            found.isEmpty() -> Text(
                                stringResource(R.string.search_no_results, query.trim()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                            else -> found.forEachIndexed { index, place ->
                                SectionRow(
                                    title = place.name,
                                    supporting = place.address,
                                    icon = R.drawable.ic_trigger_area,
                                    position = SectionScope.positionAt(index, found.size),
                                    onClick = {
                                        onPick(place.point)
                                        query = ""
                                        focusManager.clearFocus()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RangeCard(radius: Double, onRadiusChange: (Double) -> Unit, canRecenter: Boolean, onRecenter: () -> Unit) {
        val resources = LocalResources.current
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceBright,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.area_range), style = MaterialTheme.typography.titleMedium)
                        Text(
                            Distances.format(resources, radius),
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (canRecenter) {
                        FilledTonalButton(onClick = onRecenter, shapes = ButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null)
                            Text(stringResource(R.string.area_recenter), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Slider(
                    value = radius.toFloat(),
                    onValueChange = { onRadiusChange(((it / RADIUS_STEP).roundToInt() * RADIUS_STEP).toDouble()) },
                    valueRange = MIN_RADIUS..MAX_RADIUS,
                )
                Text(
                    stringResource(R.string.area_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    private val WORLD_CENTER = GeoPoint(0.0, 0.0)
    private const val MIN_QUERY = 3
    private const val SEARCH_DELAY_MILLIS = 500L
    private val RESULTS_HEIGHT = 320.dp
}
