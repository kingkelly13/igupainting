package com.strataos.remote.ui

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
    const val Power = "M12 3v8 M7 6.5a7 7 0 1 0 10 0"
    const val Mute = "M4 9h4l5-4v14l-5-4H4z M16.5 9.5l5 5 M21.5 9.5l-5 5"
    const val Input = "M14 4h6v16h-6 M3 12h11 M10 8l4 4-4 4"
    const val Tv = "M3 5h18v12H3z M8 20h8"
    const val Box = "M3 8h18v8H3z M7 12h.01 M11 12h6"
    const val Bulb = "M9 18h6 M10 21h4 M12 3a6 6 0 0 1 3.5 10.9V16h-7v-2.1A6 6 0 0 1 12 3z"
    const val Speaker = "M7 3h10v18H7z M9 14a3 3 0 1 0 6 0a3 3 0 1 0-6 0z M12 7v.01"
    const val Projector = "M3 8h18v9H3z M12 12.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0z M6 12h3 M6 17v2 M18 17v2"
    const val Remote = "M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z M12 6.5v.01 M10 11h4 M10 14h4 M10 17h4"
    const val Edit = "M4 20h4L19 9l-4-4L4 16z M13 7l4 4"
    const val More = "M12 5v.01 M12 12v.01 M12 19v.01"
    const val Import = "M12 3v12 M7 10l5 5 5-5 M4 21h16"
    const val Export = "M12 15V3 M7 8l5-5 5 5 M4 21h16"
    const val Play = "M7 4l13 8-13 8z"
    const val Pause = "M8 5v14 M16 5v14"
    const val PlayPause = "M3 5l9 7-9 7z M15 5v14 M20 5v14"
    const val Stop = "M6 6h12v12H6z"
    const val Rewind = "M11 6l-8 6 8 6z M21 6l-8 6 8 6z"
    const val FastForward = "M13 6l8 6-8 6z M3 6l8 6-8 6z"
    const val Record = "M12 6a6 6 0 1 0 0 12a6 6 0 1 0 0-12z"
    const val ChevronUp = "M6 15l6-6 6 6"
    const val Back = "M9 14l-5-5 5-5 M4 9h11a5 5 0 0 1 0 10h-3"
    const val Menu = "M4 7h16 M4 12h16 M4 17h16"
    const val Guide = "M4 5h16v14H4z M4 10h16 M9 10v9"
    const val History = "M4 12a8 8 0 1 0 2.3-5.7 M4 4v4h4"
    const val SunSmall = "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0z M12 5v1 M12 18v1 M5 12h1 M18 12h1"
    const val Sparkle = "M12 3v4 M12 17v4 M3 12h4 M17 12h4 M6 6l2.5 2.5 M15.5 15.5L18 18 M6 18l2.5-2.5 M15.5 8.5L18 6"
    const val Beam = "M8 16a5.5 5.5 0 0 1 8 0 M5 12.5a10 10 0 0 1 14 0 M12 20v.01"
    const val Search = "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0z M20 20l-4-4"
    const val Home = "M4 11l8-7 8 7v9H4z M10 20v-5h4v5"
    const val Sun = "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4"
    const val Volume = "M4 9h4l5-4v14l-5-4H4z M16 9a4 4 0 0 1 0 6 M18.5 6.5a7.5 7.5 0 0 1 0 11"
    const val Settings = "M4 7h10 M18 7h2 M4 17h4 M12 17h8 M16 5v4 M10 15v4"
    const val Grid = "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z"
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
