package ua.sprotyv.ui

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
private fun picture(id: String): String = when (id) {
    "kid-siren" -> "🚨"
    "kid-buzz" -> "🏠"
    "kid-strange" -> "✋"
    "kid-lost" -> "🧍"
    "kid-fire" -> "🔥"
    "kid-scared" -> "🧸"
    "kid-code" -> "🔑"
    else -> "⭐"
}

/** Soft colours from the group palette, one per tile, so each is found by colour too. */
private val KID_COLOURS = listOf(Group.DANGER, Group.HOME_LIFE, Group.FAMILY, Group.LEAVE, Group.PREPARE, Group.FIRST_AID, Group.MINE)

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
                KidTile(card, KID_COLOURS[i % KID_COLOURS.size].tint()) { nav.go(Route.CardRoute(card.id)) }
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
            Text(picture(card.id), fontSize = 56.sp, modifier = Modifier.clearAndSetSemantics { })
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

/** Two large buttons always below: call family, and 112. No other services to choose from. */
@Composable
private fun KidCallBar(onFamily: (() -> Unit)?) {
    val context = LocalContext.current
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
                onClick = { Reach.dial(context, Service.UNIFIED.number) },
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
        Text(stringResource(R.string.kid_call_none), style = MaterialTheme.typography.titleMedium)
        return
    }
    Text(stringResource(R.string.kid_call_intro), style = MaterialTheme.typography.titleMedium)
    family.forEach { p ->
        Button(onClick = { Reach.dial(context, p.phone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text("📞", modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(12.dp))
            Text(p.name.ifBlank { p.phone }, style = MaterialTheme.typography.titleLarge)
        }
    }
    Text(stringResource(R.string.kid_call_112_hint), style = MaterialTheme.typography.bodyLarge)
}
