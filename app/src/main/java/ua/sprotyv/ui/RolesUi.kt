package ua.sprotyv.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as A11yRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Group
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.Setting

const val KEY_ROLES = "roles"

/** The circles a role belongs to: how a person holds on and helps in a long war. */
enum class Circle(val title: Int) { CARE(R.string.circle_care), DEFENCE(R.string.circle_defence), HELP(R.string.circle_help), WORK(R.string.circle_work), VOICE(R.string.circle_voice) }

/**
 * One role: not a label but a person's place, and the next step from there. [facts] are short
 * lines from official sources ([source] says which and when checked); [links] are groups of the
 * link list, or single links as "#id"; [pathLevel] is the role's own block in "My path".
 */
class Role(
    val id: String,
    val circle: Circle,
    val title: Int,
    val hint: Int,
    val affirm: Int,
    val factsTitle: Int? = null,
    val facts: List<Int> = emptyList(),
    val source: Int? = null,
    val moreTitle: Int? = null,
    val more: List<Int> = emptyList(),
    val moreSource: Int? = null,
    val links: List<String> = emptyList(),
    val pathLevel: Int? = null,
)

/** The medic role is the "I have medical training" switch kept before roles; it stays there. */
const val ROLE_MEDIC = "medic"

val ROLES: Map<String, Role> = listOf(
    Role(
        "dobro", Circle.DEFENCE, R.string.role_dobro, R.string.role_dobro_hint, R.string.role_dobro_affirm,
        factsTitle = R.string.service_facts_title,
        facts = listOf(R.string.service_fact_1, R.string.role_dobro_contracts, R.string.role_dobro_tro, R.string.service_fact_3, R.string.service_fact_4, R.string.service_fact_5, R.string.role_dobro_rights),
        source = R.string.role_dobro_source,
        moreTitle = R.string.role_women_title,
        more = listOf(R.string.role_women_1, R.string.role_women_2, R.string.role_women_3, R.string.role_women_4, R.string.role_women_5),
        moreSource = R.string.role_women_source,
        links = listOf("service"),
        pathLevel = PATH_LEVEL_DOBRO,
    ),
    Role(
        "serving", Circle.DEFENCE, R.string.role_serving, R.string.role_serving_hint, R.string.role_serving_affirm,
        factsTitle = R.string.role_serving_facts_title,
        facts = listOf(R.string.role_serving_1, R.string.role_serving_2, R.string.role_serving_3, R.string.role_serving_4, R.string.role_serving_5, R.string.role_serving_6),
        source = R.string.role_serving_source,
        links = listOf("#armyplus", "#mod-hotline", "#legal-aid", "#mil-rights", "#mil-proxy"),
    ),
    Role(
        "parent", Circle.CARE, R.string.role_parent, R.string.role_parent_hint, R.string.role_parent_affirm,
        factsTitle = R.string.role_parent_facts_title,
        facts = listOf(R.string.role_parent_1, R.string.role_parent_2, R.string.role_parent_3, R.string.role_parent_4, R.string.role_parent_5),
        source = R.string.role_parent_source,
        links = listOf("kids", "#unicef-parents"),
    ),
    Role(
        "grandparent", Circle.CARE, R.string.role_grandparent, R.string.role_grandparent_hint, R.string.role_grandparent_affirm,
        factsTitle = R.string.role_grandparent_facts_title,
        facts = listOf(R.string.role_grandparent_1, R.string.role_grandparent_2, R.string.role_grandparent_3, R.string.role_grandparent_4, R.string.role_grandparent_5, R.string.role_grandparent_6),
        source = R.string.role_grandparent_source,
        links = listOf("#pfu-line", "#pfu-portal", "#elegant-course", "#cyberpolice"),
    ),
    Role(
        "carer", Circle.CARE, R.string.role_carer, R.string.role_carer_hint, R.string.role_carer_affirm,
        factsTitle = R.string.role_carer_facts_title,
        facts = listOf(R.string.role_carer_1, R.string.role_carer_2, R.string.role_carer_3, R.string.role_carer_4, R.string.role_carer_5),
        source = R.string.role_carer_source,
        links = listOf("care", "#msp-eservices"),
    ),
    Role(
        "milfamily", Circle.CARE, R.string.role_milfamily, R.string.role_milfamily_hint, R.string.role_milfamily_affirm,
        factsTitle = R.string.role_milfamily_facts_title,
        facts = listOf(R.string.role_milfamily_1, R.string.role_milfamily_2, R.string.role_milfamily_3, R.string.role_milfamily_4, R.string.role_milfamily_5, R.string.role_milfamily_6),
        source = R.string.role_milfamily_source,
        links = listOf("search", "#veteran-pro", "#veteran-line", "#veteran-fund"),
    ),
    Role(
        "veteran", Circle.DEFENCE, R.string.role_veteran, R.string.role_veteran_hint, R.string.role_veteran_affirm,
        factsTitle = R.string.role_veteran_facts_title,
        facts = listOf(R.string.role_veteran_1, R.string.role_veteran_2, R.string.role_veteran_3, R.string.role_veteran_4, R.string.role_veteran_5, R.string.role_veteran_6),
        source = R.string.role_veteran_source,
        links = listOf("veterans", "#legal-aid"),
    ),
    Role(
        "dftg", Circle.DEFENCE, R.string.role_dftg, R.string.role_dftg_hint, R.string.role_dftg_affirm,
        factsTitle = R.string.role_dftg_facts_title,
        facts = listOf(R.string.role_dftg_1, R.string.role_dftg_2, R.string.role_dftg_3, R.string.role_dftg_4, R.string.role_dftg_5, R.string.role_dftg_6),
        source = R.string.role_dftg_source,
        links = listOf("service", "learn"),
        pathLevel = PATH_LEVEL_DFTG,
    ),
    Role(
        "skills", Circle.DEFENCE, R.string.role_skills, R.string.role_skills_hint, R.string.role_skills_affirm,
        factsTitle = R.string.role_skills_facts_title,
        facts = listOf(R.string.role_skills_1, R.string.role_skills_2, R.string.role_skills_3, R.string.role_skills_4, R.string.role_skills_5, R.string.role_skills_6),
        source = R.string.role_skills_source,
        links = listOf("#sbu-1516", "#cyberpolice", "#brave1", "#digital-team", "drones", "cyber"),
    ),
    Role(
        "volunteer", Circle.HELP, R.string.role_volunteer, R.string.role_volunteer_hint, R.string.role_volunteer_affirm,
        factsTitle = R.string.role_volunteer_facts_title,
        facts = listOf(R.string.role_volunteer_1, R.string.role_volunteer_2, R.string.role_volunteer_3, R.string.role_volunteer_4, R.string.role_volunteer_5),
        source = R.string.role_volunteer_source,
        links = listOf("#tax-volunteers", "#cyberpolice", "#redcross"),
    ),
    Role(
        "donor", Circle.HELP, R.string.role_donor, R.string.role_donor_hint, R.string.role_donor_affirm,
        factsTitle = R.string.role_donor_facts_title,
        facts = listOf(R.string.role_donor_1, R.string.role_donor_2, R.string.role_donor_3, R.string.role_donor_4, R.string.role_donor_5),
        source = R.string.role_donor_source,
        links = listOf("#blood"),
    ),
    Role(
        "instructor", Circle.HELP, R.string.role_instructor, R.string.role_instructor_hint, R.string.role_instructor_affirm,
        factsTitle = R.string.role_instructor_facts_title,
        facts = listOf(R.string.role_instructor_1, R.string.role_instructor_2, R.string.role_instructor_3, R.string.role_instructor_4),
        source = R.string.role_instructor_source,
        links = listOf("#redcross", "#redcross-centres", "#medcat", "#prometheus-fa"),
    ),
    Role(
        "neighbours", Circle.HELP, R.string.role_neighbours, R.string.role_neighbours_hint, R.string.role_neighbours_affirm,
        factsTitle = R.string.role_neighbours_facts_title,
        facts = listOf(R.string.role_neighbours_1, R.string.role_neighbours_2, R.string.role_neighbours_3, R.string.role_neighbours_4),
        source = R.string.role_neighbours_source,
        links = listOf("#howareu", "#who-pfa", "#nezlamnist", "#msp-eservices"),
    ),
    Role(
        "teacher", Circle.WORK, R.string.role_teacher, R.string.role_teacher_hint, R.string.role_teacher_affirm,
        factsTitle = R.string.role_teacher_facts_title,
        facts = listOf(R.string.role_teacher_1, R.string.role_teacher_2, R.string.role_teacher_3, R.string.role_teacher_4, R.string.role_teacher_5),
        source = R.string.role_teacher_source,
        links = listOf("#mon-alerts", "#mon-mines", "#howareu"),
    ),
    Role(
        "research", Circle.WORK, R.string.role_research, R.string.role_research_hint, R.string.role_research_affirm,
        factsTitle = R.string.role_research_facts_title,
        facts = listOf(R.string.role_research_1, R.string.role_research_2, R.string.role_research_3, R.string.role_research_4),
        source = R.string.role_research_source,
        links = listOf("#nrfu", "#horizon", "#msca4ukraine", "#brave1"),
    ),
    Role(
        "infra", Circle.WORK, R.string.role_infra, R.string.role_infra_hint, R.string.role_infra_affirm,
        factsTitle = R.string.role_infra_facts_title,
        facts = listOf(R.string.role_infra_1, R.string.role_infra_2, R.string.role_infra_3, R.string.role_infra_4),
        source = R.string.role_infra_source,
        links = listOf("#booking", "#energy-safety", "#howareu"),
    ),
    Role(
        "business", Circle.WORK, R.string.role_business, R.string.role_business_hint, R.string.role_business_affirm,
        factsTitle = R.string.role_business_facts_title,
        facts = listOf(R.string.role_business_1, R.string.role_business_2, R.string.role_business_3, R.string.role_business_4, R.string.role_business_5),
        source = R.string.role_business_source,
        links = listOf("#diia-business", "#dar", "#evidnovlennia"),
    ),
    Role(
        "localgov", Circle.WORK, R.string.role_localgov, R.string.role_localgov_hint, R.string.role_localgov_affirm,
        factsTitle = R.string.role_localgov_facts_title,
        facts = listOf(R.string.role_localgov_1, R.string.role_localgov_2, R.string.role_localgov_3, R.string.role_localgov_4),
        source = R.string.role_localgov_source,
        links = listOf("#nezlamnist", "#nads", "#decentralization", "#howareu"),
    ),
    Role(
        "culture", Circle.VOICE, R.string.role_culture, R.string.role_culture_hint, R.string.role_culture_affirm,
        factsTitle = R.string.role_culture_facts_title,
        facts = listOf(R.string.role_culture_1, R.string.role_culture_2, R.string.role_culture_3, R.string.role_culture_4, R.string.role_culture_5),
        source = R.string.role_culture_source,
        links = listOf("#ucf", "#nsju", "#imi", "facts"),
    ),
    Role(
        "student", Circle.VOICE, R.string.role_student, R.string.role_student_hint, R.string.role_student_affirm,
        factsTitle = R.string.role_student_facts_title,
        facts = listOf(R.string.role_student_1, R.string.role_student_2, R.string.role_student_3, R.string.role_student_4),
        source = R.string.role_student_source,
        links = listOf("#mon", "#nszu", "#dcz-voucher"),
    ),
    Role(
        "abroad", Circle.VOICE, R.string.role_abroad, R.string.role_abroad_hint, R.string.role_abroad_affirm,
        factsTitle = R.string.role_abroad_facts_title,
        facts = listOf(R.string.role_abroad_1, R.string.role_abroad_2, R.string.role_abroad_3, R.string.role_abroad_4, R.string.role_abroad_5, R.string.role_abroad_6),
        source = R.string.role_abroad_source,
        links = listOf("#united24", "#mfa-queue", "#msp-abroad", "#mon-abroad", "#mfa"),
    ),
    Role(
        ROLE_MEDIC, Circle.WORK, R.string.role_medic, R.string.role_medic_hint, R.string.role_medic_affirm,
        links = listOf("health"),
        pathLevel = PATH_LEVEL_MEDIC,
    ),
).associateBy { it.id }

/** The roles picked; anything odd is dropped. */
fun parseRoles(s: String?): Set<String> {
    if (s.isNullOrBlank() || s.length > 1_000) return emptySet()
    return try {
        val a = org.json.JSONArray(s)
        (0 until minOf(a.length(), 30)).map { a.optString(it) }.filter { it in ROLES && it != ROLE_MEDIC }.toSet()
    } catch (e: Exception) {
        emptySet()
    }
}

/** The roles picked, the medic one included; null while still reading. */
@Composable
fun rememberRoles(): Set<String>? {
    val stored by remember { PersonalStore.observe { db -> db.settings().observe(KEY_ROLES).map { listOf(it) } } }.collectAsStateWithLifecycle(null)
    val medic = HomePrefs.state.collectAsState().value?.medic == true
    val list = stored ?: return null
    return parseRoles(list.first()) + (if (medic) setOf(ROLE_MEDIC) else emptySet())
}

/**
 * "Who I am now": roles by circle, any number or none, changed at any time. Picked roles open
 * their own page and add their block to "My path". Kept encrypted in "Mine": a role can say a lot
 * about a person (service, health), so it never leaves this phone except in the backup.
 */
@Composable
fun RolesScreen(nav: Nav) {
    val context = LocalContext.current
    val picked = rememberRoles() ?: return Loading()
    fun change(id: String, on: Boolean) {
        if (id == ROLE_MEDIC) {
            HomePrefs.update(context) { it.copy(medic = on) }
            return
        }
        PersonalStore.save("setting:$KEY_ROLES:" + java.util.UUID.randomUUID()) { db ->
            val now = parseRoles(db.settings().get(KEY_ROLES))
            db.settings().put(Setting(KEY_ROLES, org.json.JSONArray((if (on) now + id else now - id).sorted()).toString()))
        }
    }
    Text(stringResource(R.string.roles_intro), style = MaterialTheme.typography.bodyLarge)
    // The roles picked first, each one tap from its page
    val mine = ROLES.values.filter { it.id in picked }
    if (mine.isNotEmpty()) {
        Block(stringResource(R.string.roles_mine), Group.PREPARE, tint = resist(), padded = false) {
            mine.forEachIndexed { i, r -> BlockRow(stringResource(r.title), stringResource(r.hint), first = i == 0) { nav.go(Route.RoleRoute(r.id)) } }
        }
    }
    Circle.entries.forEach { circle ->
        val roles = ROLES.values.filter { it.circle == circle }
        if (roles.isEmpty()) return@forEach
        val got = roles.count { it.id in picked }
        Block(stringResource(circle.title), Group.PREPARE, tint = resist(), padded = false, foldable = true, startOpen = got > 0 || mine.isEmpty(),
            trailing = { if (got > 0) Text(stringResource(R.string.ready_count, got, roles.size), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }) {
            roles.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val on = r.id in picked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).doneRow(on).toggleable(value = on, role = A11yRole.Checkbox) { change(r.id, it) }
                        .padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp), colors = doneCheckbox())
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(stringResource(r.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(r.hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.roles_more_coming), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.roles_private), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * One role's page: what the person already gives, their steps in "My path", plain facts from
 * official sources, where to turn, and how to hold on for long. Never "you must": the next step.
 */
@Composable
fun RoleScreen(r: Role, content: Content?, nav: Nav) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    Text(stringResource(r.affirm), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (r.pathLevel != null) HubCard(stringResource(R.string.role_steps_title), stringResource(R.string.role_steps_hint), resist()) { nav.go(Route.Mine(MinePart.PATH)) }
    if (r.facts.isNotEmpty() && r.factsTitle != null) FactsBlock(stringResource(r.factsTitle), r.facts, r.source)
    if (r.more.isNotEmpty() && r.moreTitle != null) FactsBlock(stringResource(r.moreTitle), r.more, r.moreSource)
    if (content == null) return Loading()
    // Where to turn: one block, whatever groups the links come from
    val links = r.links.flatMap { g -> content.links.filter { if (g.startsWith("#")) it.id == g.drop(1) else it.group == g } }.distinctBy { it.id }
    if (links.isNotEmpty()) {
        Block(stringResource(R.string.role_where), Group.PREPARE, tint = resist(), padded = false, foldable = true, summary = stringResource(R.string.shelf_count, links.size)) {
            links.forEach { link -> LinkRow(link) { openLink(context, link, toolbar) } }
        }
    }
    // Holding on for long: rest, support, the daily habits
    Block(stringResource(R.string.role_long_title), Group.PREPARE, tint = resist(), padded = false, foldable = true) {
        Text(stringResource(R.string.role_long_intro), style = ReadingText, modifier = Modifier.padding(16.dp))
        BlockRow(stringResource(R.string.gart_title), stringResource(R.string.gart_hint)) { nav.go(Route.Mine(MinePart.GART)) }
        content.links.filter { it.group == "mind" }.forEach { link -> LinkRow(link) { openLink(context, link, toolbar) } }
    }
}

@Composable
private fun FactsBlock(title: String, facts: List<Int>, source: Int?) {
    Block(title, Group.PREPARE, tint = resist(), foldable = true) {
        facts.forEach {
            Row { Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp)); Text(stringResource(it), style = ReadingText, modifier = Modifier.weight(1f)) }
        }
        source?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
