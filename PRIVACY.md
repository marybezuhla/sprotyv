# Конфіденційність

«Спротив» працює без інтернету: у нього немає дозволу на мережу, і перевірка в CI зупиняє збірку, якщо такий дозвіл чи мережевий код з'явиться. Застосунок нічого не надсилає ні нам, ні комусь іншому: немає акаунтів, аналітики, реклами, звітів про збої назовні.

## Які дані є і де вони лежать

| Що | Де | Як захищено |
|---|---|---|
| «Моє»: медкартка, довірені люди, план, валіза, запаси, нотатки, документи, позначки | на телефоні, у базі застосунку | зашифровано (SQLCipher); ключ у захищеному сховищі Android (Keystore), за бажанням відкривається лише відбитком чи PIN |
| Документи (фото, PDF) | на телефоні, окремими файлами | кожен файл зашифровано своїм ключем, ключ у базі «Мого» |
| Номери своїх для дитини (якщо ви це ввімкнули) | на телефоні | окрема копія лише імен і номерів, зашифрована ключем Keystore без відбитка; вимкнете — копію видалено |
| Нотатки до турнікета | на телефоні | зашифровано ключем Keystore, видаляються через добу |
| Налаштування вигляду, плитки головного екрана, лічильники | на телефоні | у пам'яті застосунку, без особистих записів; Android не робить їхніх копій |
| Резервна копія | лише там, куди ви самі її збережете | зашифровано вашим паролем (Argon2id + AES-GCM) |

Android не копіює дані застосунку ні в хмару, ні на новий телефон: це вимкнено в маніфесті.

## Що бачать інші

- **Коли ви ділитеся** (повідомлення «Я в безпеці», файл контактів .vcf, документ, QR-код, PDF): те, що надсилаєте, бачить застосунок, якому передаєте, і той, хто отримає. Перед кожним таким кроком застосунок попереджає. Файл .vcf і копія документа не зашифровані.
- **Дзвінки й SMS** відкриваються у стандартних застосунках телефона, **WhatsApp і Signal** — лише справжні, перевірені за підписом. Самі ці застосунки бачать номер і текст.
- **Посилання** у «Знаннях» відкриваються у вашому браузері: сайт бачить вашу адресу, як за будь-якого відвідування.
- **GitHub** бачить адресу телефона чи комп'ютера, що завантажує APK або відкриває сторінку проєкту. Те саме для Obtainium чи F-Droid, якщо ви оновлюєтеся через них.
- **Повідомлення про помилку** (якщо ви самі його надішлете) публікується відкрито на GitHub; з назв у тексті видно, якими розділами ви користувалися. Перед надсиланням ви бачите весь текст.

## Межі захисту й подробиці

- **Межі захисту:** щоб у небезпеці застосунок відкривався миттєво, типово ключ не вимагає відбитка чи PIN-коду щоразу. Тому будь-хто з розблокованим телефоном, зокрема якщо вас змусять його розблокувати, побачить ваші дані. Без блокування екрана (або коли його тримає відкритим Smart Lock) захист від сторонніх відсутній. За бажанням «Моє» можна захистити відбитком або PIN-кодом (налаштування → «Замок на «Моє»»; є «Лише PIN»): тоді ключ працює лише після підтвердження, щоразу після повернення в застосунок. Увага: на деяких версіях Android після зняття блокування екрана ключ стає непридатним, і збережені дані вже не відкриються (довідник і дзвінки працюватимуть). Перевіряємо це на реальних телефонах; робіть резервну копію. Коли застосунок іде з екрана, база закривається; уже показаний на екрані текст лишається в пам'яті застосунку до повернення, щоб не загубити недописане (знімки екрана при цьому заборонені, доки ви самі не дозволите їх у налаштуваннях; поки дозвіл увімкнено, про нього нагадує значок камери вгорі). У «Моєму» застосунок просить клавіатуру не запам'ятовувати набране; не кожна клавіатура це виконує, тож для чутливого тексту можна вимкнути в ній навчання. Із замком на «Моє» дані закриваються й тоді, коли екран телефона гасне, поки ви у виборі файлу чи в камері. Плаваючі вікна інших застосунків над екранами «Мого» приховано. Якщо телефон не має захищеного апаратного сховища ключів, застосунок про це попередить.
- **Резервна копія й код плану:** зашифровані паролем (Argon2id, 64 МБ пам'яті, + AES-256-GCM). Файл копії має нейтральну назву; на його початку є коротка позначка формату, а решта без пароля нечитабельна; копія й код плану зашифровані з різним призначенням, тож одне ніколи не відкриється як інше. Застосунок сам не копіює паролі в буфер обміну, а код плану показується без пароля, щоб одне фото екрана не дало обох. Код сімейного плану починається з короткої позначки, щоб «Спротив» його впізнав; решта без пароля нечитабельна. Пароль копії — шість випадкових слів зі списку EFF або власний (від 16 знаків), пароль плану — чотири слова, які кажуть голосом. Забутий пароль не відновити.
- **Документи:** кожен файл зашифровано окремо (AES-256-GCM, свій випадковий ключ), ключі — лише в зашифрованій базі, тож замок «Моє» захищає й файли. Переглядаються в пам'яті, розшифрований файл на диск не записується. «Поділитися» дає іншому застосунку незашифровану копію — лише після попередження; свою копію «Спротив» прибирає за хвилину після повернення. Фото понад 2400 пікселів зменшуються, прихована інформація з них прибирається. Резервна копія може містити документи або бути без них.
- **Налаштування головної:** вигляд, вибрані плитки й увімкнений захист налаштувань зберігаються без шифрування, бо головна й 112 мають відкриватися миттєво. Особистих записів там немає, лише які плитки показати, частки «Моєї готовності» для головної (наприклад, «2 з 4», без самих записів), скільки відключень і ночей в укритті пережито, лічильники «Моїх щитів», пройдені вправи, режим поїздки і чи дозволено знімки екрана в «Моєму» (дозвіл зберігається, доки ви його не вимкнете); у резервні копії Android вони не потрапляють. Позначки «Лікую й рятую» та військової служби зберігаються зашифрованими в «Моєму»: із замком вони з'являються лише після відбитка чи PIN-коду. Захист налаштувань просить PIN-код телефону у вікні самого Android, щоб плитки не змінили випадково; застосунок PIN-код не бачить. Це не замок на ваші дані. Якщо з телефона зняти блокування екрана, захист налаштувань перестає діяти. Вибір плиток може натякати, що для вас важливо (наприклад, діти чи прифронтова місцевість), тож за потреби тримайте на головній лише загальне.
- **Режим поїздки й «Нотатки»:** режим поїздки ховає «Моє» й чутливі джерела з екранів (довідник лишається) і вимикається лише PIN-кодом телефона, якщо на телефоні є блокування екрана; нічого не видаляє, тож хто змусить вас ввести PIN, побачить дані. Щоб справді прибрати все особисте з телефона, є «Стерти всі дані» (налаштування → «Якщо телефон у чужих руках»). Нейтральна назва «Нотатки» змінює лише іконку й назву на головному екрані та в недавніх застосунках.

## Як усе стерти

У застосунку: Налаштування → «Якщо телефон у чужих руках» → «Стерти всі дані». Спершу видаляються ключі в Keystore, потім база, документи, нотатки, налаштування й копії чисел у віджетах; застосунок закривається. Довідник лишається, бо він вбудований.

Або видаліть застосунок (Налаштування Android → Застосунки → «Спротив» → Видалити): разом із ним зникає все те саме.

Резервні копії та файли, які ви самі зберегли деінде, видаліть окремо.

---

# Privacy

Sprotyv works without the internet: it has no network permission, and a CI check stops the build if one or any network code appears. The app sends nothing to us or anyone else: no accounts, analytics, ads or crash reports.

## What data there is and where

| What | Where | How it is protected |
|---|---|---|
| My data: medical card, trusted people, plan, go-bag, supplies, notes, documents, marks | on the phone, in the app's database | encrypted (SQLCipher); the key is in Android's secure keystore, optionally opened only by fingerprint or PIN |
| Documents (photos, PDFs) | on the phone, as separate files | each file encrypted with its own key, kept in the My data database |
| My people's numbers for a child (if you turned this on) | on the phone | a separate copy of names and numbers only, encrypted with a keystore key that needs no fingerprint; turned off, it is deleted |
| Tourniquet notes | on the phone | encrypted with a keystore key, deleted after a day |
| Look settings, home-screen tiles, counters | on the phone | in the app's storage, no personal records; Android makes no copies |
| Backup | only where you save it yourself | encrypted with your password (Argon2id + AES-GCM) |

Android does not copy the app's data to the cloud or to a new phone: this is turned off in the manifest.

## What others see

- **When you share** ("I am safe", a .vcf contacts file, a document, a QR code, a PDF): the app you hand it to and whoever receives it see what you send. The app warns before each such step. A .vcf file and a shared document copy are not encrypted.
- **Calls and SMS** open in the phone's own apps; **WhatsApp and Signal** only when they are the genuine apps, checked by their signature. Those apps see the number and the text.
- **Links** in Knowledge open in your browser: the site sees your address, as with any visit.
- **GitHub** sees the address of the phone or computer that downloads the APK or opens the project page. The same goes for Obtainium or F-Droid if you update through them.
- **A bug report** (if you send one yourself) is posted publicly on GitHub; the names in it show which sections you used. You see the whole text before sending.

## Protection limits and details

- **Protection limits:** so the app opens at once in danger, by default the key needs no fingerprint or PIN each time. Anyone holding the phone unlocked, including if you are forced to unlock it, can see your data. With no screen lock (or when Smart Lock keeps it open) there is no protection from others. Optionally “My data” can be protected with a fingerprint or PIN (settings → Lock on “My data”; “PIN only” too): the key then works only after confirmation, every time you return to the app. Note: on some Android versions, removing the screen lock makes the key unusable and the saved data can no longer be opened (the guide and calls keep working). We are checking this on real phones; keep a backup. The database is closed when the app leaves the screen; text already on screen stays in the app's memory until you return, so nothing half-typed is lost (screenshots stay blocked unless you allow them in settings; while allowed, a camera icon at the top says so). In “My data” the app asks the keyboard not to learn what is typed; not every keyboard obeys, so for sensitive text you can turn off its learning. With the lock on “My data”, the data also closes when the phone's screen goes off while you are in a file picker or the camera. Other apps' floating windows are hidden over the screens of “My data”. If the phone has no secure hardware for keys, the app says so.
- **Backup and plan code:** encrypted with a password (Argon2id, 64 MB of memory, + AES-256-GCM). The backup file has a neutral name; it starts with a short format tag, and the rest is unreadable without the password; the backup and the plan code are encrypted for different purposes, so one never opens as the other. The app never copies passwords to the clipboard itself, and the plan code is shown without its password, so one photo of the screen never holds both. The family plan code starts with a short tag so Sprotyv knows it; the rest is unreadable without the password. The backup password is six random words from the EFF list or your own (16 characters or more); the plan password is four words said aloud. A forgotten password cannot be recovered.
- **Documents:** each file is encrypted on its own (AES-256-GCM, its own random key); the keys live only in the encrypted database, so the lock on “My data” covers the files too. They are viewed in memory; a decrypted file is never written to storage. “Share” gives another app an unencrypted copy, only after a warning; Sprotyv removes its copy a minute after you return. Photos over 2400 pixels are made smaller and lose hidden details. A backup can include the documents or leave them out.
- **Home screen settings:** the look, the chosen tiles and whether settings are locked are stored unencrypted, so the home screen and 112 open at once. They hold no personal records, only which tiles to show, the “My readiness” shares for the home screen (such as “2 of 4”, no records), how many power cuts and shelter nights were lived through, the “My shields” counters, practised drills, trip mode and whether screenshots of “My data” are allowed (kept until you turn it off), and are not in Android backups. The “I treat and save” and military service marks are kept encrypted in “My data”: with its lock on they show only after the fingerprint or PIN. The settings lock asks for the phone's PIN in Android's own window, so tiles are not changed by accident; the app never sees the PIN. It is not a lock on your data. If the phone's screen lock is removed, the settings lock stops working. The choice of tiles can hint at what matters to you (children or a frontline area, say), so keep only general tiles on the home screen if that is a concern.
- **Trip mode and “Notes”:** trip mode hides “My data” and sensitive sources from the screens (the guide stays) and turns off only with the phone's PIN while the phone has a screen lock; it deletes nothing, so anyone who makes you enter the PIN can see the data. To really remove everything personal from the phone, use “Erase all data” (Settings → “If the phone is in the wrong hands”). The neutral name “Notes” changes only the icon and name on the home screen and in recent apps.

## How to erase everything

In the app: Settings → “If the phone is in the wrong hands” → “Erase all data”. The keystore keys go first, then the database, documents, notes, settings and the numbers copied for widgets; the app closes. The guide stays, as it is built in.

Or uninstall the app (Android Settings → Apps → Sprotyv → Uninstall): all the same goes with it.

Delete any backups and files you saved elsewhere yourself.
