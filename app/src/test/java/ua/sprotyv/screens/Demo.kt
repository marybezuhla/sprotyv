package ua.sprotyv.screens

import ua.sprotyv.content.Content
import ua.sprotyv.content.Kit
import ua.sprotyv.content.MySupplies
import ua.sprotyv.content.Supplies
import ua.sprotyv.home.Pins
import ua.sprotyv.home.Preset
import ua.sprotyv.personal.FamilyPlan
import ua.sprotyv.personal.KitCheck
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Note
import ua.sprotyv.personal.Person
import ua.sprotyv.personal.PlanRow
import ua.sprotyv.personal.Setting
import ua.sprotyv.personal.backup.BackupData
import ua.sprotyv.ui.Bookmark
import ua.sprotyv.ui.Bookmarks
import ua.sprotyv.ui.KEY_HABITS
import ua.sprotyv.ui.KEY_KIT_REVIEWED
import ua.sprotyv.ui.KEY_PATH
import ua.sprotyv.ui.KEY_ROLES
import ua.sprotyv.ui.KEY_SAFE_MESSAGE
import ua.sprotyv.ui.KEY_SUPPLIES
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Placeholder data for tests: no person, no story — numbered contacts and notes with neutral
 * words, of realistic length, so the owner's screens can be drawn and the backup checked.
 * Phone numbers are zeros.
 */
object Demo {
    private val zone = ZoneId.of("Europe/Kyiv")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    fun snapshot(content: Content): BackupData.Snapshot {
        val medical = MedicalCard(
            name = "Ім'я Прізвище",
            birthDate = "01.01.1990",
            bloodGroup = "A(II) Rh+",
            allergies = "Приклад алергії: назва ліків, як проявляється",
            medicines = "Приклад: назва ліків, доза, коли приймати",
            conditions = "Приклад стану здоров'я, про який варто знати медикам",
            notes = "Довший приклад примітки для медиків: що робити, кому подзвонити, де лежать ліки. Текст потрібної довжини, щоб перевірити переноси рядків.",
            updatedAt = at(2026, 9, 2),
        )
        val people = (1..6).map { i ->
            Person(name = "Контакт $i", phone = "+38000000010$i", relation = if (i % 2 == 0) "Приклад: хто ця людина й чим допоможе" else "Приклад зв'язку", safeList = i <= 3, ice = i <= 2)
        }
        val plan = FamilyPlan(
            point1 = "Місце 1: найближче укриття (приклад адреси)",
            point2 = "Місце 2: Пункт незламності (приклад)",
            point3 = "Місце 3: у родичів в іншому місті (приклад)",
            goBetweenName = "Контакт 3",
            goBetweenPhone = "+380000000103",
            notes = "Приклад нотатки до плану: де документи, хто що бере, як діяти, якщо немає зв'язку. Текст потрібної довжини.",
            updatedAt = at(2026, 6, 20),
        )
        val rows = listOf(
            PlanRow(kind = PlanRow.KIND_ROLE, first = "Людина 1", second = "Документи, гроші, ліки", sort = 0),
            PlanRow(kind = PlanRow.KIND_ROLE, first = "Людина 2", second = "Вода, павербанк, ліхтарик", sort = 1),
            PlanRow(kind = PlanRow.KIND_ROLE, first = "Людина 3", second = "Свій рюкзак, телефон", sort = 2),
            PlanRow(kind = PlanRow.KIND_IF_THEN, first = "Немає зв'язку", second = "Зустрічаємося в місці 1 о визначеній годині", sort = 3),
            PlanRow(kind = PlanRow.KIND_IF_THEN, first = "Оголосили евакуацію", second = "Ідемо до місця збору, пишемо «Я в безпеці»", sort = 4),
        )
        // The go-bag: most of it ticked, a few things missing
        val kitItems = Kit.groups(content.guide).flatMap { it.items }.distinctBy { it.key }
        val kit = kitItems.mapIndexed { i, item -> KitCheck(item.key, checked = i % 5 != 3, at = at(2026, 1 + i % 10, 1 + i % 27)) }
        val food = Supplies.food(content.guide, 3, 14).map { Kit.key(it.what) }
        val supplies = MySupplies(
            people = 3, days = 14, water = 100, waterUntil = "2027-02",
            have = food.filterIndexed { i, _ -> i % 4 != 2 }.toSet(),
            until = mapOf(MySupplies.MEDS to "2026-11", MySupplies.BATTERIES to "2027-05") + food.take(2).mapIndexed { i, k -> k to listOf("2027-03", "2026-12")[i] },
        )
        val path = listOf("bleed", "kit", "move", "plan", "people", "shelter", "facts", "neighbours", "help")
        val bookmarks = (1..4).map { i -> Bookmark("https://example.org/$i", "Закладка $i: приклад назви сторінки", at(2026, i, 5)) }
        val settings = listOf(
            Setting(KEY_SAFE_MESSAGE, "Я в безпеці. Приклад повідомлення для своїх."),
            Setting(KEY_KIT_REVIEWED, at(2026, 9, 28).toString()),
            Setting(KEY_SUPPLIES, supplies.toJson()),
            Setting(KEY_PATH, org.json.JSONArray(path.sorted()).toString()),
            Setting(KEY_HABITS, habits()),
            Setting(KEY_ROLES, org.json.JSONArray(listOf("dftg")).toString()),
        ) + Bookmarks.toPages(bookmarks)!!.mapIndexed { i, page -> Setting(Bookmarks.KEYS[i], page) }
        val notes = (1..10).map { i ->
            Note(
                text = "Нотатка $i\n" + if (i % 3 == 0) "— пункт першого списку\n— пункт другий\n— третій пункт" else "Приклад тексту нотатки потрібної довжини: що, де, коли. Ще одне речення, щоб перевірити перенос.",
                pinned = i <= 2, updatedAt = at(2026, 1 + i % 10, 1 + i),
            )
        }
        val home = mapOf(
            "mode" to "FULL", "pins" to Pins.write(Preset.GENERAL.pins), "lock" to "false", "theme" to "SYSTEM",
            "dynamic" to "false", "call_bar" to "true", "readiness_hidden" to "false", "medic" to "false", "service" to "false",
            "neutral" to "false", "lock_mine" to "false",
            "notify_tourniquet" to "true", "notify_wait" to "true", "notify_counts" to "true",
            "notify_supplies" to "true", "notify_notes" to "true", "notify_kit" to "true", "notify_trip_all" to "false",
        )
        // A copy keeps each record's own number, as the app's database gives them
        return BackupData.Snapshot(
            created = at(2026, 10, 31, 9), medical = medical,
            people = people.mapIndexed { i, p -> p.copy(id = i + 1L) }, plan = plan,
            rows = rows.mapIndexed { i, r -> r.copy(id = i + 1L) },
            kit = kit, settings = settings, home = home,
            notes = notes.mapIndexed { i, n -> n.copy(id = i + 1L) },
        )
    }

    /** Three habits, ticked on most of the last weeks, today one of them. */
    private fun habits(): String {
        val today = java.time.LocalDate.now().toEpochDay()
        val done = org.json.JSONObject()
        listOf("news" to 3, "charge" to 2, "water" to 5).forEach { (id, every) ->
            done.put(id, org.json.JSONArray((0L..30L).filter { it % every != 1L }.map { today - it - (if (id == "news") 0 else 1) }))
        }
        return org.json.JSONObject().put("active", org.json.JSONArray(listOf("news", "charge", "water"))).put("done", done).put("body", org.json.JSONArray()).put("bodyMonth", "").toString()
    }
}
