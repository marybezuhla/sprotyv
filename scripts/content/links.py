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

def link(id, group, kind, title, note, url="", app="", phone=""):
    assert kind in ("official", "volunteer", "independent"), id
    assert re.fullmatch(r"[a-z0-9-]{1,40}", id), id
    assert (url and re.fullmatch(r"https://[a-z0-9-]+(\.[a-z0-9-]+)+(/[A-Za-z0-9._~/?=&%-]*)?", url)) or (not url and phone), id
    assert not app or re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z0-9_]+)+", app), id
    assert not phone or re.fullmatch(r"[0-9]{3,4}", phone), id
    assert 0 < len(title) <= 80 and 0 < len(note) <= 200, id
    return {"id": id, "group": group, "kind": kind, "title": title, "note": note,
            "url": url, "app": app, "phone": phone, "checkedOn": CHECKED}

LINKS = [
    # Air threats
    link("air-alert", "threats", "official", "Повітряна тривога: тривоги й рух цілей",
         "Офіційні тривоги й приблизний рух крилатих ракет і шахедів від Повітряних сил. Балістики не показує, дані із затримкою.",
         url="https://map.ukrainealarm.com", app="com.ukrainealarm"),
    link("air-force", "threats", "official", "Повітряні сили ЗСУ",
         "Попередження про напрямки ракет і дронів. Канал також пише про збиті цілі — нам важливі лише попередження.",
         url="https://t.me/kpszsu"),
    link("monitor", "threats", "volunteer", "monitor: мапа дронів",
         "Приблизне положення дронів з відкритих даних, оновлення кожні 20 секунд. Бета-версія, команда не названа, не офіційне оповіщення.",
         url="https://map.war-monitor.org"),
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
        assert l["group"] in ("threats", "front", "help", "facts"), l["id"]
        assert len(l["note"]) <= 200, l["id"]
    with open(out, "w", encoding="utf-8") as f:
        json.dump(LINKS, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(len(LINKS), "links")
