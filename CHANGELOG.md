# CHANGELOG — Staya

## v1.0.1 · b4
- Встроенный сайт: 5 фиксов пинга MC и /mods.zip; новый ключ staya-site-address, ядро само дописывает ключи staya-* в server.properties
- Страница и консоль показывают публичный адрес сайта; адрес читается на лету, без рестарта сервера
- «Статус» на сайте снова показывает «онлайн · N / M» и версию Minecraft (был пустой)

## v1.0.1
- Встроенный сайт: публичная страница (статус, версия, адрес) и «Скачать моды» (/mods.zip); включается в server.properties (staya-website-enabled=true, staya-website-port)
- Фикс: экранирование бекслэшей в лисе сайта (сборка #1)
- Фикс: DataFormatException в пинге MC (сборка #2)

## v1.0.0
- Ребренд Youer 1.21.1 → Staya: имя jar Staya-1.21.1-b<N>-server.jar, баннер STAYA + лиса, CHANGELOG
