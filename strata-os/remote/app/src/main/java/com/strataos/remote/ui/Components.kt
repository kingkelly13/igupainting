package com.strataos.remote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * What a key on screen needs from the remote: whether it has a code, and what to do when it is
 * pressed and let go. Reads state at call time, so one instance can serve a whole layout.
 */
interface KeyHost {
    val editing: Boolean
    fun has(key: String): Boolean
    fun isPressed(key: String): Boolean
    fun down(key: String)
    fun up(key: String)
}

/** Press-and-hold gesture: [onDown] when the finger lands, [onUp] when it lifts or slides off. */
fun Modifier.holdable(key: Any, onDown: () -> Unit, onUp: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(key) {
        detectTapGestures(onPress = {
            onDown()
            tryAwaitRelease()
            onUp()
        })
    },
)

/**
 * One remote button. Pressed, it fills with the accent colour: the code is going out. Keys with
 * no code are dimmed; in edit mode every key gets an accent outline and opens the editor.
 */
@Composable
fun RemoteKey(
    key: String,
    host: KeyHost,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: String? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    swatch: Color? = null,
    /** Send on touch-down and repeat while held. Off for keys inside scrolling lists. */
    hold: Boolean = true,
    iconSize: Dp = 22.dp,
    fontSize: Int = 14,
) {
    val accent = LocalAccent.current
    var down by remember { mutableStateOf(false) }
    val currentHost by rememberUpdatedState(host)
    val lit = down || host.isPressed(key)
    val available = host.has(key)
    val background = when {
        lit -> accent
        swatch != null -> swatch
        else -> StrataColors.Surface
    }
    val content = when {
        lit -> StrataColors.OnAccent
        swatch != null -> if (swatch.luminance() > 0.55f) StrataColors.Ink else Color.White
        else -> StrataColors.Text
    }
    val border = if (host.editing) accent else StrataColors.Line
    val gesture = if (hold) {
        Modifier.holdable(key, onDown = { down = true; currentHost.down(key) }, onUp = { down = false; currentHost.up(key) })
    } else {
        Modifier.clickable { currentHost.down(key); currentHost.up(key) }
    }
    Box(
        modifier
            .alpha(if (available || host.editing) 1f else 0.32f)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .then(gesture)
            .semantics {
                role = Role.Button
                contentDescription = label ?: key
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null && label != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StrataIcon(icon, content, size = iconSize)
                Text(label, color = content, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else if (icon != null) {
            StrataIcon(icon, content, size = iconSize)
        } else if (label != null) {
            Text(
                label,
                color = content,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** A tall two-way key, like a remote's volume or channel rocker. */
@Composable
fun Rocker(
    upKey: String,
    downKey: String,
    label: String,
    host: KeyHost,
    modifier: Modifier = Modifier,
    upIcon: String = StrataIcons.Plus,
    downIcon: String = StrataIcons.Minus,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(32.dp)
    val currentHost by rememberUpdatedState(host)
    Column(
        modifier
            .width(64.dp)
            .clip(shape)
            .background(StrataColors.Surface)
            .border(1.dp, if (host.editing) accent else StrataColors.Line, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for ((key, icon) in listOf(upKey to upIcon, downKey to downIcon)) {
            var down by remember { mutableStateOf(false) }
            val lit = down || host.isPressed(key)
            val available = host.has(key) || host.editing
            if (key == downKey) {
                Text(label, color = StrataColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (lit) accent else Color.Transparent)
                    .holdable(key, onDown = { down = true; currentHost.down(key) }, onUp = { down = false; currentHost.up(key) })
                    .semantics {
                        role = Role.Button
                        contentDescription = "$label ${if (key == upKey) "up" else "down"}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                StrataIcon(
                    icon,
                    if (lit) StrataColors.OnAccent else StrataColors.Text,
                    modifier = Modifier.alpha(if (available) 1f else 0.32f),
                    size = 24.dp,
                )
            }
        }
    }
}

/** The direction pad: four arrows around a centre OK, one touch target with five zones. */
@Composable
fun DPad(host: KeyHost, modifier: Modifier = Modifier, size: Dp = 220.dp) {
    val accent = LocalAccent.current
    val currentHost by rememberUpdatedState(host)
    var zone by remember { mutableStateOf<String?>(null) }
    val litZone = zone ?: listOf("up", "down", "left", "right", "ok").firstOrNull { host.isPressed(it) }
    Box(
        modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { position ->
                    val area = this@pointerInput.size
                    val r = min(area.width, area.height) / 2f
                    val dx = position.x - area.width / 2f
                    val dy = position.y - area.height / 2f
                    val distance = hypot(dx, dy)
                    if (distance <= r) {
                        val angle = Math.toDegrees(atan2(dy, dx).toDouble())
                        val hit = when {
                            distance < r * 0.38f -> "ok"
                            angle in -45.0..45.0 -> "right"
                            angle in 45.0..135.0 -> "down"
                            angle in -135.0..-45.0 -> "up"
                            else -> "left"
                        }
                        zone = hit
                        currentHost.down(hit)
                        tryAwaitRelease()
                        currentHost.up(hit)
                        zone = null
                    }
                })
            }
            .semantics { contentDescription = "Direction pad" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(StrataColors.Surface, r, center)
            val sweepStart = when (litZone) {
                "right" -> -45f
                "down" -> 45f
                "left" -> 135f
                "up" -> 225f
                else -> null
            }
            if (sweepStart != null) {
                drawArc(
                    accent,
                    startAngle = sweepStart,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(2 * r, 2 * r),
                )
            }
            drawCircle(if (host.editing) accent else StrataColors.Line, r - 0.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            drawCircle(if (litZone == "ok") accent else StrataColors.Raised, r * 0.36f, center)
            drawCircle(StrataColors.Line, r * 0.36f, center, style = Stroke(1.dp.toPx()))
        }
        fun tint(key: String) = when {
            litZone == key && key != "ok" -> StrataColors.OnAccent
            host.has(key) || host.editing -> StrataColors.Text
            else -> StrataColors.LineStrong
        }
        val inset = size * 0.08f
        StrataIcon(StrataIcons.ChevronUp, tint("up"), Modifier.align(Alignment.TopCenter).padding(top = inset), size = 26.dp)
        StrataIcon(StrataIcons.ChevronDown, tint("down"), Modifier.align(Alignment.BottomCenter).padding(bottom = inset), size = 26.dp)
        StrataIcon(StrataIcons.ChevronLeft, tint("left"), Modifier.align(Alignment.CenterStart).padding(start = inset), size = 26.dp)
        StrataIcon(StrataIcons.ChevronRight, tint("right"), Modifier.align(Alignment.CenterEnd).padding(end = inset), size = 26.dp)
        Text(
            "OK",
            color = when {
                litZone == "ok" -> StrataColors.OnAccent
                host.has("ok") || host.editing -> StrataColors.Text
                else -> StrataColors.LineStrong
            },
            style = displayStyle(20.sp, FontWeight.SemiBold),
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

/** A full-width action. [prominent] is the main action on a screen; it is never accent-coloured. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    prominent: Boolean = false,
    enabled: Boolean = true,
) {
    val background = if (prominent) StrataColors.Text else StrataColors.Raised
    val content = if (prominent) StrataColors.Ink else StrataColors.Text
    Row(
        modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            StrataIcon(icon, content, size = 20.dp)
            Box(Modifier.width(10.dp))
        }
        Text(text, color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** A round icon-only button for top bars. */
@Composable
fun IconAction(icon: String, description: String, onClick: () -> Unit, tint: Color = StrataColors.Text) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        StrataIcon(icon, tint, size = 22.dp)
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconAction(StrataIcons.ChevronLeft, "Back", onBack)
        Text(
            title,
            style = displayStyle(24.sp, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 4.dp else 2.dp),
        )
        actions()
    }
}

/** Segmented control. The selected segment is accent-filled: it is the current state. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(StrataColors.Surface)
            .border(1.dp, StrataColors.Line, CircleShape)
            .padding(4.dp),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(if (on) accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) StrataColors.OnAccent else StrataColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** A bottom sheet over a scrim. It rises with the keyboard and scrolls when it has to. */
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Box(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(shape)
                .background(StrataColors.Sheet)
                .border(1.dp, StrataColors.Line, shape)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            content = content,
        )
    }
}
