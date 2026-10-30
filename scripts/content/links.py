#!/usr/bin/env python3
"""
The "Situation" section: trusted places to look, opened outside Sprotyv (the browser or the
source's own app). Sprotyv itself fetches nothing. Each link says who runs it and what to keep in
mind, and is marked official, volunteer or independent. Checked by hand on the date below.
Aerial targets: only as their sources publish them; nothing on air defence, shoot-downs or impacts.

    python3 scripts/content/links.py   -> content/src/main/assets/links.json
"""
import json, os, re

CHECKED = "2026-10-30"

FLAGS = {"unknown_author", "ads", "beta", "collects_data", "paid", "paused"}
DIRECTORY = ("idp", "money", "docs", "kids", "veterans", "health", "care")

def link(id, group, kind, title, note, url="", app="", phone="", flags=()):
    assert set(flags) <= FLAGS, id
    assert kind in ("official", "volunteer", "independent"), id
    assert re.fullmatch(r"[a-z0-9-]{1,40}", id), id
    assert (url and re.fullmatch(r"https://[a-z0-9-]+(\.[a-z0-9-]+)+(/[A-Za-z0-9._~/?=&%-]*)?", url)) or (not url and phone), id
    assert not app or re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z0-9_]+)+", app), id
    assert not phone or re.fullmatch(r"[0-9]{3,10}", phone), id
    assert 0 < len(title) <= 80 and 0 < len(note) <= 200, id
    return {"id": id, "group": group, "kind": kind, "title": title, "note": note,
            "url": url, "app": app, "phone": phone, "checkedOn": CHECKED, "flags": list(flags)}

LINKS = [
    # Air threats
    link("air-alert", "threats", "official", "Повітряна тривога: тривоги й рух цілей",
         "Офіційні тривоги й приблизний рух крилатих ракет і шахедів від Повітряних сил. Балістики не показує, дані із затримкою.",
         url="https://map.ukrainealarm.com", app="com.ukrainealarm"),
    link("air-force", "threats", "official", "Повітряні сили ЗСУ",
         "Попередження про напрямки ракет і дронів. Канал також пише про збиті цілі — нам важливі лише попередження.",
         url="https://t.me/kpszsu"),
    link("monitor", "threats", "volunteer", "monitor: мапа дронів",
         "Приблизне положення дронів з відкритих даних, оновлення кожні 20 секунд. Не офіційне оповіщення.",
         url="https://map.war-monitor.org", flags=("unknown_author", "beta")),
    link("kyivsafety", "threats", "volunteer", "KyivSafety: ризик за адресою в Києві",
         "Оцінка 0–100 для адреси за історією влучань і прольотів з відкритих джерел; дослідницький інструмент. Висока оцінка — не безпека: тривога означає укриття.",
         url="https://kyivsafety.com", flags=("unknown_author",)),
    link("alerts-in-ua", "threats", "volunteer", "alerts.in.ua",
         "Мапа тривог за областями й громадами з офіційних каналів.",
         url="https://alerts.in.ua"),
    # Front line
    link("deepstate", "front", "volunteer", "DeepState: лінія фронту",
         "Лінія фронту й окуповані території, із затримкою в кілька днів. Відкривайте лише цю адресу: є клони.",
         url="https://deepstatemap.live"),
    link("isw", "front", "independent", "ISW: аналітичні мапи",
         "Щоденні мапи й огляди американського аналітичного центру, англійською.",
         url="https://understandingwar.org"),
    # Help
    link("app-112", "help", "official", "112 Ukraine (МВС)",
         "Виклик 112 через інтернет, коли немає мобільного зв'язку. Увійдіть заздалегідь через ID.GOV.UA.",
         url="https://play.google.com/store/apps/details?id=ua.gov.mvs.service112", app="ua.gov.mvs.service112"),
    link("evacuation", "help", "official", "Лінія евакуації 1548",
         "Цілодобова державна гаряча лінія з кризових питань, зокрема евакуації.",
         phone="1548"),
    link("diia", "help", "official", "Дія: укриття й Пункти незламності",
         "У застосунку Дія є мапа укриттів і Пунктів незламності.",
         url="https://diia.gov.ua"),
    link("mines", "help", "official", "ДСНС: мапа мінної небезпеки",
         "Території, де можуть бути міни й вибухонебезпечні предмети.",
         url="https://mine.dsns.gov.ua"),
    # Power cuts: each oblast's own grid operator (addresses checked against Glavcom's list of 17.10.2026)
    link("ukrenergo", "power", "official", "Укренерго: стан енергосистеми",
         "Загальний режим відключень на добу для всієї країни. Графік своєї черги — на сайті обленерго.",
         url="https://t.me/Ukrenergo"),
    link("dtek", "power", "official", "ДТЕК: офіційний канал",
         "Єдиний офіційний канал групи ДТЕК; є десятки фейкових з такою назвою.",
         url="https://t.me/dtek_ua"),
    link("power-kyiv-city", "power", "official", "Київ", "ДТЕК Київські електромережі: графік відключень за чергами.", url="https://www.dtek-kem.com.ua/ua/shutdowns"),
    link("power-kyiv-oblast", "power", "official", "Київська область", "ДТЕК Київські регіональні електромережі: графік відключень за чергами.", url="https://www.dtek-krem.com.ua/ua/shutdowns"),
    link("power-vinnytsia", "power", "official", "Вінницька область", "Вінницяобленерго: графік відключень за чергами.", url="https://www.voe.com.ua/disconnection"),
    link("power-volyn", "power", "official", "Волинська область", "Волиньобленерго: графік відключень за чергами.", url="https://energy.volyn.ua/spozhyvacham/perervy-u-elektropostachanni/hrafik-vidkliuchen/"),
    link("power-dnipro", "power", "official", "Дніпропетровська область", "ДТЕК Дніпровські електромережі: графік відключень за чергами.", url="https://www.dtek-dnem.com.ua/ua/shutdowns"),
    link("power-dnipro-cek", "power", "official", "Дніпропетровська область (ЦЕК)", "ЦЕК, частина області: графік відключень за чергами.", url="https://cek.dp.ua/index.php/cpojivaham/vidkliuchennia.html"),
    link("power-donetsk", "power", "official", "Донецька область", "ДТЕК Донецькі електромережі: графік відключень за чергами.", url="https://www.dtek-dem.com.ua/ua/shutdowns"),
    link("power-zhytomyr", "power", "official", "Житомирська область", "Житомиробленерго: графік відключень за чергами.", url="https://www.ztoe.com.ua/unhooking-search.php"),
    link("power-zakarpattia", "power", "official", "Закарпатська область", "Закарпаттяобленерго: графік відключень за чергами.", url="https://zakarpat.energy/customers/break-in-electricity-supply/schedule/"),
    link("power-zaporizhzhia", "power", "official", "Запорізька область", "Запоріжжяобленерго: сайт оператора; графік — у розділі про відключення.", url="https://www.zoe.com.ua"),
    link("power-ivano-frankivsk", "power", "official", "Івано-Франківська область", "Прикарпаттяобленерго: графік відключень за чергами.", url="https://svitlo.oe.if.ua"),
    link("power-kirovohrad", "power", "official", "Кіровоградська область", "Кіровоградобленерго: графік відключень за чергами.", url="https://kiroe.com.ua/electricity-blackout"),
    link("power-lviv", "power", "official", "Львівська область", "Львівобленерго: графік відключень за чергами.", url="https://poweron.loe.lviv.ua"),
    link("power-mykolaiv", "power", "official", "Миколаївська область", "Миколаївобленерго: графік відключень за чергами.", url="https://off.energy.mk.ua"),
    link("power-odesa", "power", "official", "Одеська область", "ДТЕК Одеські електромережі: графік відключень за чергами.", url="https://www.dtek-oem.com.ua/ua/shutdowns"),
    link("power-poltava", "power", "official", "Полтавська область", "Полтаваобленерго: графік відключень за чергами.", url="https://www.poe.pl.ua/disconnection/povidomlennia/"),
    link("power-rivne", "power", "official", "Рівненська область", "Рівнеобленерго: графік відключень за чергами.", url="https://www.roe.vsei.ua/disconnections"),
    link("power-sumy", "power", "official", "Сумська область", "Сумиобленерго: графік відключень за чергами.", url="https://www.soe.com.ua/consumers/shutdowns"),
    link("power-ternopil", "power", "official", "Тернопільська область", "Тернопільобленерго: сайт оператора; графік — у розділі про відключення.", url="https://www.toe.com.ua"),
    link("power-kharkiv", "power", "official", "Харківська область", "Харківобленерго: графік відключень за чергами.", url="https://www.oblenergo.kharkov.ua/uk/gaoinfo"),
    link("power-kherson", "power", "official", "Херсонська область", "Херсонобленерго: сайт оператора; графік — у розділі про відключення.", url="https://ksoe.com.ua"),
    link("power-khmelnytskyi", "power", "official", "Хмельницька область", "Хмельницькобленерго: графік відключень за чергами.", url="https://hoe.com.ua/page/informatsija-vidkljuchennja"),
    link("power-cherkasy", "power", "official", "Черкаська область", "Черкасиобленерго: графік відключень за чергами.", url="https://www.cherkasyoblenergo.com/off"),
    link("power-chernivtsi", "power", "official", "Чернівецька область", "Чернівціобленерго: графік відключень за чергами.", url="https://oblenergo.cv.ua/shutdowns/"),
    link("power-chernihiv", "power", "official", "Чернігівська область", "Чернігівобленерго: графік відключень за чергами.", url="https://chernihivoblenergo.com.ua/blackouts"),
    # Evacuation and humanitarian help
    link("shid-sos", "evac", "volunteer", "Схід SOS: евакуація",
         "Вивозять дітей, тяжкохворих і маломобільних людей, допомагають із розселенням. Пн–Сб, 8:00–18:00.",
         phone="0800332614"),
    link("angels", "evac", "volunteer", "Янголи Спасіння: евакуація",
         "Евакуація з медичним супроводом і за потреби з розселенням.",
         phone="0800334620"),
    link("helping-to-leave", "evac", "volunteer", "Helping to Leave",
         "Допомагають виїхати з окупованих територій; публікують річні звіти.",
         url="https://helpingtoleave.org"),
    link("unhcr", "evac", "independent", "УВКБ ООН: довідник для українців",
         "Допомога для переміщених людей, контакти, права.",
         url="https://help.unhcr.org/ukraine/uk/"),
    link("uanimals", "evac", "volunteer", "UAnimals: тварини",
         "Евакуація тварин із прифронтових районів. Прийом заявок буває призупинено — перевірте на сайті.",
         url="https://uanimals.org"),
    # Missing people and prisoners
    link("koordshtab", "search", "official", "Координаційний штаб: полонені",
         "Заява про полоненого й стан розгляду; гаряча лінія 0 800 300 529. Шахраї беруть гроші «за інформацію» — не платіть.",
         url="https://cabinet.koordshtab.gov.ua"),
    link("missing-registry", "search", "official", "Реєстр зниклих безвісти (МВС)",
         "Внести людину до реєстру й отримати витяг; лінія 1698.",
         url="https://znykli-bezvisti.mvs.gov.ua"),
    link("nib", "search", "official", "Національне інформаційне бюро",
         "Розшук цивільних і військових, зниклих через війну.",
         phone="1648"),
    link("icrc-family", "search", "independent", "Червоний Хрест: пошук рідних",
         "Розшук родичів і відновлення зв'язку, безкоштовно; лінія 0 800 300 155.",
         url="https://familylinks.icrc.org"),
    # Psychological support
    link("howareu", "mind", "official", "«Ти як?»",
         "Державна програма психічного здоров'я: вправи, курси й куди звернутися по допомогу.",
         url="https://howareu.com"),
    link("la-strada", "mind", "volunteer", "La Strada: 116 123",
         "Цілодобово й безкоштовно: насильство, торгівля людьми. Для дітей і молоді — 116 111.",
         phone="116123"),
    link("gov-1547", "mind", "official", "Урядова лінія 1547",
         "Цілодобово: домашнє насильство, торгівля людьми, насильство щодо дітей.",
         phone="1547"),
    link("teenergizer", "mind", "volunteer", "Teenergizer",
         "Безкоштовні консультації психолога для підлітків і молоді.",
         url="https://teenergizer.org"),
    # Rights and help
    link("legal-aid", "rights", "official", "Безоплатна правнича допомога",
         "Безкоштовна консультація юриста, зокрема для постраждалих від війни; 0 800 213 103.",
         url="https://legalaid.gov.ua"),
    link("warcrimes", "rights", "official", "Повідомити про воєнний злочин",
         "Офіс Генерального прокурора: розповісти про злочин і передати докази.",
         url="https://warcrimes.gov.ua"),
    link("helsinki", "rights", "independent", "Українська Гельсінська спілка",
         "Правова допомога постраждалим від війни, зокрема з Реєстром збитків.",
         url="https://www.helsinki.org.ua"),
    link("redcross", "rights", "independent", "Червоний Хрест України: курси першої допомоги",
         "Курси першої допомоги для цивільних; розклад — у своїй області.",
         url="https://redcross.org.ua"),
    link("lun-shelters", "help", "independent", "LUN Місто: відгуки про укриття",
         "Мешканці пишуть, чи відчинене укриття. Не офіційне: перевірки знаходили укриття, яких насправді немає.",
         url="https://lun.ua/misto/monitoring-shelters"),
    # ---- "Where to turn" (directory). Checked against official news and reputable media, 30.10.2026 ----
    # Displaced people
    link("pfu-idp", "idp", "official", "Пенсійний фонд: допомога ВПО",
         "Допомога на проживання й компенсація тим, хто прихистив переселенців; гаряча лінія 0 800 503 753.",
         url="https://portal.pfu.gov.ua", flags=("collects_data",)),
    link("idp-vouchers", "idp", "official", "Житлові ваучери (Дія)",
         "Ваучер на житло для переселенців з окупованих територій. Заява лише в Дії, черга безкоштовна.",
         url="https://diia.gov.ua"),
    link("eoselia", "idp", "official", "єОселя",
         "Державна пільгова іпотека; умови й ставки — на сайті.",
         url="https://eoselia.diia.gov.ua", flags=("paid",)),
    link("dcz", "idp", "official", "Державна служба зайнятості",
         "Пошук роботи, навчання, гранти на власну справу.",
         url="https://www.dcz.gov.ua"),
    link("gov-1545", "idp", "official", "Урядова гаряча лінія 1545",
         "Питання й скарги до будь-якого органу влади, зокрема щодо виплат.",
         phone="1545"),
    # Money
    link("evidnovlennia", "money", "official", "єВідновлення (Дія)",
         "Допомога на ремонт або нове житло замість зруйнованого. Заява лише в Дії, безкоштовно.",
         url="https://diia.gov.ua"),
    link("unicef-spilno", "money", "independent", "ЮНІСЕФ «Спільно»",
         "Грошова допомога родинам з дітьми; 0 800 600 017. ЮНІСЕФ ніколи не просить CVV, пароль чи «внесок».",
         url="https://www.unicef.org/ukraine/spilno-cash-transfers"),
    link("cyberpolice", "money", "official", "Кіберполіція: шахраї",
         "Дали дані картки шахраям — спершу заблокуйте картку в банку, потім повідомте; 0 800 505 170.",
         url="https://cyberpolice.gov.ua"),
    # Documents
    link("dmsu", "docs", "official", "ДМС: паспорт і документи",
         "Втратили паспорт — заявіть у поліцію, потім до ДМС, ЦНАП чи «Документу». Поки чекаєте — Дія.",
         url="https://dmsu.gov.ua"),
    link("minjust", "docs", "official", "Мін'юст: народження й смерть на окупованих",
         "Спершу суд встановлює факт, потім ДРАЦС видає українське свідоцтво.",
         url="https://minjust.gov.ua"),
    link("mfa", "docs", "official", "МЗС: консульства й е-Консул",
         "Документи для тих, хто за кордоном: заява онлайн, отримання в консульстві.",
         url="https://mfa.gov.ua"),
    # Children
    link("children-of-war", "kids", "official", "Діти війни",
         "Повідомити про депортовану чи зниклу дитину. Не платіть нікому за «пошук» дитини.",
         url="https://childrenofwar.gov.ua"),
    link("bring-kids-back", "kids", "official", "Bring Kids Back UA",
         "Державна ініціатива з повернення дітей, вивезених росією.",
         url="https://bringkidsback.org.ua"),
    link("kids-line", "kids", "volunteer", "Дитяча лінія 116 111",
         "Анонімно й безкоштовно: булінг, насильство, конфлікти. Також 0 800 500 225.",
         phone="116111"),
    # Veterans and families
    link("veteran-line", "veterans", "official", "Єдина ветеранська лінія 1528",
         "Статуси, пільги, житло, робота, психологічна допомога; щодня 8–20, безкоштовно.",
         phone="1528"),
    link("veteran-pro", "veterans", "official", "Ветеран PRO: фахівець із супроводу",
         "Особистий супровідник у громаді допоможе скласти план і оформити послуги.",
         url="https://veteranpro.gov.ua", flags=("collects_data",)),
    link("veteran-fund", "veterans", "official", "Український ветеранський фонд: кризова лінія",
         "Цілодобова психологічна й юридична підтримка ветеранів і родин. Буває довге очікування.",
         phone="0800332029"),
    link("veteran-hub", "veterans", "volunteer", "Veteran Hub",
         "Психологічні та юридичні консультації для ветеранів і родин.",
         url="https://veteranhub.com.ua"),
    # Health and rehabilitation
    link("nszu", "health", "official", "НСЗУ 16-77: безоплатна медицина",
         "Безоплатні послуги, аптеки з «Доступними ліками», скарги на відмову; психіатр без направлення.",
         phone="1677"),
    link("superhumans", "health", "volunteer", "Superhumans",
         "Протезування й реабілітація для військових і цивільних, дорослих і дітей; безоплатно.",
         url="https://superhumans.com"),
    link("unbroken", "health", "official", "Unbroken (Львів)",
         "Комунальний центр: реабілітація, реконструктивна хірургія, дитяче відділення.",
         url="https://unbroken.org.ua"),
    link("moz-abroad", "health", "official", "МОЗ: лікування за кордоном",
         "Направлення оформлює ваш заклад; посередники-«агенції» не потрібні.",
         phone="0800505201"),
    # Older people and people with disabilities
    link("pfu-line", "care", "official", "Пенсійний фонд: контакт-центр",
         "Пенсії й соціальні допомоги. ПФУ ніколи не питає дані картки телефоном.",
         phone="0800503753"),
    link("ombudsman", "care", "official", "Омбудсман 1678",
         "Скарги на порушення прав, зокрема в будинках для літніх.",
         phone="1678"),
    link("barrier-free", "care", "official", "Мапа безбар'єрності",
         "Чи доступна будівля для крісла колісного, візочка, людей з порушеннями зору.",
         url="https://lun.ua/misto/barrier-free-map-2242"),
    link("proliska", "care", "volunteer", "Проліска: евакуація маломобільних",
         "Евакуація людей з інвалідністю, літніх і лежачих спецавтівками з медиками.",
         url="https://proliska.org"),
    # Fakes
    link("cpd", "facts", "official", "Центр протидії дезінформації",
         "Спростування фейків і попередження про ворожі канали та боти.",
         url="https://cpd.gov.ua"),
    link("perevirka", "facts", "independent", "Бот «Перевірка»",
         "Надішліть текст, фото чи посилання — фактчекери скажуть, чи це правда.",
         url="https://t.me/perevir_bot"),
    link("voxcheck", "facts", "independent", "VoxCheck",
         "Фактчекінг заяв і фейків від VoxUkraine.",
         url="https://voxukraine.org"),
    link("stopfake", "facts", "independent", "StopFake",
         "Спростування російських фейків з 2014 року.",
         url="https://www.stopfake.org"),
    link("detector", "facts", "independent", "Детектор медіа: дезінформація",
         "Розбори пропаганди й фейків, зокрема про тривоги.",
         url="https://disinfo.detector.media"),
]

if __name__ == "__main__":
    out = os.path.join(os.path.dirname(__file__), "..", "..", "content", "src", "main", "assets", "links.json")
    assert len({l["id"] for l in LINKS}) == len(LINKS)
    for l in LINKS:
        assert l["group"] in ("threats", "front", "help", "evac", "search", "mind", "rights", "facts", "power") + DIRECTORY, l["id"]
        assert len(l["note"]) <= 200, l["id"]
    with open(out, "w", encoding="utf-8") as f:
        json.dump(LINKS, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(len(LINKS), "links")
