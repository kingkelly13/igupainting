package com.strataos.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strataos.launcher.LauncherModel
import com.strataos.launcher.Space
import com.strataos.launcher.data.AppEntry

/** The strata wallpaper: five bands and the accent line, scaled from the 390 x 844 design frame. */
@Composable
fun StrataWallpaper(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val sx = size.width / 390f
        val sy = size.height / 844f
        fun edge(p: FloatArray): Path = Path().apply {
            moveTo(0f, p[0] * sy)
            cubicTo(p[1] * sx, p[2] * sy, p[3] * sx, p[4] * sy, p[5] * sx, p[6] * sy)
            cubicTo(p[7] * sx, p[8] * sy, p[9] * sx, p[10] * sy, p[11] * sx, p[12] * sy)
        }
        WallpaperBands.forEachIndexed { i, points ->
            val band = edge(points).apply {
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(band, StrataColors.Bands[i])
            if (i == 2) {
                drawPath(edge(points), accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

private val WallpaperBands = listOf(
    floatArrayOf(470f, 80f, 445f, 150f, 500f, 230f, 480f, 310f, 460f, 350f, 440f, 390f, 455f),
    floatArrayOf(560f, 70f, 540f, 160f, 590f, 250f, 565f, 340f, 540f, 360f, 530f, 390f, 545f),
    floatArrayOf(640f, 90f, 615f, 170f, 668f, 265f, 642f, 360f, 616f, 365f, 612f, 390f, 624f),
    floatArrayOf(728f, 100f, 708f, 180f, 752f, 280f, 730f, 380f, 708f, 370f, 706f, 390f, 714f),
    floatArrayOf(796f, 110f, 780f, 190f, 815f, 290f, 798f, 390f, 781f, 372f, 782f, 390f, 788f),
)

/** Makes a full-screen overlay the touch target, so taps never reach the home screen beneath. */
fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * An app icon in Strata's style. With monochrome on, apps that ship a themed icon get it drawn
 * in the text colour on a graphite tile; the rest are shown in greyscale.
 */
@Composable
fun AppIcon(app: AppEntry, mono: Boolean, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val shape = RoundedCornerShape(size * 0.32f)
    val monoIcon = app.monoIcon
    if (mono && monoIcon != null) {
        Box(
            modifier
                .size(size)
                .clip(shape)
                .background(StrataColors.Surface)
                .border(1.dp, StrataColors.Line, shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = monoIcon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(StrataColors.Text),
                modifier = Modifier.size(size * 0.72f),
            )
        }
    } else {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            colorFilter = if (mono) Grayscale else null,
            modifier = modifier.size(size),
        )
    }
}

/** On/off pill. Purely visual: the row that holds it handles the click and semantics. */
@Composable
fun StrataSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .background(if (checked) accent else StrataColors.Line)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (checked) StrataColors.OnAccent else StrataColors.Muted),
        )
    }
}

@Composable
fun StrataCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(StrataColors.Surface)
            .border(1.dp, StrataColors.Line, shape),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = StrataColors.Muted,
        fontSize = 13.sp,
        modifier = modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** The long-press menu shared by the home grid and All apps. */
@Composable
fun AppMenu(
    model: LauncherModel,
    app: AppEntry,
    expanded: Boolean,
    onDismiss: () -> Unit,
    currentSpace: Space? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = app.label,
            color = StrataColors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (currentSpace != null) {
            val keys = model.appsIn(currentSpace)
            val index = keys.indexOfFirst { it.key == app.key }
            if (index > 0) {
                MenuItem("Move left", StrataIcons.ArrowLeft) { model.move(currentSpace, app, -1); onDismiss() }
            }
            if (index in 0 until keys.lastIndex) {
                MenuItem("Move right", StrataIcons.ArrowRight) { model.move(currentSpace, app, 1); onDismiss() }
            }
        }
        for (space in Space.entries) {
            val inSpace = model.isIn(space, app)
            MenuItem(
                label = if (inSpace) "Remove from ${space.label}" else "Add to ${space.label}",
                icon = if (inSpace) StrataIcons.Minus else StrataIcons.Plus,
            ) {
                model.toggleMembership(space, app)
                onDismiss()
            }
        }
        MenuItem("App info", StrataIcons.Info) { model.repo.openAppInfo(app); onDismiss() }
        MenuItem("Uninstall", StrataIcons.Trash) { model.repo.uninstall(app); onDismiss() }
    }
}

@Composable
private fun MenuItem(label: String, icon: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp) },
        leadingIcon = { StrataIcon(icon, StrataColors.Text, size = 18.dp) },
        onClick = onClick,
    )
}
