package com.strataos.launcher.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Strata's stroke icon set: 24 px grid, 1.75 px round stroke. Paths match the design canvas. */
object StrataIcons {
    const val Phone = "M6.5 3.5h3l1.5 4.5-2 1.3a11 11 0 0 0 5.7 5.7l1.3-2 4.5 1.5v3a2 2 0 0 1-2 2A16.5 16.5 0 0 1 4.5 5.5a2 2 0 0 1 2-2z"
    const val Camera = "M3 8h4l2-3h6l2 3h4v11H3z M12 10a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z"
    const val Mic = "M9 6a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0z M5 11a7 7 0 0 0 14 0 M12 18v3"
    const val Location = "M12 21s-7-6.5-7-12a7 7 0 0 1 14 0c0 5.5-7 12-7 12z M9.5 9a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0z"
    const val Calendar = "M4 6h16v14H4z M4 10h16 M8 3v4 M16 3v4"
    const val Battery = "M3 8h15v8H3z M21 11v2"
    const val Charging = "M3 8h15v8H3z M21 11v2 M11 9.5l-2 2.5h3l-2 2.5"
    const val Search = "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0z M20 20l-4-4"
    const val Toggles = "M7 4h10a4 4 0 0 1 0 8H7a4 4 0 0 1 0-8z M14 8a2 2 0 1 0 4 0a2 2 0 1 0-4 0z M7 14h10a4 4 0 0 1 0 8H7a4 4 0 0 1 0-8z M6 18a2 2 0 1 0 4 0a2 2 0 1 0-4 0z"
    const val Home = "M4 11l8-7 8 7v9H4z M10 20v-5h4v5"
    const val Briefcase = "M3 8h18v11H3z M8 8V5h8v3 M3 13h18"
    const val Compass = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M15.5 8.5l-2 5-5 2 2-5z"
    const val Wifi = "M2.5 9a14 14 0 0 1 19 0 M5.5 12.5a9.5 9.5 0 0 1 13 0 M8.5 16a5 5 0 0 1 7 0 M12 19.5v.01"
    const val Data = "M7 20V8 M3 12l4-4 4 4 M17 4v12 M13 12l4 4 4-4"
    const val Bluetooth = "M7 7l10 10-5 4V3l5 4L7 17"
    const val Moon = "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"
    const val Torch = "M8 3h8v4l-2 3v11h-4V10L8 7z M12 13v2"
    const val Rotate = "M4 12a8 8 0 0 1 14-5.3 M20 12a8 8 0 0 1-14 5.3 M18 3v4h-4 M6 21v-4h4"
    const val Plane = "M11.5 2a1.5 1.5 0 0 1 1.5 1.5V9l8 5v2l-8-2.5V18l2.5 2v1.5l-4-1-4 1V20l2.5-2v-4.5L2 16v-2l8-5V3.5A1.5 1.5 0 0 1 11.5 2z"
    const val Leaf = "M5 19c0-8 5-14 15-14c0 10-6 15-14 15 M5 19l7-7"
    const val Cast = "M3 8V5h18v14h-7 M3 19h.01 M3 15a4 4 0 0 1 4 4 M3 11a8 8 0 0 1 8 8"
    const val Vibrate = "M8 4h8v16H8z M4 8v8 M20 8v8 M11 17h2"
    const val Sun = "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4"
    const val AutoBrightness = "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0z M12 2v2 M12 20v2 M2 12h2 M20 12h2 M10.5 13.5l1.5-3.5 1.5 3.5 M11 12.8h2"
    const val Volume = "M4 9h4l5-4v14l-5-4H4z M16 9a4 4 0 0 1 0 6 M18.5 6.5a7.5 7.5 0 0 1 0 11"
    const val Shield = "M12 3l8 3v6c0 5-3.5 8-8 9c-4.5-1-8-4-8-9V6z M9 12l2 2 4-4"
    const val Lock = "M6 11h12v9H6z M8 11V8a4 4 0 0 1 8 0v3"
    const val Settings = "M4 7h10 M18 7h2 M4 17h4 M12 17h8 M16 5v4 M10 15v4"
    const val Bell = "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z M10 21h4"
    const val Grid = "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z"
    const val Files = "M3 6h6l2 2h10v11H3z"
    const val Clock = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7v5l3 2"
    const val Translate = "M4 5h9 M8.5 3v2 M6 5c1 4 3.5 7 6.5 8.5 M11 5c-1 4-3.5 7-6.5 8.5 M13 21l4-9 4 9 M14.5 18h5"
    const val Globe = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M3 12h18 M12 3c3.5 3.5 3.5 14.5 0 18 M12 3c-3.5 3.5-3.5 14.5 0 18"
    const val Plus = "M12 5v14 M5 12h14"
    const val ChevronRight = "M9 6l6 6-6 6"
    const val ChevronLeft = "M15 6l-6 6 6 6"
    const val ChevronDown = "M6 9l6 6 6-6"
    const val Close = "M6 6l12 12 M18 6L6 18"
    const val Info = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v5 M12 8v.01"
    const val Trash = "M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13"
    const val ArrowLeft = "M19 12H5 M11 6l-6 6 6 6"
    const val ArrowRight = "M5 12h14 M13 6l6 6-6 6"
    const val Check = "M5 12l5 5 9-10"
    const val Minus = "M5 12h14"

    private val cache = HashMap<String, ImageVector>()

    fun vector(path: String): ImageVector = cache.getOrPut(path) {
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(path),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}

@Composable
fun StrataIcon(
    path: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = StrataIcons.vector(path),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}
