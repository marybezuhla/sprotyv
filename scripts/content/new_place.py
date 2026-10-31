#!/usr/bin/env python3
"""
"New place": what to do after leaving home for another region, step by step (first days, the first
week, the first month, three to six months), and a short part for those abroad. Each step: the
action first, a few plain lines, where to go, official sources and the date checked. Written for
Sprotyv from the sources named (scripts/content/adaptation/RESEARCH.md); amounts only with a date,
and rules that are changing are said to be changing.

    python3 scripts/content/new_place.py  ->  content/src/main/assets/new_place.json
"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "content", "src", "main", "assets", "new_place.json")
CHECKED = "31.10.2026"

LAW = ("Закон України № 4924-IX «Про забезпечення прав і свобод внутрішньо переміщених осіб» (чинний з 22.10.2026)", "https://zakon.rada.gov.ua/laws/show/4924-20")
MSP_VPO = ("Мінсоцполітики: нова система обліку ВПО, довідки залишаються чинними", "https://www.msp.gov.ua/press-center/news/vpo-dovidky-nova-systema")
PFU = ("Пенсійний фонд: як ВПО оформити допомогу на проживання", "https://www.pfu.gov.ua/tr/294135-yak-vnutrishno-peremishhenym-osobam-oformyty-dopomogu-na-prozhyvannya/")

# stage: days (first 3 days), week, month, later (3-6 months), abroad
STEPS = [
    dict(id="shelter", stage="days", title="Знайти безпечний нічліг",
         text="Тимчасове житло громади безоплатне — платите лише за комунальні послуги. Заява — до виконавчого органу ради або ЦНАП за місцем обліку ВПО. Перевагу мають багатодітні й родини з дітьми, вагітні, люди з інвалідністю, родини військових, ті, хто переїжджає вдруге. Вільного житла може не бути одразу — вас поставлять у чергу.",
         where="ЦНАП або виконавчий орган ради громади",
         sources=[LAW, ("ДТКТ: порядок надання ВПО житла для тимчасового проживання (постанова КМУ № 495)", "https://news.dtkt.ua/society/community/88128-udoskonaleno-poriadok-nadannia-vpo-zitla-dlia-timcasovogo-prozivannia")]),
    dict(id="safe", stage="days", title="Сказати рідним, що ви в безпеці",
         text="Одне коротке повідомлення знімає паніку й звільняє лінії. Нову адресу запишіть у «Моєму», щоб вона була під рукою.",
         where="«Я в безпеці» у «Спротиві»", route="safe", sources=[]),
    dict(id="documents", stage="days", title="Перевірити документи",
         text="Якщо паспорт загубився, його відновлюють у будь-якому ЦНАП, підрозділі міграційної служби чи «Паспортному сервісі» там, де ви зараз. Якщо викрали — спершу поліція. Поки нового немає, особу тимчасово підтверджують закордонний паспорт, посвідчення водія чи документи в Дії.",
         where="ЦНАП, міграційна служба, «Паспортний сервіс»",
         sources=[("Кабмін / ДМС: покроковий алгоритм дій у разі втрати паспорта", "https://www.kmu.gov.ua/news/dms-pokrokovij-algoritm-dij-u-razi-vtrati-pasporta-pid-chas-vijni"),
                  ("Державна міграційна служба: що робити, якщо втрачено паспорт", "https://dmsu.gov.ua/news/region/17989.html")]),

    dict(id="idp", stage="week", title="Стати на облік як ВПО",
         text="Заяву подають у Дії, у ЦНАП або в органі соцзахисту. Безоплатно. Поступово замість довідки видаватимуть витяг з єдиної бази, а поки видають довідки. Видані раніше довідки чинні, переоформлювати нічого не треба. Порядок обліку оновлюють — перевірте перед зверненням.",
         where="Дія, ЦНАП, соцзахист", sources=[LAW, MSP_VPO]),
    dict(id="move-notice", stage="week", title="Переїхали ще раз — повідомте протягом 30 днів",
         text="Про нове місце проживання чи повернення додому ВПО повідомляє протягом 30 календарних днів. Інакше вас можуть зняти з обліку.",
         where="ЦНАП або виконавчий орган ради", sources=[LAW]),
    dict(id="allowance", stage="week", title="Подати заяву на допомогу на проживання",
         text="Станом на 31.10.2026: 3000 грн на місяць дітям і людям з інвалідністю, 2000 грн іншим, на 6 місяців. Хто має право і чи продовжать — залежить від того, звідки ви виїхали, доходу й складу родини. Працездатним, які звертаються вперше, треба протягом 3 місяців працевлаштуватися, стати ФОП або стати на облік у центрі зайнятості. Правила змінюються: перевіряйте в Пенсійному фонді.",
         where="Дія, портал чи застосунок Пенсійного фонду, ЦНАП",
         sources=[PFU, ("Пенсійний фонд: умови продовження виплат ВПО у 2026 році", "https://www.pfu.gov.ua/kh/261830-vyplaty-vpo-u-2026-rotsi-umovy-prodovzhennya-vyplat/")]),
    dict(id="school", stage="week", title="Записати дітей до школи й садка",
         text="Діти ВПО мають право на садок і школу за місцем обліку одного з батьків. До школи беруть і без свідоцтва про народження чи реєстрації, медичну довідку можна донести до початку навчання. Учні комунальних шкіл, які навчаються очно чи змішано, з 1.09.2026 харчуються безоплатно.",
         where="Школа чи садок поруч, відділ освіти громади",
         sources=[LAW, ("osvita.ua: зарахування до школи дітей ВПО (наказ МОН № 367)", "https://osvita.ua/school/86392/"), ("Кабмін: безоплатне харчування для всіх учнів комунальних шкіл", "https://www.kmu.gov.ua/news/usi-uchni-shcho-navchaiutsia-v-komunalnykh-shkolakh-otrymaiut-bezoplatne-hariache-kharchuvannia-z-novoho-navchalnoho-roku")]),
    dict(id="doctor", stage="week", title="Знайти сімейного лікаря",
         text="Первинну допомогу дають і без реєстрації та декларації. Підпишіть нову декларацію з лікарем там, де живете: він випише рецепти, зокрема за «Доступними ліками». Найближчий заклад підкаже контакт-центр НСЗУ 16-77.",
         where="Найближча амбулаторія, НСЗУ 16-77",
         sources=[("МОЗ: переселенцям надають медичні послуги незалежно від реєстрації", "https://moz.gov.ua/uk/pereselencjam-majut-nadavati-medichni-poslugi-nezalezhno-vid-miscja-prozhivannja-chi-reestracii")]),

    dict(id="damage", stage="month", title="Зафіксувати втрачене майно",
         text="Пошкоджене чи знищене житло — повідомлення і заява в «єВідновлення» через Дію, ЦНАП або нотаріуса. Окремо — заява до міжнародного Реєстру збитків через Дію: він фіксує збиток для майбутньої компенсації. Не ризикуйте життям заради фото: житло в зоні бойових дій обстежують дистанційно.",
         where="Дія, ЦНАП, нотаріус",
         sources=[("Дія: пошкоджене майно (єВідновлення)", "https://diia.gov.ua/services/poshkodzhene-majno"),
                  ("Реєстр збитків для України (Рада Європи): усі категорії відкрито", "https://www.coe.int/be/web/portal/-/register-of-damage-for-ukraine-now-fully-open-for-compensation-claims-1")]),
    dict(id="work", stage="month", title="Робота або своя справа",
         text="Центр зайнятості допоможе з пошуком роботи і перенавчанням, розповість про ваучер на навчання. Є гранти на власну справу (для ВПО з досвідом — +20 %) і компенсації роботодавцям, які беруть ВПО. Контакт-центр служби зайнятості — 1518, безкоштовно.",
         where="Центр зайнятості, Дія",
         sources=[("Кабмін: контакт-центр служби зайнятості 1518 і ваучер на навчання", "https://www.kmu.gov.ua/news/kontakt-tsentr-sluzhby-zainiatosti-vidpovidaie-khto-maie-pravo-na-vaucher-dlia-navchannia"),
                  ("Дія: грант на власну справу", "https://diia.gov.ua/services/zapochatkuvannia-ta-masshtabuvannia-vlasnoi-spravy")]),
    dict(id="legal", stage="month", title="Безоплатна правнича допомога",
         text="Документи, житло, виплати, борги — юрист проконсультує безоплатно. Телефон 0 800 213 103, пн–пт 8:00–18:00, дзвінок безкоштовний.",
         where="0 800 213 103",
         sources=[("Судова влада України: безоплатна правнича допомога", "https://court.gov.ua/press/news/2084499/")]),
    dict(id="council", stage="month", title="Знайти своїх: раду ВПО і земляків",
         text="У багатьох громадах діють ради з питань ВПО; за примірним положенням половина членів — переселенці. Їхні пропозиції влада зобов'язана розглянути. Приходьте на засідання, пропонуйте, шукайте земляків.",
         where="Виконавчий орган ради, ЦНАП, центр соцпослуг",
         sources=[LAW, ("Мінсоцполітики: законодавство для ВПО (положення про ради ВПО)", "https://www.msp.gov.ua/otrymuvacham-soc-pidtrymky/vnutrishno-peremishchenym-osobam/zakonodavstvo-dlya-vpo")]),

    dict(id="housing", stage="later", title="Житло надовше",
         text="Оренда, програма «єОселя» та житлові програми для ВПО. Житлові ваучери поки що лише для ВПО з окупованих територій, які мають статус учасника бойових дій або інвалідність унаслідок війни. Умови змінюються: перевіряйте в Дії.",
         where="Дія, «єОселя»",
         sources=[("Кабмін: програма житлових ваучерів для ВПО з окупованих територій", "https://www.kmu.gov.ua/news/prohrama-zhytlovykh-vaucheriv-dlia-vnutrishno-peremishchenykh-osib-z-tot-ponad-25-tysiach-zaiav-za-misiats"),
                  ("єОселя", "https://eoselia.diia.gov.ua/")]),
    dict(id="community", stage="later", title="Стати частиною нової громади",
         text="Волонтерство, добровольчі формування територіальних громад (ДФТГ), допомога іншим переселенцям, сусідська мережа. Ваші навички потрібні тут: так нове місце стає своїм.",
         where="«Спротив»: що може кожен", route="resistance", sources=[]),
    dict(id="roots", stage="later", title="Тримати зв'язок із рідним краєм",
         text="Спільнота земляків, історія вашого села чи міста, пам'ять про загиблих, плани на повернення. Громада, яку не розсіяли, — теж спротив.",
         where="Рада ВПО, громадські організації земляків", sources=[]),
    dict(id="mind", stage="later", title="Подбати про себе",
         text="Сум, злість, безсоння після втрати дому — нормальні реакції. Допомагає рутина, рух, сон, розмова з близькими. Безоплатну психологічну допомогу можна отримати через сімейного лікаря.",
         where="Сімейний лікар; «Куди звернутися»", route="directory",
         sources=[("НСЗУ: безоплатна психологічна та психіатрична допомога в Програмі медичних гарантій", "https://www.kmu.gov.ua/news/bezoplatna-psykholohichna-ta-psykhiatrychna-dopomoha-v-prohrami-medychnykh-harantii")]),

    dict(id="abroad-protection", stage="abroad", title="Тимчасовий захист у ЄС",
         text="Тимчасовий захист для українців у ЄС продовжено до 4 березня 2028 року. Нові заявники, на яких поширюються військові обов'язки, мають показати, що виконали їх в Україні (наприклад, законний виїзд чи відстрочка). Тих, хто вже має захист, це не стосується.",
         where="Міграційна служба країни перебування",
         sources=[("EUR-Lex: Рішення Ради ЄС (EU) 2026/1912", "https://eur-lex.europa.eu/eli/dec_impl/2026/1912/2026-08-04"),
                  ("Представництво ЄС в Україні: захист продовжено до березня 2028", "https://www.eeas.europa.eu/delegations/ukraine/eu-countries-agree-extend-temporary-protection-those-fleeing-ukraine-until-march-2028_en")]),
    dict(id="abroad-consul", stage="abroad", title="Тимчасовий консульський облік",
         text="Станьте на облік у посольстві чи консульстві: особисто, через законного представника або рекомендованим листом. Так простіше з документами й допомогою.",
         where="Посольство або консульство України",
         sources=[("Дія: тимчасовий консульський облік", "https://guide.diia.gov.ua/view/vziattia-na-tymchasovyi-konsulskyi-oblik-30ab0704-12f0-4708-b656-accb5135d2e3")]),
    dict(id="abroad-status", stage="abroad", title="Статус ВПО і зв'язок з домом",
         text="Статус ВПО знімають, якщо людина перебуває за кордоном понад 90 днів поспіль або понад 180 днів протягом року, крім поважних причин, визначених урядом. Дія і заява до Реєстру збитків доступні й з-за кордону.",
         where="Дія", sources=[LAW]),
    dict(id="abroad-contribution", stage="abroad", title="Ваш внесок — і звідти",
         text="Волонтерство, збори, правда про війну в новій країні, українська мова й культура для дітей, зв'язок із рідними вдома. Ви — частина спротиву, де б не були.",
         where="«Підтримати країну» у «Спротиві»", route="resistance", sources=[]),
]

STAGES = ["days", "week", "month", "later", "abroad"]
ROUTES = {"safe", "resistance", "directory"}


def words(s):
    return len(re.findall(r"[\w'’-]+", s))


def main():
    ids = [s["id"] for s in STEPS]
    if len(ids) != len(set(ids)):
        sys.exit("ids repeat")
    out = []
    for s in STEPS:
        if s["stage"] not in STAGES:
            sys.exit(f"{s['id']}: stage")
        if words(s["title"]) > 9 or words(s["text"]) > 70:
            sys.exit(f"{s['id']}: too long ({words(s['title'])}/{words(s['text'])})")
        if s.get("route") and s["route"] not in ROUTES:
            sys.exit(f"{s['id']}: route")
        for _, url in s["sources"]:
            if not url.startswith("https://") or "t.me" in url:
                sys.exit(f"{s['id']}: url")
        out.append({"id": s["id"], "stage": s["stage"], "title": s["title"], "text": s["text"], "where": s["where"],
                    "route": s.get("route", ""), "sources": [{"title": t, "url": u} for t, u in s["sources"]], "checked": CHECKED})
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"title": "Нове місце", "steps": out}, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(f"New place: {len(out)} steps")


if __name__ == "__main__":
    main()
