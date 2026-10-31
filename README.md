# Спротив · Sprotyv

Безкоштовний застосунок для Android: що робити просто зараз, коли небезпечно, і як підготуватися заздалегідь. Українська та англійська. Без реклами, акаунтів і стеження.

> **Статус: розробка.** Ще немає релізу для встановлення. Помилки й побажання: [Issues](../../issues).

## Що буде

- **Що робити зараз:** покрокові картки для тривоги, кровотечі, дрона, пожежі, завалу та інших ситуацій.
- **Довідник** виживання й базової медицини для дорослих і дітей — повністю офлайн.
- **Побут у кризі:** немає світла, тепла, води, газу чи зв'язку.
- **Я в безпеці:** готове повідомлення рідним через WhatsApp, Signal або SMS.
- **Моє:** медкартка, сімейний план, тривожна валіза — зашифровано, лише на телефоні.
- **Два вигляди:** простий (шість великих кнопок) і повний (вкладки, усі розділи, власна головна з плитками). Можна налаштувати телефон для рідних і захистити налаштування PIN-кодом телефону.
- **Подзвонити:** 112, 101, 102, 103, 104 одним дотиком на кожному екрані.
- **Тривоги** — з версії 1.1. До того тримайте офіційний застосунок «Повітряна тривога» й слухайте сирени.

## Прозорість

- **Мережа:** версія 1.0 не має дозволу на інтернет; збірка зупиняється, якщо він з'являється. З 1.1 застосунок лише завантажує тривоги й вміст і нічого не надсилає.
- **Шифрування:** особисті дані — у базі SQLCipher, ключ у захищеному сховищі телефона. Хмарну копію Android вимкнено.
- **Вміст:** кожна картка й розділ мають джерело й дату перевірки; перша допомога — за наказом МОЗ № 441. Рецензента, коли він є, названо.
- **Збірка:** APK збирається з цього коду в GitHub Actions окремо від тестів, з перевіркою контрольних сум залежностей, і підписується лише після схвалення автора. Відбиток сертифіката SHA-256: `d00241499cd44d0859c5762a0b4cc317862358c8df3210fc3a0e9473e038a1ac`.
- **Межі захисту:** щоб у небезпеці застосунок відкривався миттєво, типово ключ не вимагає відбитка чи PIN-коду щоразу. Тому будь-хто з розблокованим телефоном, зокрема якщо вас змусять його розблокувати, побачить ваші дані. Без блокування екрана (або коли його тримає відкритим Smart Lock) захист від сторонніх відсутній. За бажанням «Моє» можна захистити відбитком або PIN-кодом (налаштування → «Вхід у «Моє»»; є «Лише PIN»): тоді ключ працює лише після підтвердження, щоразу після повернення в застосунок. Увага: на деяких версіях Android після зняття блокування екрана ключ стає непридатним, і збережені дані вже не відкриються (довідник і дзвінки працюватимуть). Перевіряємо це на реальних телефонах; робіть резервну копію. Коли застосунок іде з екрана, база закривається; уже показаний на екрані текст лишається в пам'яті застосунку до повернення, щоб не загубити недописане (знімки екрана при цьому заборонені, доки ви самі не дозволите їх у налаштуваннях; поки дозвіл увімкнено, про нього нагадує смужка вгорі). Клавіатура телефона може запам'ятовувати набрані слова — для чутливого тексту вимкніть у ній навчання. Якщо телефон не має захищеного апаратного сховища ключів, застосунок про це попередить.
- **Резервна копія й код плану:** зашифровані паролем (Argon2id, 64 МБ пам'яті, + AES-256-GCM). Файл копії має нейтральну назву й жодних розпізнаваних позначок; копія й код плану зашифровані з різним призначенням, тож одне ніколи не відкриється як інше. Пароль не можна скопіювати в буфер обміну, а код плану показується без пароля, щоб одне фото екрана не дало обох. Код сімейного плану починається з короткої позначки, щоб «Спротив» його впізнав; решта без пароля нечитабельна. Пароль копії — шість випадкових слів зі списку EFF або власний (від 16 знаків), пароль плану — чотири слова, які кажуть голосом. Забутий пароль не відновити.
- **Налаштування головної:** вигляд, вибрані плитки й увімкнений захист налаштувань зберігаються без шифрування, бо головна й 112 мають відкриватися миттєво. Особистих записів там немає, лише які плитки показати, частки «Моєї готовності» для головної (наприклад, «2 з 4», без самих записів) і чи дозволено знімки екрана в «Моєму» (дозвіл вимикається при кожному перезапуску); у резервні копії Android вони не потрапляють. Захист налаштувань просить PIN-код телефону у вікні самого Android, щоб плитки не змінили випадково; застосунок PIN-код не бачить. Це не замок на ваші дані. Якщо з телефона зняти блокування екрана, захист налаштувань перестає діяти. Вибір плиток може натякати, що для вас важливо (наприклад, діти чи прифронтова місцевість), тож за потреби тримайте на головній лише загальне.
- **Режим поїздки й «Нотатки»:** режим поїздки ховає «Моє» й чутливі джерела з екранів (довідник лишається) і вимикається лише PIN-кодом телефона, якщо на телефоні є блокування екрана; нічого не видаляє, тож хто змусить вас ввести PIN, побачить дані. Нейтральна назва «Нотатки» змінює лише іконку й назву на головному екрані та в недавніх застосунках.
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
- **Two looks:** simple (six large buttons) and full (tabs, all sections, your own home screen of tiles). A phone can be set up for a relative, with settings locked by the phone's PIN.
- **Call:** 112, 101, 102, 103, 104 in one tap on every screen.
- **Air alerts** from version 1.1. Until then keep the official "Air Alert" app and listen to the sirens.

## Transparency

- **Network:** version 1.0 has no internet permission; the build stops if one appears. From 1.1 the app only downloads alerts and content and sends nothing.
- **Encryption:** personal data is in an SQLCipher database with its key in the phone's secure keystore. Android cloud backup is off.
- **Content:** every card names its source and check date; anything not reviewed by a specialist is marked.
- **Build:** the APK is built from this code in GitHub Actions apart from the tests, with dependency checksums verified, and signed only after the author's approval. Certificate SHA-256: `d00241499cd44d0859c5762a0b4cc317862358c8df3210fc3a0e9473e038a1ac`.
- **Protection limits:** so the app opens at once in danger, by default the key needs no fingerprint or PIN each time. Anyone holding the phone unlocked, including if you are forced to unlock it, can see your data. With no screen lock (or when Smart Lock keeps it open) there is no protection from others. Optionally “Mine” can be protected with a fingerprint or PIN (settings → Opening “Mine”; “PIN only” too): the key then works only after confirmation, every time you return to the app. Note: on some Android versions, removing the screen lock makes the key unusable and the saved data can no longer be opened (the guide and calls keep working). We are checking this on real phones; keep a backup. The database is closed when the app leaves the screen; text already on screen stays in the app's memory until you return, so nothing half-typed is lost (screenshots stay blocked unless you allow them in settings; while allowed, a strip at the top says so). The phone's keyboard may learn typed words: for sensitive text, turn off its learning. If the phone has no secure hardware for keys, the app says so.
- **Backup and plan code:** encrypted with a password (Argon2id, 64 MB of memory, + AES-256-GCM). The backup file has a neutral name and no recognisable marks; the backup and the plan code are encrypted for different purposes, so one never opens as the other. Passwords cannot be copied to the clipboard, and the plan code is shown without its password, so one photo of the screen never holds both. The family plan code starts with a short tag so Sprotyv knows it; the rest is unreadable without the password. The backup password is six random words from the EFF list or your own (16 characters or more); the plan password is four words said aloud. A forgotten password cannot be recovered.
- **Home screen settings:** the look, the chosen tiles and whether settings are locked are stored unencrypted, so the home screen and 112 open at once. They hold no personal records, only which tiles to show, the “My readiness” shares for the home screen (such as “2 of 4”, no records) and whether screenshots of “Mine” are allowed (that turns off at every restart), and are not in Android backups. The settings lock asks for the phone's PIN in Android's own window, so tiles are not changed by accident; the app never sees the PIN. It is not a lock on your data. If the phone's screen lock is removed, the settings lock stops working. The choice of tiles can hint at what matters to you (children or a frontline area, say), so keep only general tiles on the home screen if that is a concern.
- **Trip mode and “Notes”:** trip mode hides “Mine” and sensitive sources from the screens (the guide stays) and turns off only with the phone's PIN while the phone has a screen lock; it deletes nothing, so anyone who makes you enter the PIN can see the data. The neutral name “Notes” changes only the icon and name on the home screen and in recent apps.
- **Advice limits:** general advice; it does not replace a doctor, rescuers, official instructions or sirens.
- **How it was made:** written with AI assistance (Claude, Anthropic), directed and tested by the author.

Kotlin · Jetpack Compose · Room · SQLCipher. Third-party material: [NOTICE.md](NOTICE.md). Code licence GPL-3.0.
