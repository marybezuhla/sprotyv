# Спротив · Sprotyv

Безкоштовний застосунок для Android: що робити просто зараз, коли небезпечно, і як підготуватися заздалегідь. Українська та англійська. Без реклами, акаунтів і стеження.

> **Статус: розробка.** Ще немає релізу для встановлення. Помилки й побажання: [Issues](../../issues).

## Що буде

- **Що робити зараз:** покрокові картки для тривоги, кровотечі, дрона, пожежі, завалу та інших ситуацій.
- **Довідник** виживання й базової медицини для дорослих і дітей — повністю офлайн.
- **Побут у кризі:** немає світла, тепла, води, газу чи зв'язку.
- **Я в безпеці:** готове повідомлення рідним через WhatsApp, Signal або SMS.
- **Моє:** медкартка, сімейний план, тривожна валіза — зашифровано, лише на телефоні.
- **Подзвонити:** 112, 101, 102, 103, 104 одним дотиком на кожному екрані.
- **Тривоги** — з версії 1.1. До того тримайте офіційний застосунок «Повітряна тривога» й слухайте сирени.

## Прозорість

- **Мережа:** версія 1.0 не має дозволу на інтернет; збірка зупиняється, якщо він з'являється. З 1.1 застосунок лише завантажує тривоги й вміст і нічого не надсилає.
- **Шифрування:** особисті дані — у базі SQLCipher, ключ у захищеному сховищі телефона. Хмарну копію Android вимкнено.
- **Вміст:** кожна картка має джерело й дату перевірки; що не рецензовано фахівцем, позначено.
- **Збірка:** APK збирається з цього коду в GitHub Actions окремо від тестів, з перевіркою контрольних сум залежностей, і підписується лише після схвалення автора. Відбиток сертифіката SHA-256: `d00241499cd44d0859c5762a0b4cc317862358c8df3210fc3a0e9473e038a1ac`.
- **Межі захисту:** щоб у небезпеці застосунок відкривався миттєво, ключ не вимагає відбитка чи PIN-коду щоразу. Тому будь-хто з розблокованим телефоном, зокрема якщо вас змусять його розблокувати, побачить ваші дані. Без блокування екрана (або коли його тримає відкритим Smart Lock) захист від сторонніх відсутній. Окремий вхід за відбитком чи PIN-кодом з'явиться згодом. Увага: на деяких версіях Android після зняття блокування екрана ключ стає непридатним, і збережені дані вже не відкриються (довідник і дзвінки працюватимуть). Перевіряємо це на реальних телефонах; робіть резервну копію, коли вона з'явиться. Коли застосунок іде з екрана, база закривається; уже показаний на екрані текст лишається в пам'яті застосунку до повернення, щоб не загубити недописане (знімки екрана при цьому заборонені). Клавіатура телефона може запам'ятовувати набрані слова — для чутливого тексту вимкніть у ній навчання. Якщо телефон не має захищеного апаратного сховища ключів, застосунок про це попередить.
- **Межі порад:** застосунок дає загальні рекомендації й не замінює лікаря, рятувальників, офіційних вказівок і сирен.
- **Як створено:** код написано за допомогою ШІ (Claude, Anthropic) під керівництвом і з тестуванням автора.

Kotlin · Jetpack Compose · Room · SQLCipher. Сторонні матеріали — [NOTICE.md](NOTICE.md). Ліцензія коду GPL-3.0.

---

# Sprotyv · English

A free Android app: what to do right now when it is dangerous, and how to prepare in advance. Ukrainian and English. No ads, no accounts, no tracking.

> **Status: in development.** No release to install yet. Bugs and wishes: [Issues](../../issues).

## What is coming

- **What to do now:** step-by-step cards for an air alert, bleeding, a drone, fire, rubble and more.
- **A guide** to survival and basic medicine for adults and children, fully offline.
- **Life in a crisis:** no power, heat, water, gas or connection.
- **I am safe:** a ready message to family through WhatsApp, Signal or SMS.
- **Mine:** medical card, family plan, emergency kit — encrypted, on the phone only.
- **Call:** 112, 101, 102, 103, 104 in one tap on every screen.
- **Air alerts** from version 1.1. Until then keep the official "Air Alert" app and listen to the sirens.

## Transparency

- **Network:** version 1.0 has no internet permission; the build stops if one appears. From 1.1 the app only downloads alerts and content and sends nothing.
- **Encryption:** personal data is in an SQLCipher database with its key in the phone's secure keystore. Android cloud backup is off.
- **Content:** every card names its source and check date; anything not reviewed by a specialist is marked.
- **Build:** the APK is built from this code in GitHub Actions apart from the tests, with dependency checksums verified, and signed only after the author's approval. Certificate SHA-256: `d00241499cd44d0859c5762a0b4cc317862358c8df3210fc3a0e9473e038a1ac`.
- **Protection limits:** so the app opens at once in danger, the key needs no fingerprint or PIN each time. Anyone holding the phone unlocked, including if you are forced to unlock it, can see your data. With no screen lock (or when Smart Lock keeps it open) there is no protection from others. A separate fingerprint or PIN lock comes later. Note: on some Android versions, removing the screen lock makes the key unusable and the saved data can no longer be opened (the guide and calls keep working). We are checking this on real phones; keep a backup once that feature arrives. The database is closed when the app leaves the screen; text already on screen stays in the app's memory until you return, so nothing half-typed is lost (screenshots stay blocked). The phone's keyboard may learn typed words: for sensitive text, turn off its learning. If the phone has no secure hardware for keys, the app says so.
- **Advice limits:** general advice; it does not replace a doctor, rescuers, official instructions or sirens.
- **How it was made:** written with AI assistance (Claude, Anthropic), directed and tested by the author.

Kotlin · Jetpack Compose · Room · SQLCipher. Third-party material: [NOTICE.md](NOTICE.md). Code licence GPL-3.0.
