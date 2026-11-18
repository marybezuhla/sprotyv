# Спротив · Sprotyv

Безкоштовний застосунок для Android: що робити просто зараз, коли небезпечно, і як підготуватися заздалегідь. Українська та англійська. Без реклами, акаунтів і стеження, інтернет не потрібен. Потрібен Android 12 або новіший.

> Помилки й побажання: [Issues](../../issues).

## Особливості

- 🆘 **Що робити зараз.** Покрокові картки: тривога, кровотеча, людина вдавилася, дрон, пожежа, завал та інше. Біля кожної картки джерело й дата перевірки.
- 📖 **Довідник** виживання й базової медицини, порядки домедичної допомоги МОЗ, Конституція й Правила дорожнього руху повним текстом, «Зброя і закон» стисло.
- 🕯️ **Побут у кризі:** немає світла, тепла, води, газу чи зв'язку.
- ☎️ **Телефони.** Перевірені номери служб і гарячих ліній з джерелом і датою; 112 у кутку кожного екрана.
- ✅ **Я в безпеці.** Готове повідомлення рідним через WhatsApp, Signal або SMS.
- 🔒 **Моє.** Медкартка, сімейний план, довірені люди, тривожна валіза, запаси, нотатки, документи. Зашифровано й лише на телефоні; резервна копія з паролем.
- 🛡️ **Готовність.** Ролі, шлях готовності, щоденний гарт, щити й лічильники добрих справ, де навчитися.
- 🧒 **Для різних людей:** простий вигляд із шістьма великими кнопками, дитячий режим, режим поїздки, нейтральна іконка «Нотатки», віджети.

Тривоги застосунок не показує: тримайте офіційний застосунок «Повітряна тривога» й слухайте сирени.

## Встановлення

- 📲 **Напряму:** завантажте файл `.apk` звідси: [github.com/marybezuhla/sprotyv/releases/latest](https://github.com/marybezuhla/sprotyv/releases/latest).
- 🔄 **З оновленнями:** скористайтеся безкоштовним застосунком Obtainium. Його можна встановити з F-Droid: [f-droid.org/packages/dev.imranr.obtainium.fdroid](https://f-droid.org/packages/dev.imranr.obtainium.fdroid) (F-Droid — це безкоштовний каталог застосунків для Android, щось на зразок Google Play. Усі застосунки там мають відкритий код, без реклами й стеження, а перед публікацією їх перевіряють і збирають самі укладачі каталогу). В Obtainium натисніть «Додати застосунок» і вставте посилання `github.com/marybezuhla/sprotyv`.

## Прозорість

- **Мережа:** дозволу на інтернет немає; збірка зупиняється, якщо він з'являється.
- **Шифрування:** особисті дані в базі SQLCipher, ключ у захищеному сховищі телефона; хмарну копію Android вимкнено. Щоб застосунок відкривався миттєво, типово ключ не просить відбитка: хто тримає розблокований телефон, побачить дані. «Моє» можна закрити відбитком чи PIN-кодом, а все стерти — у налаштуваннях («Якщо телефон у чужих руках»). Подробиці — у [PRIVACY.md](PRIVACY.md).
- **Вміст:** кожна картка має джерело й дату перевірки; що не рецензовано фахівцем, позначено.
- **Підпис:** справжній APK підписано сертифікатом із відбитком SHA-256 `d00241499cd44d0859c5762a0b4cc317862358c8df3210fc3a0e9473e038a1ac`. Перевірити: `apksigner verify --print-certs sprotyv-<версія>.apk` (з Android SDK Build Tools) має показати саме цей відбиток.
- **Збірка:** APK збирається з цього коду в GitHub Actions з перевіркою контрольних сум залежностей і підписується лише після схвалення авторки.
- **Як створено:** код написано за допомогою ШІ (Claude, Anthropic) під керівництвом і з тестуванням авторки.
- **Застереження:** поради загальні й не замінюють лікаря, рятувальників, офіційних вказівок і сирен.

Kotlin · Jetpack Compose · Room · SQLCipher. Ліцензія коду GNU GPL версії 3 або (на ваш вибір) будь-якої пізнішої (GPL-3.0-or-later), довідника CC BY-SA 4.0. Бібліотеки й джерела — [NOTICE.md](NOTICE.md), приватність — [PRIVACY.md](PRIVACY.md), вразливості — [SECURITY.md](SECURITY.md).

---

# Sprotyv · English

A free Android app: what to do right now when it is dangerous, and how to prepare in advance. Ukrainian and English. No ads, no accounts, no tracking, no internet needed. Requires Android 12 or newer.

> Bugs and wishes: [Issues](../../issues).

## Features

- 🆘 **What to do now.** Step-by-step cards: an air alert, bleeding, choking, a drone, fire, rubble and more. Every card shows its source and check date.
- 📖 **A guide** to survival and basic medicine, the Ministry of Health first aid procedures, the Constitution and the Rules of the Road in full, "Weapons and the law" in brief.
- 🕯️ **Life in a crisis:** no power, heat, water, gas or connection.
- ☎️ **Phones.** Checked numbers of services and hotlines with source and date; 112 in the corner of every screen.
- ✅ **I am safe.** A ready message to family through WhatsApp, Signal or SMS.
- 🔒 **My data.** Medical card, family plan, trusted people, go-bag, supplies, notes, documents. Encrypted and only on the phone; a backup with a password.
- 🛡️ **Readiness.** Roles, a readiness path, daily resilience, shields and counts of good deeds, where to learn.
- 🧒 **For different people:** a simple look with six large buttons, a kids' mode, trip mode, a neutral "Notes" icon, widgets.

The app does not show air alerts: keep the official "Air Alert" app and listen to the sirens.

## Install

- 📲 **Directly:** download the `.apk` file from [github.com/marybezuhla/sprotyv/releases/latest](https://github.com/marybezuhla/sprotyv/releases/latest).
- 🔄 **With updates:** use the free app Obtainium, available on F-Droid: [f-droid.org/packages/dev.imranr.obtainium.fdroid](https://f-droid.org/packages/dev.imranr.obtainium.fdroid) (F-Droid is a free catalogue of Android apps, a bit like Google Play: every app there is open source, without ads or tracking, and is checked and built by the catalogue's maintainers before publishing). In Obtainium tap "Add App" and paste `github.com/marybezuhla/sprotyv`.

## Transparency

- **Network:** no internet permission; the build stops if one appears.
- **Encryption:** personal data is in an SQLCipher database with its key in the phone's secure keystore; Android cloud backup is off. So the app opens at once, by default the key asks for no fingerprint: anyone holding the phone unlocked can see the data. "My data" can be locked with a fingerprint or PIN, and everything can be erased in settings ("If the phone is in the wrong hands"). Details: [PRIVACY.md](PRIVACY.md).
- **Content:** every card names its source and check date; anything not reviewed by a specialist is marked.
- **Signature:** the genuine APK is signed with a certificate whose SHA-256 fingerprint is `d00241499cd44d0859c5762a0b4cc317862358c8df3210fc3a0e9473e038a1ac`. To check it: `apksigner verify --print-certs sprotyv-<version>.apk` (from the Android SDK Build Tools) must show this fingerprint.
- **Build:** the APK is built from this code in GitHub Actions with dependency checksums verified, and signed only after the author's approval.
- **How it was made:** the code was written with AI (Claude, Anthropic), directed and tested by the author.
- **Disclaimer:** advice is general and does not replace a doctor, rescuers, official instructions or sirens.

Kotlin · Jetpack Compose · Room · SQLCipher. Licence: code under GNU GPL version 3 or (at your option) any later version (GPL-3.0-or-later), the guide under CC BY-SA 4.0. Libraries and sources: [NOTICE.md](NOTICE.md), privacy: [PRIVACY.md](PRIVACY.md), vulnerabilities: [SECURITY.md](SECURITY.md).
