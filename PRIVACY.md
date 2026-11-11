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

## How to erase everything

In the app: Settings → “If the phone is in the wrong hands” → “Erase all data”. The keystore keys go first, then the database, documents, notes, settings and the numbers copied for widgets; the app closes. The guide stays, as it is built in.

Or uninstall the app (Android Settings → Apps → Sprotyv → Uninstall): all the same goes with it.

Delete any backups and files you saved elsewhere yourself.
