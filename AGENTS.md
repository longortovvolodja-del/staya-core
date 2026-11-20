# Staya-core — ядро Minecraft-сервера (форк Youer 1.21.1, GPL v3)

«Based on Youer (GPL v3)» — сохранять везде (баннер, footer сайта, лицензии).

Сборка: только GitHub Actions (ветка 1.21.1, JDK 21, ~20–40 мин); локально не собирать.
Jar: Staya-1.21.1-b<N>-server.jar (N = номер запуска CI). Скачивать из артефактов Actions
(название артефакта то же). Перед каждой фичей — строка в CHANGELOG.md: «## vX.Y.Z» + «- описание»;
последняя строка сама печатается в баннере.

Карта:
- youerlauncher/src/main/java/com/mohistmc/launcher/youer/Main.java — точка входа: баннер (L62-64), старт сайта (L99, до BootstrapLauncher)
- youerlauncher/.../StayaBanner.java — баннер: арт, версия (staya-build.properties), последняя строка CHANGELOG, пустая коробка
- youerlauncher/src/main/resources/staya/banner.txt — арт баннера (вариант 2 из MyProject/арт.txt)
- youerlauncher/.../StayaWebsite.java — встроенный сайт (v1.0.1): / и /mods.zip; тумблер staya-website-enabled / staya-website-port в server.properties (порт по умолчанию 8080, включено только при true)
- projects/youer/build.gradle — task youerJar (L474): имя/версия jar, manifest; task stayaBuildInfo (L459) генерит staya/staya-build.properties
- .github/workflows/gradle.yml — CI: ветка 1.21.1, артефакт Staya-1.21.1-b<N>-server, -PstayaBuildNumber / -PstayaProductVersion / -PstayaChangelogLine
- youerlauncher/src/main/resources/lang/message*.properties — тексты консоли (welcomemessage = «Thanks for using Staya»)
- CHANGELOG.md — история версий

Следующие версии (согласовано с заказчиком):
- v1.0.2 — защита от несовместимых модов: статическая проверка, полное отключение + причина, список отключённых виден только админу
- v1.0.3 — OP-замок: пароль OP задаётся в консоли, фриз только OP с паролем (игроки без OP заходят и играют сразу, пароль не нужен), тихая команда /pas, фриз заново при каждом входе + веб-панель /admin
