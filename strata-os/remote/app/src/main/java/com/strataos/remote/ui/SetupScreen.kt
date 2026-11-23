package com.strataos.remote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strataos.remote.RemoteModel
import com.strataos.remote.SetupStep
import com.strataos.remote.data.CodeLibrary
import com.strataos.remote.data.DeviceKind

/** Add a remote: what it is, which brand, find a working code, name it. */
@Composable
fun SetupScreen(model: RemoteModel) {
    val pickFile = rememberIrFilePicker(
        onFile = { text, name -> model.importFlipper(text, name)?.let { model.report(it, error = true) } },
        onError = { model.report(it, error = true) },
    )
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        when (model.setup.step) {
            SetupStep.Kind -> KindStep(model)
            SetupStep.Brand -> BrandStep(model, pickFile)
            SetupStep.Test -> TestStep(model, pickFile)
            SetupStep.Name -> NameStep(model)
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Text(title, style = displayStyle(34.sp), modifier = Modifier.padding(start = 4.dp, top = 12.dp))
    Text(
        body,
        color = StrataColors.Muted,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
    )
}

@Composable
private fun SetupStatus(model: RemoteModel) {
    val status = model.status ?: return
    Row(Modifier.padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        StrataIcon(if (model.statusIsError) StrataIcons.Info else StrataIcons.Beam, StrataColors.Muted, size = 16.dp)
        Text(status, color = StrataColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun ColumnScope.KindStep(model: RemoteModel) {
    TopBar("Add a remote", onBack = { model.back() })
    Heading("What are you controlling?", "Point the top of your phone, where the IR blaster is, at it while you set up.")
    Spacer(Modifier.weight(1f))
    // Tiles sit low on the screen, in thumb reach.
    val kinds = DeviceKind.entries
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        for (row in kinds.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (kind in row) {
                    KindTile(kind, Modifier.weight(1f)) { model.chooseKind(kind) }
                }
            }
        }
    }
}

@Composable
private fun KindTile(kind: DeviceKind, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val built = CodeLibrary.forKind(kind).size
    Column(
        modifier
            .aspectRatio(1.25f)
            .clip(shape)
            .background(StrataColors.Surface)
            .border(1.dp, StrataColors.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(StrataColors.Raised),
            contentAlignment = Alignment.Center,
        ) {
            StrataIcon(kindIcon(kind), StrataColors.Text, size = 22.dp)
        }
        Spacer(Modifier.weight(1f))
        Text(kind.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            when (built) {
                0 -> "Import or add codes"
                1 -> "1 code set built in"
                else -> "$built code sets built in"
            },
            color = StrataColors.Muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ColumnScope.BrandStep(model: RemoteModel, pickFile: () -> Unit) {
    val kind = model.setup.kind
    val sets = model.setupCodeSets
    TopBar(kind.label, onBack = { model.back() })
    Heading(
        if (sets.isEmpty()) "Where are the codes?" else "Which brand?",
        if (sets.isEmpty()) {
            "There are no built-in codes for this kind of device yet. Import a Flipper .ir file for it, or type the codes in."
        } else {
            "You'll test a code next, so have the ${kind.noun} in front of you."
        },
    )
    SetupStatus(model)
    LazyColumn(
        Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sets, key = { it.id }) { set ->
            ChoiceRow(set.brand, set.note, null) { model.tryCodeSets(listOf(set)) }
        }
        if (sets.size > 1) {
            item {
                ChoiceRow(
                    "Not sure? Try them all",
                    "Tests the power code of all ${sets.size} sets, one at a time",
                    StrataIcons.Search,
                ) { model.tryCodeSets(sets) }
            }
        }
        item { SectionLabel("Other ways") }
        item {
            ChoiceRow(
                "Import a Flipper .ir file",
                "Thousands of remotes are shared in this format, for example in the Flipper-IRDB collection",
                StrataIcons.Import,
                pickFile,
            )
        }
        item {
            ChoiceRow("Start with an empty remote", "Add each button's code yourself", StrataIcons.Edit) { model.startEmpty() }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, icon: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(StrataColors.Surface)
            .border(1.dp, StrataColors.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            StrataIcon(icon, StrataColors.Text, size = 22.dp)
            Spacer(Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = StrataColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        StrataIcon(StrataIcons.ChevronRight, StrataColors.Muted, size = 18.dp)
    }
}

@Composable
private fun ColumnScope.TestStep(model: RemoteModel, pickFile: () -> Unit) {
    val setup = model.setup
    val set = setup.current ?: return
    val kind = setup.kind
    val view = LocalView.current
    TopBar("Find the code", onBack = { model.back() })

    if (setup.exhausted) {
        Heading(
            "None of those worked",
            "Your ${kind.noun} may use a code that isn't built in yet. Import a Flipper .ir file for its model, " +
                "or start an empty remote and type the codes in.",
        )
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            PillButton("Import a Flipper .ir file", pickFile, Modifier.fillMaxWidth(), icon = StrataIcons.Import, prominent = true)
            PillButton("Start with an empty remote", { model.startEmpty() }, Modifier.fillMaxWidth(), icon = StrataIcons.Edit)
            PillButton("Choose another brand", { model.back() }, Modifier.fillMaxWidth())
        }
        return
    }

    val test = set.testButton
    val confirm = set.confirmButton
    Heading(
        "Point your phone at the ${kind.noun}",
        if (setup.candidates.size > 1) {
            "Code ${setup.index + 1} of ${setup.candidates.size} · ${set.brand}. Tap ${test.label}, then say whether it responded."
        } else {
            "${set.brand}: ${set.note}. Tap ${test.label}, then say whether it responded."
        },
    )
    SetupStatus(model)
    Spacer(Modifier.weight(1f))

    // The test button: big, centred, low.
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(if (setup.tried) StrataColors.Raised else StrataColors.Surface)
                .border(2.dp, if (setup.tried) accent else StrataColors.LineStrong, CircleShape)
                .clickable(role = Role.Button) {
                    if (model.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    model.testCurrent(confirm = false)
                }
                .semantics { contentDescription = "Send ${test.label}" },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StrataIcon(if (test.key.startsWith("power")) StrataIcons.Power else StrataIcons.Beam, StrataColors.Text, size = 40.dp)
                Text(test.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (confirm != null) {
            Text(
                "Already off? Send ${confirm.label} instead",
                color = StrataColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { model.testCurrent(confirm = true) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        if (setup.tried) {
            Text(
                "Did your ${kind.noun} respond?",
                style = displayStyle(20.sp, FontWeight.SemiBold),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }
        PillButton(
            "Yes, it worked",
            { model.acceptCandidate() },
            Modifier.fillMaxWidth(),
            icon = StrataIcons.Check,
            prominent = true,
            enabled = setup.tried,
        )
        PillButton(
            if (setup.index + 1 < setup.candidates.size) "No, try the next code" else "No, it didn't",
            { model.nextCandidate() },
            Modifier.fillMaxWidth(),
            enabled = setup.tried,
        )
    }
}

@Composable
private fun ColumnScope.NameStep(model: RemoteModel) {
    val setup = model.setup
    TopBar("Name it", onBack = { model.back() })
    val count = setup.buttons.size
    Heading(
        "Almost done",
        setup.note ?: "$count ${if (count == 1) "button is" else "buttons are"} ready. You can change any of them later with Edit.",
    )
    OutlinedTextField(
        value = setup.name,
        onValueChange = { setup.name = it },
        label = { Text("Name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (setup.name.isNotBlank()) model.finishSetup() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
    )
    Spacer(Modifier.weight(1f))
    PillButton(
        "Save remote",
        { model.finishSetup() },
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        icon = StrataIcons.Check,
        prominent = true,
        enabled = setup.name.isNotBlank(),
    )
}
