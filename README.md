# SeedMap for Xaero's World Map

[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-blue)
![Fabric](https://img.shields.io/badge/loader-Fabric-lightgrey)
![Client side](https://img.shields.io/badge/side-client-orange)

**RU** · [EN](#english)

**Chunkbase прямо внутри карты Xaero.** Аддон добавляет в Xaero's World Map режим «карта по сиду»: биомы, структуры, слизневые чанки и спавн рисуются по сиду мира, даже там, где ты ещё не был.

> Неофициальный аддон. Не связан с автором Xaero's World Map.

## Возможности

- **Три режима карты** (кнопка в правом верхнем углу World Map): `Xaero`, `Seedmap`, `Изучено + структуры`
- **Биомы** Верхнего мира, Нижнего мира и Энда
- **Структуры**: деревни (с типом), аванпосты, особняки, храмы, хижины ведьм, иглу, затонувшие корабли, руины океана, клады, монументы, шахты, крепости, древние города, руины троп, камеры испытаний, разрушенные порталы, крепости Незера, бастионы, окаменелости Незера, города и корабли Края
- **Мелкие объекты**: подземелья со спавнером, аметистовые жеоды, окаменелости, лавовые озёра, пустынные колодцы, врата Края
- **Слизневые чанки** и **точка спавна**
- **Фильтры** по типам и размер иконок
- **Метки**: ПКМ по структуре ставит или убирает метку, ЛКМ показывает координаты, чанк, расстояние и тип
- Русский и английский интерфейс

## Установка

1. Установи [Fabric Loader](https://fabricmc.net/) 0.20.1+ и Minecraft 26.2 (Java 25).
2. Положи в `mods/`: [Fabric API](https://modrinth.com/mod/fabric-api), [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) и jar этого аддона. Xaero's Minimap по желанию.

Мод работает **только на клиенте**, на сервер ставить не нужно.

## Использование

1. Открой World Map (по умолчанию `M`).
2. Кнопкой в правом верхнем углу выбери режим **Seedmap**.
3. **Одиночная игра**: сид берётся из мира автоматически.
4. **Сервер**: нажми **Сид** и введи сид мира (число или текст), он запоминается отдельно для каждого сервера.
5. Приближай карту, чтобы увидеть структуры и подземелья.

### Ограничения

- Аддон **не определяет сид сервера сам**, его нужно знать и ввести вручную.
- На серверах генерация считается по ванильным данным. Моды и датапаки, меняющие генерацию, на мультиплеере не учитываются.
- Часть позиций помечена как приблизительные: ландшафт для них проверен не полностью.
- Соблюдай правила серверов: если на сервере запрещены карты по сиду, не используй аддон.

Конфиг: `config/seedmapxaero/config.json`.

## Сборка

Нужны JDK 25 и Gradle 9.8.2 (или Gradle Wrapper, если добавишь его в репозиторий).

1. Положи jar-файлы Xaero в `libs/` (список в [libs/README.md](libs/README.md)).
2. Выполни:
   ```
   gradle build
   ```
3. Готовый мод: `build/libs/seedmap-xaero-<версия>.jar`.

## Лицензия и авторство

Проект распространяется по лицензии **MIT** ([LICENSE](LICENSE)).

**Копировать, изменять и дополнять проект можно только с указанием авторства:**
сохрани файл `LICENSE` с копирайтом и явно укажи автора оригинала (**vadiim**) со ссылкой на этот репозиторий. Подробности и пример строки в [NOTICE.md](NOTICE.md).

---

## English

**Chunkbase-style seed map right inside Xaero's World Map.** The addon adds a seed-based view to World Map: biomes, structures, slime chunks and world spawn are drawn from the seed, even in places you have never visited.

> Unofficial addon. Not affiliated with the author of Xaero's World Map.

### Features

- **Three map modes** (button in the top-right of World Map): `Xaero`, `Seedmap`, `Explored + structures`
- **Biomes** for the Overworld, Nether and End
- **Structures**: villages (with variant), outposts, mansions, temples, witch huts, igloos, shipwrecks, ocean ruins, buried treasure, monuments, mineshafts, strongholds, ancient cities, trail ruins, trial chambers, ruined portals, Nether fortresses, bastions, Nether fossils, End cities and End City ships
- **Small features**: dungeons, amethyst geodes, fossils, lava lakes, desert wells, end gateways
- **Slime chunks** and **world spawn**
- **Filters** per type and icon size
- **Waypoints**: RMB on a structure sets or removes a waypoint, LMB shows coordinates, chunk, distance and type
- English and Russian UI

### Installation

1. Install [Fabric Loader](https://fabricmc.net/) 0.20.1+ and Minecraft 26.2 (Java 25).
2. Put into `mods/`: [Fabric API](https://modrinth.com/mod/fabric-api), [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) and this addon's jar. Xaero's Minimap is optional.

**Client-side only**, nothing to install on the server.

### Usage

1. Open World Map (default key `M`).
2. Pick **Seedmap** with the mode button in the top-right corner.
3. **Singleplayer**: the seed is read from the world automatically.
4. **Servers**: press **Seed** and enter the world seed (number or text); it is stored per server.
5. Zoom in to see structures and dungeons.

#### Limitations

- The addon **does not discover a server's seed**; you must know it and enter it yourself.
- On servers, generation uses vanilla data. Mods and datapacks that change worldgen are not taken into account in multiplayer.
- Some positions are marked as approximate: terrain is not fully checked for them.
- Respect server rules: if a server forbids seed maps, do not use this addon.

Config: `config/seedmapxaero/config.json`.

### Building

Requires JDK 25 and Gradle 9.8.2 (or add a Gradle Wrapper to the repo).

1. Put the Xaero jars into `libs/` (see [libs/README.md](libs/README.md)).
2. Run:
   ```
   gradle build
   ```
3. The mod jar is in `build/libs/seedmap-xaero-<version>.jar`.

### License and attribution

Licensed under **MIT** ([LICENSE](LICENSE)).

**You may copy, modify and extend this project only with attribution:** keep the `LICENSE` file with the copyright notice and clearly credit the original author (**vadiim**) with a link to this repository. See [NOTICE.md](NOTICE.md) for details and a sample credit line.
