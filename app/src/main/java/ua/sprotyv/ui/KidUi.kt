package ua.sprotyv.ui

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.ui.semantics.liveRegion

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics

import androidx.compose.foundation.background

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.sprotyv.R
import ua.sprotyv.content.Card as ActionCard
import ua.sprotyv.content.Content
import ua.sprotyv.content.Service
import ua.sprotyv.home.Group
import ua.sprotyv.reach.Reach

// The child's look (6-10 years). Built on what helps young children act in an emergency: one
// picture per situation (a child reads the picture before the word), seven choices at most, very
// short steps one at a time, the same rules they practise as a game (guide 22.1), a calm tone and
// a grown-up always one tap away. No settings within reach: those sit behind the phone's PIN.

/** A picture for each card: emoji are colourful, known to children and drawn by the phone itself. */
internal fun picture(id: String): String = when (id) {
    "kid-siren" -> "🚨"
    "kid-buzz" -> "🛸"
    "kid-strange" -> "✋"
    "kid-lost" -> "🧍"
    "kid-fire" -> "🔥"
    "kid-scared" -> "🧸"
    "kid-code" -> "🔑"
    else -> "⭐"
}

/** The same pictures drawn by OpenMoji (CC BY-SA 4.0): the same look on every phone. */
internal fun pictureRes(id: String): Int = when (id) {
    "kid-siren" -> R.drawable.om_siren
    "kid-buzz" -> R.drawable.hi_drone
    "kid-strange" -> R.drawable.om_hand
    "kid-lost" -> R.drawable.om_person
    "kid-fire" -> R.drawable.om_fire
    "kid-scared" -> R.drawable.om_teddy
    "kid-code" -> R.drawable.om_key
    else -> R.drawable.om_star
}

/** Soft colours from the group palette, one per tile, so each is found by colour too. */
/** Each card's colour by what it is about: danger orange, family blue, comfort green. */
private fun kidColour(id: String): Group = when (id) {
    "kid-siren", "kid-buzz", "kid-strange", "kid-fire" -> Group.DANGER
    "kid-scared" -> Group.FIRST_AID
    else -> Group.FAMILY
}

@Composable
fun KidHome(content: Content?, nav: Nav, onGrownUp: () -> Unit) {
    val cards = content?.kidCards.orEmpty()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    // Small and plain on purpose: for the grown-up, behind the phone's PIN
                    TextButton(onClick = onGrownUp, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text(stringResource(R.string.kid_grown_up)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Sunny(64.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.kid_hello), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.kid_hello_hint), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (content != null && content.kidCardsBroken) item(span = { GridItemSpan(maxLineSpan) }) { Broken() }
            // Seven situations; the last one spans the row, so nothing looks missing
            items(cards.take(7), key = { it.id }, span = { c -> GridItemSpan(if (cards.indexOf(c) == 6) maxLineSpan else 1) }) { card ->
                val i = cards.indexOf(card)
                KidTile(card, kidColour(card.id).tint()) { nav.go(Route.CardRoute(card.id)) }
            }
        }
        // Trip mode hides the family numbers (encrypted data): then only 112, never a dead button
        val trip = HomePrefs.state.collectAsStateWithLifecycle().value?.trip == true
        KidCallBar(onFamily = if (trip) null else ({ nav.go(Route.Mine(MinePart.KID_CALL)) }))
    }
}

@Composable
private fun KidTile(card: ActionCard, tint: Tint, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = tint.container, contentColor = tint.ink),
        // Round, soft shapes: friendly to a child's eye, and large enough for a small finger
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // The picture is for the eye; TalkBack reads the words
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(pictureRes(card.id)), null, Modifier.size(56.dp), colorFilter = if (card.id == "kid-buzz") androidx.compose.ui.graphics.ColorFilter.tint(Group.DANGER.tint().ink) else null)
            Spacer(Modifier.size(8.dp))
            Text(card.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Sunny, the child's little helper: an original smiling sunflower drawn here, with no picture
 * files. Calm, still and warm; it never moves, so it never pulls attention from the steps.
 */
@Composable
fun Sunny(size: Dp, happy: Boolean = false) {
    Canvas(Modifier.size(size).clearAndSetSemantics { }) {
        val c = center
        val r = this.size.minDimension / 2
        val petal = Color(0xFFFFC928)
        // Twelve petals around the middle
        repeat(12) { i ->
            rotate(i * 30f, c) {
                drawOval(petal, topLeft = Offset(c.x - r * 0.18f, c.y - r), size = Size(r * 0.36f, r * 0.62f))
            }
        }
        drawCircle(Color(0xFF7A4E2D), radius = r * 0.5f, center = c)
        // Eyes and a smile; a wider smile when a card is done
        val eye = Color(0xFFFFF3D6)
        drawCircle(eye, radius = r * 0.07f, center = Offset(c.x - r * 0.18f, c.y - r * 0.1f))
        drawCircle(eye, radius = r * 0.07f, center = Offset(c.x + r * 0.18f, c.y - r * 0.1f))
        val sweep = if (happy) 160f else 120f
        drawArc(
            eye, startAngle = 90f - sweep / 2, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(c.x - r * 0.24f, c.y - r * 0.12f), size = Size(r * 0.48f, r * 0.4f),
            style = Stroke(width = r * 0.07f, cap = StrokeCap.Round),
        )
    }
}

/**
 * Two large buttons always below: call family, and 112. No other services to choose from. 112
 * asks once, in a child's words, before the dialler opens: a small finger often taps by chance.
 */
@Composable
internal fun KidCallBar(onFamily: (() -> Unit)?) {
    val context = LocalContext.current
    var ask by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (ask) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = false },
            icon = { Sunny(56.dp) },
            title = { Text(stringResource(R.string.kid_112_ask), textAlign = TextAlign.Center) },
            text = { Text(stringResource(R.string.kid_112_ask_hint), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(
                    onClick = { ask = false; Reach.dial(context, Service.UNIFIED.number) },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.kid_112_yes), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(onClick = { ask = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.kid_112_no))
                }
            },
        )
    }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onFamily != null) {
                Button(onClick = onFamily, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                    Text("📞", modifier = Modifier.clearAndSetSemantics { })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.kid_call_family), fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = { ask = true },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
                modifier = (if (onFamily == null) Modifier.weight(1f) else Modifier).heightIn(min = 64.dp),
            ) { Text(stringResource(R.string.kid_call_112), fontWeight = FontWeight.SemiBold) }
        }
    }
}

/**
 * The child's own people, set by a grown-up in "Trusted people": one big button each, opens the
 * phone's dialler with the number filled in. Encrypted data, so a protected screen.
 */
@Composable
fun KidCallScreen() {
    val context = LocalContext.current
    val people = rememberPeople() ?: return Loading()
    val family = people.filter { it.ice || it.safeList }.filter { it.phone.isNotBlank() }
    if (family.isEmpty()) {
        Text(stringResource(R.string.kid_call_none), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        return
    }
    Text(stringResource(R.string.kid_call_intro), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    family.forEach { p ->
        Button(onClick = { Reach.dial(context, p.phone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text("📞", modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(12.dp))
            Text(p.name.ifBlank { p.phone }, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
    Text(stringResource(R.string.kid_call_112_hint), style = MaterialTheme.typography.bodyLarge)
}

/**
 * A child's card, built on how children take things in under stress: one short step at a time,
 * a big picture, big words, one big button. No sources, no "all steps", no preview of the next one;
 * the small "why" is said plainly under the step. Praise at the end, never scolding (guide 5.5).
 */
@Composable
internal fun KidCardBody(card: ua.sprotyv.content.Card, content: ua.sprotyv.content.Content, nav: Nav) {
    var step by androidx.compose.runtime.saveable.rememberSaveable(card.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    val done = step >= card.steps.size
    // Where you are: big dots, done ones filled
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
        card.steps.indices.forEach { i ->
            androidx.compose.foundation.layout.Box(
                Modifier.size(if (i == step) 18.dp else 14.dp).background(
                    if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape,
                ),
            )
        }
    }
    if (!done) {
        val s = card.steps[step]
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(pictureRes(card.id)), null, Modifier.size(72.dp), colorFilter = if (card.id == "kid-buzz") androidx.compose.ui.graphics.ColorFilter.tint(Group.DANGER.tint().ink) else null)
            Text(
                s.action, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            )
            if (s.why.isNotBlank()) Text(s.why, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text(stringResource(if (step == card.steps.lastIndex) R.string.kid_step_last else R.string.kid_step_next), style = MaterialTheme.typography.titleLarge)
        }
        if (step > 0) {
            androidx.compose.material3.TextButton(onClick = { step-- }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.kid_step_back)) }
        }
    } else {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Sunny(120.dp, happy = true)
            Text(
                stringResource(R.string.kid_well_done), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading(); liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            )
            Text(stringResource(R.string.kid_well_done_hint), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        // A game only after the card, never between the steps
        if (content.practice.any { it.card == card.id }) {
            Button(onClick = { nav.go(Route.PracticeRoute(card.id)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(R.string.practice_card_kid), style = MaterialTheme.typography.titleMedium)
            }
        }
        androidx.compose.material3.OutlinedButton(onClick = { step = 0 }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.again)) }
    }
}
