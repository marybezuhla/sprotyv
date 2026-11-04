# Безпека

## Як повідомити про вразливість

Будь ласка, **не відкривайте публічне issue**. Скористайтеся приватним звітом GitHub: вкладка **Security → Report a vulnerability** у цьому репозиторії. Звіт бачить лише авторка проєкту.

Опишіть, що саме вразливе, як це відтворити і на якій версії застосунку та Android.

## Що обіцяємо

- **3 дні:** підтвердимо, що звіт отримано.
- **7 днів:** виправимо критичну вразливість (доступ до зашифрованих даних, виконання чужого коду, підміна оновлення) і випустимо нову версію.
- **Інші вразливості:** виправимо в найближчому випуску.
- Після виправлення подякуємо вам в описі випуску, якщо ви не проти.

## Що підтримується

Лише **остання версія** зі сторінки [Releases](https://github.com/Marybezu/sprotyv/releases). Перевірити, що APK справжній: `apksigner verify --print-certs sprotyv-<версія>.apk`, відбиток сертифіката SHA-256 має бути `d00241499cd44d0859c5762a0b4cc317862358c8df3210fc3a0e9473e038a1ac`.

## Мінімальна версія Android: 12

Android 12 (API 31) і новіші дають те, на чому тримається захист «Мого»: надійне сховище ключів із прив'язкою до відбитка чи PIN, заборону знімків і показу в «недавніх» на захищених екранах, явно закриті для інших застосунків компоненти, точніший контроль сповіщень на заблокованому екрані. На старіших версіях ці гарантії дати не можна, а оновлень безпеки від виробників вони здебільшого вже не отримують.

---

# Security

## How to report a vulnerability

Please **do not open a public issue**. Use GitHub's private report: the **Security → Report a vulnerability** tab of this repository. Only the project's author sees it.

Describe what is vulnerable, how to reproduce it, and which app and Android versions.

## What we promise

- **3 days:** we confirm we received the report.
- **7 days:** we fix a critical vulnerability (access to encrypted data, running someone else's code, a forged update) and release a new version.
- **Other vulnerabilities:** fixed in the next release.
- After the fix we thank you in the release notes, if you wish.

## What is supported

Only the **latest version** on the [Releases](https://github.com/Marybezu/sprotyv/releases) page. To check an APK is genuine: `apksigner verify --print-certs sprotyv-<version>.apk`; the certificate's SHA-256 must be `d00241499cd44d0859c5762a0b4cc317862358c8df3210fc3a0e9473e038a1ac`.

## Minimum Android version: 12

Android 12 (API 31) and newer provide what the protection of My data relies on: a reliable keystore bound to fingerprint or PIN, blocking screenshots and the recent-apps picture on protected screens, components explicitly closed to other apps, finer control of lock-screen notifications. Older versions cannot give these guarantees and mostly no longer get security updates from their makers.
