package my.github.MrxSiN.modeevolved.editor.ui

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/**
 * How the map is colored over its one base style.
 *
 * The base is OpenFreeMap's Positron, whose layer ids this relies on. Water always takes the
 * wallpaper color. [Night] repaints the rest in the soft dark greys of Google Maps' dark mode,
 * so land, roads and labels stay apart instead of sinking into black.
 */
internal sealed interface MapTheme {

    fun applyTo(style: Style, water: Int) {
        for (layer in style.layers) {
            if (layer !is SymbolLayer && layer.id.startsWith(WATER)) paint(layer, water) else recolor(layer)
        }
    }

    /** Repaints one layer that is not a water shape. */
    fun recolor(layer: Layer)

    /** Positron as it is. */
    data object Day : MapTheme {
        override fun recolor(layer: Layer) = Unit
    }

    data object Night : MapTheme {

        override fun recolor(layer: Layer) {
            if (layer is SymbolLayer) {
                layer.setProperties(PropertyFactory.textColor(labelColor(layer.id)), PropertyFactory.textHaloColor(HALO))
                return
            }
            val color = SHAPES.firstOrNull { (part, _) -> part in layer.id }?.second ?: return
            paint(layer, color)
            if (layer is FillLayer) layer.setProperties(PropertyFactory.fillOutlineColor(color))
        }

        private fun labelColor(id: String): Int = when {
            id.startsWith(WATER) -> WATER_LABEL
            id.startsWith("highway") || id.startsWith("road") -> ROAD_LABEL
            MAJOR_PLACES.any(id::startsWith) -> PLACE_LABEL
            else -> MINOR_LABEL
        }

        /** Checked in order, so a casing or dashline is matched before the road or railway it belongs to. */
        private val SHAPES = listOf(
            "background" to 0xFF2B2D31,
            "wood" to 0xFF27352D,
            "park" to 0xFF27352D,
            "residential" to 0xFF2F3136,
            "landcover" to 0xFF3A3D42,
            "building" to 0xFF383B40,
            "aeroway-area" to 0xFF35383D,
            "casing" to 0xFF232427,
            "dashline" to 0xFF2B2D31,
            "pier" to 0xFF2B2D31,
            "aeroway" to 0xFF4A4E54,
            "path" to 0xFF3A3D42,
            "minor" to 0xFF43464C,
            "major" to 0xFF50545A,
            "motorway" to 0xFF62666C,
            "railway" to 0xFF4A4E54,
            "boundary" to 0xFF6B6F75,
        ).map { (part, color) -> part to color.toInt() }

        private val MAJOR_PLACES = listOf("label_city", "label_town", "label_village", "label_country")

        private val WATER_LABEL = 0xFF8AB4F8.toInt()
        private val ROAD_LABEL = 0xFF9AA0A6.toInt()
        private val PLACE_LABEL = 0xFFE8EAED.toInt()
        private val MINOR_LABEL = 0xFFBDC1C6.toInt()
        private val HALO = 0xCC202124.toInt()
    }

    companion object {
        /** OpenFreeMap's Positron: OpenStreetMap vector tiles, free to use without a key. */
        const val STYLE = "https://tiles.openfreemap.org/styles/positron"

        /** Water areas, rivers and their labels all start with this. */
        private const val WATER = "water"

        fun of(dark: Boolean): MapTheme = if (dark) Night else Day

        private fun paint(layer: Layer, color: Int) {
            when (layer) {
                is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(color))
                is FillLayer -> layer.setProperties(PropertyFactory.fillColor(color))
                is LineLayer -> layer.setProperties(PropertyFactory.lineColor(color))
                else -> Unit
            }
        }
    }
}
