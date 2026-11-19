# 📈 Farm Rate Counter

[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/37aSnw7evS)

[**English**](#english) | [**Русский**](#russian)

---

<a name="english"></a>
## 🇺🇸 English

**Farm Rate Counter** is a client-side Fabric mod that tells you how many items per hour your farm really produces. It works on **vanilla servers**: no server mod, no Carpet, no hopper counters needed.

### ✨ Features

- **Select collection hoppers**: look at a hopper and press **J**. Hold **J** and sweep across a row to select many at once.
- **Multiple farms**: group hoppers into named farms (`/farmrate farm iron`), each with its own color.
- **In-world overlay**: selected hoppers are outlined, and a label above the farm shows its rate, e.g. `iron: 1 240/h`.
- **HUD panel**: rate per hour, rate over the last 5 minutes, items collected and the top items with icons.
- **Fair timing**: the clock only runs while you are near the farm (the farm only works then anyway), so rates are not diluted when you fly away.
- **Storage mode**: mark the farm's chest with `/farmrate chest`. Every time you open it, the mod shows what arrived since the last time and the rate per hour, using server time, so AFK and away time count too.
- **Saved per world**: farms and statistics are kept per server / world and dimension.

### ⚙️ How it works

A vanilla client cannot see inside a hopper, but it does see the dropped items above it. The mod watches the area a hopper sucks items from and counts the items that disappear into it. Merges with neighbouring items, despawned items and items you pick up yourself are not counted.

Select the hoppers that items **fall or flow onto**, not the hopper chain below them. Items moved by hopper minecarts, droppers or container-to-container transfers are not visible to the client.

### 📝 Commands

| Command | Action |
|---|---|
| `/farmrate` | Stats of the current farm |
| `/farmrate farm <name>` | Create / switch the current farm |
| `/farmrate farms` | List farms |
| `/farmrate add` / `remove` | Add / remove the hopper you are looking at |
| `/farmrate chest` | Mark / unmark the storage chest you are looking at |
| `/farmrate stats [farm]` | Per-item statistics |
| `/farmrate reset [farm]` | Reset counters |
| `/farmrate delete <farm>` | Delete a farm |
| `/farmrate show <true/false>` | Toggle the in-world overlay |
| `/farmrate hud <true/false>` | Toggle the HUD panel |
| `/farmrate range <blocks>` | Max distance to the hoppers for counting (default 40) |

### 📥 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/).
2. Download [Fabric API](https://modrinth.com/mod/fabric-api).
3. Put **Farm Rate Counter** and Fabric API into your `mods` folder.

### 🔗 Support & Community

Join my Discord server for chat, support and news: [**Join Discord Server**](https://discord.gg/37aSnw7evS)

### 👨‍💻 Author

**Dalkco**

---

<a name="russian"></a>
## 🇷🇺 Русский

**Farm Rate Counter** — клиентский Fabric-мод, который показывает, сколько предметов в час на самом деле даёт твоя ферма. Работает на **ванильных серверах**: не нужны серверные моды, Carpet и счётчики воронок.

### ✨ Возможности

- **Выделение воронок-сборщиков**: наведись на воронку и нажми **J**. Зажми **J** и проведи по ряду воронок — выделятся все.
- **Несколько ферм**: воронки объединяются в фермы с названиями (`/farmrate farm iron`), у каждой свой цвет.
- **Подсветка в мире**: выделенные воронки обведены рамкой, над фермой табличка со скоростью, например `iron: 1 240/ч`.
- **Панель на экране**: предметов в час, скорость за последние 5 минут, сколько собрано и топ предметов с иконками.
- **Честное время**: замер идёт, только пока ты рядом с фермой (без тебя она и не работает), поэтому цифры не занижаются, когда улетаешь.
- **Режим хранилища**: отметь сундук фермы командой `/farmrate chest`. При каждом открытии мод покажет, сколько пришло с прошлого раза и сколько это в час — по серверному времени, так что AFK и время отсутствия тоже учитываются.
- **Сохранение по мирам**: фермы и статистика хранятся отдельно для каждого сервера / мира и измерения.

### ⚙️ Как это работает

Ванильный клиент не видит содержимое воронки, но видит выпавшие предметы над ней. Мод следит за зоной, из которой воронка всасывает предметы, и считает предметы, которые в ней исчезают. Слияния с соседними предметами, исчезновение по таймеру и предметы, подобранные тобой, не засчитываются.

Выделяй воронки, на которые **падают или приплывают** предметы, а не цепочку под ними. Предметы из воронок-вагонеток, раздатчиков и перекладывание между контейнерами клиенту не видны.

### 📝 Команды

| Команда | Действие |
|---|---|
| `/farmrate` | Статистика текущей фермы |
| `/farmrate farm <название>` | Создать / выбрать текущую ферму |
| `/farmrate farms` | Список ферм |
| `/farmrate add` / `remove` | Добавить / убрать воронку под прицелом |
| `/farmrate chest` | Отметить / снять сундук-хранилище под прицелом |
| `/farmrate stats [ферма]` | Статистика по предметам |
| `/farmrate reset [ферма]` | Сбросить счётчик |
| `/farmrate delete <ферма>` | Удалить ферму |
| `/farmrate show <true/false>` | Подсветка в мире |
| `/farmrate hud <true/false>` | Панель на экране |
| `/farmrate range <блоков>` | На каком расстоянии от воронок считать (по умолчанию 40) |

### 📥 Установка

1. Установите [Fabric Loader](https://fabricmc.net/use/).
2. Скачайте [Fabric API](https://modrinth.com/mod/fabric-api).
3. Положите **Farm Rate Counter** и Fabric API в папку `mods`.

### 🔗 Поддержка и общение

Присоединяйтесь к моему Discord-серверу: [**Присоединиться к Discord**](https://discord.gg/37aSnw7evS)

### 👨‍💻 Автор

**Dalkco**
