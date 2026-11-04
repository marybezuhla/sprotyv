#!/usr/bin/env python3
"""
"Phones": numbers of emergency services, state hotlines and reputable helplines. Every number was
found on an official page or the organisation's own site (the source below), on the date below.
Left out on purpose: lines that are paused (Lifeline Ukraine 7333), lines of bodies that no longer
exist, numbers found only in the press, messenger-only and chat-bot contacts, regional lines.
Re-check every few months: short numbers change (1516, 1528 and 1548 all did in 2025-2026).

    python3 scripts/content/phones.py -> content/src/main/assets/phones.json, en/phones.json
"""
import json, os, re, sys

CHECKED = "2026-11-03"
CATEGORIES = ["emergency", "evacuation", "mind", "violence", "children", "health", "security", "veterans", "state"]

UKC = "https://ukc.gov.ua/a-dovidkova-informatsiya-a/kontakts-ovv/"
KMU_GAS = "https://www.kmu.gov.ua/news/moz-rekomenduye-yak-povoditis-v-razi-avarij-v-budinkah"
LASTRADA = "https://la-strada.org.ua/en/garyachi-liniyi"
ICRC = "https://www.icrc.org/en/where-we-work/europe-central-asia/ukraine/help-useful-information-affected-people"
MSP_1547 = "https://www.msp.gov.ua/press-center/news/yakshcho-vy-postrazhdaly-vid-domashnoho-nasylstva-abo-staly-yoho-svidkom-ne-movchit!-telefonuyte-na-haryachu-liniyu-1547"
OMB = "https://ombudsman.gov.ua/en/news/tags/diya"
LEGAL = "https://legalaid.gov.ua/kliyentam/pro-bezoplatnu-pravovu-dopomogu-bpd/kontakt-czentr/"
SBU = "https://ssu.gov.ua/novyny/sbu-zaprovadyla-novyi-korotkyi-nomer-hariachoi-linii-1516"
NIB = "https://nib.gov.ua/"
PHC = "https://www.phc.org.ua/en/activities/ongoing-projects/dont-give-aids-a-chance/hivaids-hotline"
GOV1545 = "https://www.kmu.gov.ua/uryadova-garyacha-liniya-1545"

# id, category, number, source, (uk title, uk note), (en title, en note)
P = [
    ("e-112", "emergency", "112", "https://npu.gov.ua/news/zastosunok-112-ukraine-alternatyvnyi-sposib-vyklyku-ekstrenykh-sluzhb-bez-mobilnoho-zviazku",
     ("Єдиний номер екстрених служб", "Пожежні, поліція, швидка, газова служба — по всій Україні."),
     ("Single emergency number", "Fire, police, ambulance and gas services across Ukraine.")),
    ("e-101", "emergency", "101", UKC,
     ("ДСНС: пожежі й порятунок", "Пожежа, порятунок людей, обірвані дроти, знайдений вибухонебезпечний предмет. Цілодобово."),
     ("Fire and rescue (State Emergency Service)", "Fire, rescue, downed power lines, an explosive object found. 24/7.")),
    ("e-102", "emergency", "102", "https://npu.gov.ua/kontakti",
     ("Поліція", "Злочин або подія, що потребує негайного реагування поліції. Цілодобово."),
     ("Police", "A crime or incident that needs police at once. 24/7.")),
    ("e-103", "emergency", "103", KMU_GAS,
     ("Швидка медична допомога", "Загроза життю чи здоров’ю. Цілодобово."),
     ("Ambulance", "A threat to life or health. 24/7.")),
    ("e-104", "emergency", "104", KMU_GAS,
     ("Аварійна газова служба", "Запах або витік газу. Якщо запах не зникає — 104 або 112."),
     ("Gas emergency", "A smell or leak of gas. If the smell persists: 104 or 112.")),

    ("ev-1548", "evacuation", "1548", "https://dp-reintegration.gov.ua/cilodobova-garyacha-liniya-z-krizovih-pitan-1548",
     ("Гаряча лінія з кризових питань", "Евакуація, житло й виплати ВПО, гуманітарна допомога, захоплене житло. Цілодобово, безкоштовно."),
     ("Crisis hotline", "Evacuation, housing and payments for displaced people, humanitarian aid, seized homes. 24/7, free.")),
    ("ev-east-sos", "evacuation", "0800332614", "https://east-sos.org/directions/call-center/",
     ("«Схід SOS»: евакуація", "Заявки на евакуацію, зокрема людей з обмеженою рухливістю; допомога ВПО. Пн–сб 08:00–18:00, безкоштовно."),
     ("East SOS: evacuation", "Evacuation requests, including people with limited mobility; help for displaced people. Mon–Sat 08:00–18:00, free.")),
    ("ev-uz", "evacuation", "0800503111", UKC,
     ("Укрзалізниця", "Поїзди, зокрема евакуаційні. Цілодобово."),
     ("Ukrainian Railways", "Trains, including evacuation trains. 24/7.")),
    ("ev-redcross", "evacuation", "0800332656", "https://redcross.org.ua/news/2025/01/102989/",
     ("Український Червоний Хрест", "Інформація про допомогу, підтримка, розшук зниклих родичів. Щодня 09:00–18:00, безкоштовно."),
     ("Ukrainian Red Cross", "Information on aid, support, tracing missing relatives. Daily 09:00–18:00, free.")),
    ("ev-icrc", "evacuation", "0800300155", ICRC,
     ("Міжнародний комітет Червоного Хреста", "Допомога постраждалим від війни, розшук родичів. Пн–пт 09:00–13:00 і 14:00–17:00, безкоштовно."),
     ("International Committee of the Red Cross", "Help for people affected by the war, tracing relatives. Mon–Fri 09:00–13:00 and 14:00–17:00, free.")),

    ("m-npa", "mind", "0800100102", "https://help.npa-ua.org/",
     ("Національна психологічна асоціація", "Безкоштовна конфіденційна розмова з психологом. Щодня; години різняться за джерелами."),
     ("National Psychological Association", "A free, confidential talk with a psychologist. Daily; hours differ between sources.")),
    ("m-pin", "mind", "0800210160", "https://ukraine.peopleinneed.net/ua/liniia-pidtrimki-10978gp",
     ("«Людина в біді»: лінія підтримки", "Психологічна підтримка для дорослих і дітей. Цілодобово."),
     ("People in Need support line", "Psychological support for adults and children. 24/7.")),
    ("m-men", "mind", "2345", LASTRADA,
     ("Лінія підтримки для чоловіків", "Стрес, криза, вигорання. 14:00–06:00; безкоштовно з мобільних."),
     ("Support line for men", "Stress, crisis, burnout. 14:00–06:00; free from mobiles.")),
    ("m-varto", "mind", "5522", "https://vartozhyty.com.ua/en",
     ("«Варто жити»: кризове консультування", "Пн–пт 11:00–19:00; безкоштовно з lifecell, Vodafone і Київстару."),
     ("Varto zhyty crisis counselling", "Mon–Fri 11:00–19:00; free from lifecell, Vodafone and Kyivstar.")),

    ("v-1547", "violence", "1547", MSP_1547,
     ("Насильство, торгівля людьми", "Домашнє та гендерне насильство, насильство щодо дітей, торгівля людьми; є психолог. Цілодобово, безкоштовно."),
     ("Violence and trafficking", "Domestic and gender-based violence, violence against children, trafficking; a psychologist is available. 24/7, free.")),
    ("v-1547-abroad", "violence", "+380442841943", MSP_1547,
     ("Лінія 1547 з-за кордону", "Те саме, що 1547, для дзвінків з-за кордону. Платно."),
     ("Line 1547 from abroad", "The same as 1547, for calls from abroad. Paid.")),
    ("v-lastrada", "violence", "0800500335", LASTRADA,
     ("«Ла Страда»: протидія насильству", "Домашнє насильство, торгівля людьми, дискримінація. Цілодобово, безкоштовно з мобільних і стаціонарних."),
     ("La Strada: against violence", "Domestic violence, trafficking, discrimination. 24/7, free from mobiles and landlines.")),
    ("v-116123", "violence", "116123", LASTRADA,
     ("«Ла Страда»: короткий номер", "Те саме, що 0 800 500 335, з мобільних. Цілодобово, безкоштовно."),
     ("La Strada: short number", "The same as 0 800 500 335, from mobiles. 24/7, free.")),

    ("c-116111", "children", "116111", LASTRADA,
     ("Дитяча гаряча лінія", "Анонімно для дітей і молоді; порада дорослим щодо прав дитини. Безкоштовно з мобільних."),
     ("Children's hotline", "Anonymous, for children and young people; advice for adults on children's rights. Free from mobiles.")),
    ("c-0800500225", "children", "0800500225", LASTRADA,
     ("Дитяча гаряча лінія (0 800)", "Те саме, що 116 111, з мобільних і стаціонарних."),
     ("Children's hotline (0 800)", "The same as 116 111, from mobiles and landlines.")),

    ("h-1677", "health", "1677", "https://www.kmu.gov.ua/news/na-yaki-pitannya-vidpovidaye-kontakt-centr-nszu",
     ("НСЗУ: медичні гарантії", "Безоплатні послуги, «Доступні ліки», заклади з договорами НСЗУ. Пн–чт 09:00–18:00, пт до 16:45."),
     ("National Health Service", "Free services, Affordable Medicines, facilities contracted by the NHSU. Mon–Thu 09:00–18:00, Fri to 16:45.")),
    ("h-moz", "health", "0800505201", UKC,
     ("Гаряча лінія МОЗ", "Звернення з питань охорони здоров’я. Щодня 08:00–20:00."),
     ("Ministry of Health hotline", "Questions and complaints about health care. Daily 08:00–20:00.")),
    ("h-hiv", "health", "0800500451", PHC,
     ("Гаряча лінія з ВІЛ/СНІДу", "Консультанти, психологи, лікарі-інфекціоністи."),
     ("HIV/AIDS helpline", "Counsellors, psychologists, infectious-disease doctors.")),
    ("h-ost", "health", "0800507727", PHC,
     ("Замісна підтримувальна терапія", "Питання замісної терапії. 09:00–18:00, безкоштовно, анонімно."),
     ("Opioid substitution therapy", "Questions about substitution therapy. 09:00–18:00, free, anonymous.")),

    ("s-sbu", "security", "1516", SBU,
     ("СБУ", "Диверсії, підпали на замовлення, підозрілі люди й предмети, вербування. Безкоштовно з усіх операторів."),
     ("Security Service (SBU)", "Sabotage, paid arson, suspicious people or objects, recruitment. Free from all operators.")),
    ("s-sbu-0800", "security", "0800501482", SBU,
     ("СБУ (попередній номер)", "Продовжує працювати. Безкоштовно."),
     ("SBU (previous number)", "Still working. Free.")),
    ("s-mod", "security", "1512", "https://mod.gov.ua/en/ministry-of-defence-of-ukraine-hotline",
     ("Міноборони", "Питання, що належать до Міністерства оборони. Цілодобово; дзвінки записуються."),
     ("Ministry of Defence", "Matters within the Ministry of Defence's remit. 24/7; calls are recorded.")),
    ("s-nib", "security", "1648", NIB,
     ("Національне інформаційне бюро", "Повідомити про полонених, депортованих, зниклих безвісти, зокрема дітей. Цілодобово."),
     ("National Information Bureau", "Report people captured, deported or missing, including children. 24/7.")),
    ("s-nib-abroad", "security", "+380442878165", NIB,
     ("НІБ з-за кордону", "Те саме, що 1648, для дзвінків з-за кордону. Цілодобово, платно."),
     ("NIB from abroad", "The same as 1648, for calls from abroad. 24/7, paid.")),
    ("s-missing", "security", "1698", "https://www.kmu.gov.ua/news/hariachi-linii-ofisu-upovnovazhenoho-z-pytan-osib-znyklykh-bezvisty-za-osoblyvykh-obstavyn",
     ("Зниклі безвісти", "Уповноважений з питань осіб, зниклих безвісти за особливих обставин; єдиний реєстр. Пн–сб 09:00–18:00."),
     ("Missing persons", "Commissioner for persons missing under special circumstances; the single register. Mon–Sat 09:00–18:00.")),
    ("s-pow", "security", "0800300529", "https://www.kmu.gov.ua/news/yakshcho-liudyna-potrapyla-v-polon-kudy-povidomliaty",
     ("Координаційний штаб: військовополонені", "Порада родинам полонених і зниклих безвісти військових."),
     ("POW Coordination HQ", "Advice for families of captured or missing service members.")),
    ("s-pow-sbu", "security", "0443211121", "https://ssu.gov.ua/obiednanyi-tsentr-z-koordynatsii-poshuku-ta-zvilnennia-viiskovopolonenykh-nezakonno-pozbavlenykh-voli-osib-vnaslidok-ahresii-proty-ukrainy",
     ("Центр з пошуку і звільнення полонених", "Полонені, незаконно утримувані цивільні, депортовані діти. Міський номер Києва, за тарифом оператора."),
     ("Centre for search and release of POWs", "Prisoners of war, illegally detained civilians, deported children. Kyiv landline, normal call rates.")),

    ("vt-1528", "veterans", "1528", "https://www.kmu.gov.ua/news/1528-novyi-nomer-iedynoi-veteranskoi-linii",
     ("Єдина ветеранська лінія", "Статус і посвідчення, пільги, психологічна підтримка, житло, робота. Щодня 08:00–20:00; консультації безкоштовні."),
     ("Unified veterans line", "Status and ID, benefits, psychological support, housing, work. Daily 08:00–20:00; advice is free.")),
    ("vt-uvf", "veterans", "0800332029", "https://veteranfund.com.ua/en/2023/01/14/the-uvf-crisis-support-hotline/",
     ("Ветеранський фонд: кризова лінія", "Психологічна кризова підтримка для ветеранів і родин. Цілодобово, безкоштовно."),
     ("Veterans Fund crisis line", "Psychological crisis support for veterans and families. 24/7, free.")),
    ("vt-hub", "veterans", "0673482868", "https://veteranhub.com.ua/",
     ("Veteran Hub", "Ветерани, військові, родини полонених і зниклих. Щодня 09:00–21:00; дзвінок за тарифом оператора."),
     ("Veteran Hub", "Veterans, service members, families of captives and the missing. Daily 09:00–21:00; normal call rates.")),

    ("st-1545", "state", "1545", GOV1545,
     ("Урядова гаряча лінія", "Звернення до будь-якого органу влади. Безкоштовно з основних операторів."),
     ("Government hotline", "Appeals to any government body. Free from the main operators.")),
    ("st-1545-abroad", "state", "+380442841915", GOV1545,
     ("Урядова лінія з-за кордону", "Те саме, що 1545, для дзвінків з-за кордону. Платно."),
     ("Government hotline from abroad", "The same as 1545, for calls from abroad. Paid.")),
    ("st-legal", "state", "0800213103", LEGAL,
     ("Безоплатна правнича допомога", "Порада юриста пн–пт 08:00–18:00; повідомлення про затримання — цілодобово. Безкоштовно."),
     ("Free legal aid", "A lawyer's advice Mon–Fri 08:00–18:00; reporting a detention 24/7. Free.")),
    ("st-legal-abroad", "state", "+380443631041", LEGAL,
     ("Правнича допомога з-за кордону", "Те саме, для дзвінків з-за кордону. Платно."),
     ("Legal aid from abroad", "The same, for calls from abroad. Paid.")),
    ("st-ombudsman", "state", "1678", OMB,
     ("Омбудсман", "Порушені права, зокрема полонені й депортовані цивільні. Пн–чт 09:00–18:00, пт до 16:45; безкоштовно."),
     ("Ombudsman", "Rights violated, including captured or deported civilians. Mon–Thu 09:00–18:00, Fri to 16:45; free.")),
    ("st-ombudsman-0800", "state", "0800501720", "https://cyberpolice.gov.ua/contacts/",
     ("Омбудсман: другий номер", "Те саме, що 1678; той самий графік."),
     ("Ombudsman: second number", "The same as 1678; same hours.")),
    ("st-ombudsman-abroad", "state", "+380442997408", OMB,
     ("Омбудсман з-за кордону", "Для дзвінків з-за кордону. Платно."),
     ("Ombudsman from abroad", "For calls from abroad. Paid.")),
    ("st-pension", "state", "0800503753", "https://www.pfu.gov.ua/2177797-rozshyreno-mozhlyvosti-kontakt-tsentru-pensijnogo-fondu-ukrayiny/",
     ("Пенсійний фонд", "Пенсії та соціальні виплати. Пн–пт 08:00–20:00, сб 08:00–14:00."),
     ("Pension Fund", "Pensions and social payments. Mon–Fri 08:00–20:00, Sat 08:00–14:00.")),
    ("st-social", "state", "0800505213", UKC,
     ("Соціальна сервісна служба", "Соціальні допомоги й послуги. Пн–чт 10:30–15:00, пт до 14:45."),
     ("National Social Service", "Social benefits and services. Mon–Thu 10:30–15:00, Fri to 14:45.")),
    ("st-diia", "state", "0672580520", "https://thedigital.gov.ua/contacts",
     ("Підтримка Дії", "Застосунок і портал Дія. Мобільний номер, за тарифом оператора."),
     ("Diia support", "The Diia app and portal. Mobile number, normal call rates.")),
    ("st-police", "state", "0800500202", "https://npu.gov.ua/kontakti",
     ("Поліція: не екстрене", "Звернення до поліції, що не потребують негайного виїзду. Пн–пт 09:00–18:00. Екстрене — 102."),
     ("Police: non-emergency", "Police matters that don't need an immediate response. Mon–Fri 09:00–18:00. Emergencies: 102.")),
    ("st-cyber", "state", "0800505170", "https://cyberpolice.gov.ua/contacts/",
     ("Кіберполіція", "Шахрайство, кіберзлочини, стан звернення. Пн–чт 09:00–18:00, пт до 17:00."),
     ("Cyber police", "Fraud, cybercrime, status of a report. Mon–Thu 09:00–18:00, Fri to 17:00.")),
    ("st-mvs", "state", "1536", "https://mvs.gov.ua/en/work-with-citizens/telefonna-gariaca-liniia-mvs-ukrayini",
     ("МВС", "Звернення до Міністерства внутрішніх справ, довідки."),
     ("Ministry of Internal Affairs", "Appeals and enquiries to the Interior Ministry.")),
    ("st-border", "state", "1598", "https://dpsu.gov.ua/uk/main-contacts",
     ("Прикордонна служба", "Питання перетину кордону."),
     ("Border Guard Service", "Questions about crossing the border.")),
    ("st-border-abroad", "state", "+380445276363", "https://dpsu.gov.ua/uk/main-contacts",
     ("Прикордонна служба з-за кордону", "Те саме, що 1598, для дзвінків з-за кордону. Платно."),
     ("Border Guard from abroad", "The same as 1598, for calls from abroad. Paid.")),
    ("st-mfa-centre", "state", "0442245720", UKC,
     ("МЗС: контакт-центр", "Звернення до Міністерства закордонних справ. Цілодобово; міський номер Києва."),
     ("Foreign Ministry contact centre", "Enquiries to the Foreign Ministry. 24/7; Kyiv landline.")),
    ("st-mfa", "state", "0442381588", UKC,
     ("МЗС: кризова лінія", "Громадяни України в біді за кордоном. Міський номер Києва, за тарифом оператора."),
     ("Foreign Ministry crisis line", "Ukrainian citizens in trouble abroad. Kyiv landline, normal call rates.")),
]

def build(lang):
    out = []
    for id, cat, number, source, uk, en in P:
        title, note = uk if lang == "uk" else en
        assert re.fullmatch(r"[a-z0-9-]{1,40}", id), id
        assert cat in CATEGORIES, id
        # The same rule as PhonesReader in the app: +380 and 9 digits from abroad, else 3-10 digits
        assert re.fullmatch(r"\+380[0-9]{9}|[0-9]{3,10}", number), id
        assert re.fullmatch(r"https://[a-z0-9.-]+(/\S*)?", source), id
        assert "t.me" not in source and "telegram" not in source, id
        assert 0 < len(title) <= 80 and 0 < len(note) <= 240, id
        out.append({"id": id, "category": cat, "title": title, "note": note, "number": number, "source": source, "checkedOn": CHECKED})
    assert len({p["id"] for p in out}) == len(out)
    # In category order, as shown
    return sorted(out, key=lambda p: CATEGORIES.index(p["category"]))

if __name__ == "__main__":
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "content", "src", "main", "assets")
    for lang, path in (("uk", "phones.json"), ("en", os.path.join("en", "phones.json"))):
        with open(os.path.join(root, path), "w", encoding="utf-8") as f:
            json.dump(build(lang), f, ensure_ascii=False, indent=1)
            f.write("\n")
    print(len(P), "numbers", file=sys.stderr)
