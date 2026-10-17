# Industrial Expansion — TDD / инженерный контракт

**Версия документа:** 0.1, 17.10.2026. **Цель:** следующий этап после Unusual Copper 0.20.6-alpha.1. **Платформа:** Minecraft 1.20.1, Forge 47.x, Java 17. **Статус:** проект, не реализованный код. См. [мастер-GDD](INDUSTRIAL_EXPANSION_GDD.md), [броню](PROGRAMMABLE_ARMOR.md), [этапы](INDUSTRIAL_EXPANSION_ROADMAP.md), [архитектуру текущего мода](ARCHITECTURE.md), [транспорт](ITEM_TRANSPORT.md), [экстрактор](RESONANT_EXTRACTOR.md).

## 1. Совместимость с текущей архитектурой

Не вводить второй движок энергии, второй формат инвентаря или «универсальную базовую машину на 2000 строк». Использовать существующие CuE capability/порт, серверную машину с GUI/ownership/redstone, IItemHandler, StorageEndpoint/OwnedItems, item pipes, ограниченные очереди, поддержку /reload и действующие compat-адаптеры. Новые пакеты и рецепты должны соблюдать нынешнюю серверную модель доверия.

Новые подсистемы логически разделяются так:

| Подсистема | Ответственность | Зависимости |
|---|---|---|
| industrial.recipe | JSON-сериализаторы дробления, прессования, очистки, травления, кристаллизации, сборки | RecipeManager, tags, JEI optional |
| industrial.machine | BlockEntity, inventory/tank, оплаченный прогресс, redstone/ports | существующие machine/energy/storage |
| industrial.process | один исполнитель атомарных шагов и общий budget | ItemHandler, CuE, FluidHandler |
| industrial.order | DAG заказов, резервы, очередь и идемпотентные операции | industrial.process, storage |
| industrial.multiblock | проверка структур и несколько физических портов | energy graph, world events |
| armor.data | capability/версионированный NBT четырёх предметов, whitelist модулей | Forge ItemStack |
| armor.runtime | **один** серверный тик носителя, приоритеты, CuE, полёт | Living/Player events, energy |
| armor.net / armor.client | команды намерений, HUD, визуальные эффекты | SimpleChannel, клиентские экраны |
| compat | SSN, JEI, claims, другие источники полёта | optional adapters |

Это **целевые границы**, не утверждение о наличии именно таких Java package names в текущем дереве. Перед реализацией E0 сопоставить их с реальными классами и существующими test fixtures.

## 2. Контракты предметов и жидкости

Все входы и выходы работают с реальными ItemStack/FluidStack и точными NBT/capability. **simulate не резервирует** внешнее хранилище. Перед commit повторно проверять источник, версию рецепта, доступность места, права, энергию и жидкость. Результат операции сначала попадает в **собственный сохраняемый output/OwnedItems**, затем переносится наружу; не создавать результат сразу в соседнем сундуке.

Стандартная структура новой машины (логическая схема):

~~~text
machineId, schemaVersion, instanceUuid, ownerUuid
inventory: inputs / catalysts / outputs / containerReturns
fluids: tankId, fluidId, amount, capacity (if applicable)
energy: storedCuE, capacityCuE, inputLimitPerTick
ports: face modes / redstone / ownership / priorities
job: recipeId, recipeFingerprint, inputFingerprint, totalTicks,
     elapsedTicks, paidCuE, reservedInputs, reservedFluids,
     pendingOutputs, operationUuid, phase
diagnostics: pausedReason, lastError, lastKnownNetworkRevision
~~~

**Фазы операции:** IDLE → PREPARED → RUNNING → READY_TO_COMMIT → OUTPUT_PENDING → IDLE. При подготовке резервировать точные входы во **внутреннем** инвентаре, не в чужом IItemHandler. Во время RUNNING CuE оплачивается по детерминированному распределению. READY_TO_COMMIT проверяет output, рецепт и права. OUTPUT_PENDING означает, что результат уже принадлежит машине и **не может быть создан повторно** после загрузки. Сохранять переход фазы вместе с результатом в одном BlockEntity NBT. Между файлами двух чанков/игрока нельзя обещать полную crash-атомарность без отдельного протокола; внешние передачи проектировать консервативно, с возможностью отложенного вывода.

Формула равномерного списания для цены C за T тиков:

~~~text
charge(t) = floor(C * t / T) - floor(C * (t - 1) / T)
0 <= t <= T
sum(charge(1..T)) = C
~~~

Промежуточное умножение C×t считать в long с защитой от переполнения. paidCuE сохраняется. При /reload, изменившем рецепт, работа ставится на безопасную паузу; оплаченный прогресс не переносится на новый продукт. Для отказа предусмотрен понятный UI и явная операция отмены без дюпа/потери входов.

**Forge fluids:** для очистителя и травильной камеры нужен IFluidHandler с проверкой направления, simulate/execute, контейнерными возвратами и отсутствием незапрошенной генерации воды. Никаких бесконечных источников жидкости внутри машины, кроме отдельно явно разрешённого ванильного механизма. Автоклав продолжает использовать свою существующую модель воды, её не ломать.

## 3. Формат новых рецептов и datapack

Предлагаемые recipe types:

- unusualcopper:crushing
- unusualcopper:pressing
- unusualcopper:purifying
- unusualcopper:crystallizing
- unusualcopper:etching
- unusualcopper:precision_assembling

Обязательные поля: recipe ID, ingredient/tag с количеством, точный output ItemStack с количеством, ticks, cue, optional fluid, optional catalyst/tool, optional byproducts с детерминированным правилом, minMachineTier, version/compat flag. Валидация при загрузке: положительные количества/время, неотрицательная цена, лимиты, разрешённые items/fluids, допустимый NBT, отсутствие неизвестных registry IDs, контроль переполнения.

**Пример формата (проект, сериализатор ещё предстоит написать):**

~~~json
{
  "type": "unusualcopper:crushing",
  "ingredient": {"item": "minecraft:raw_copper"},
  "count": 1,
  "result": {"item": "unusualcopper:copper_grit", "count": 1},
  "ticks": 60,
  "cue": 180
}
~~~

**Пример очистки (проект):**

~~~json
{
  "type": "unusualcopper:purifying",
  "ingredient": {"item": "unusualcopper:copper_grit"},
  "count": 1,
  "fluid": {"fluid": "minecraft:water", "amount": 250},
  "result": {"item": "unusualcopper:washed_copper_dust", "count": 3},
  "byproduct": {"item": "unusualcopper:inert_tailings", "count": 1},
  "ticks": 80,
  "cue": 300
}
~~~

Поля JSON **не** являются готовым Forge API; точная схема serializer/codec определяется в E1 и закрепляется тестом загрузки. Использовать data/unusualcopper/recipes и обычные теги items/blocks. Нельзя превращать любой forge:ores в автоматически выгодный рецепт; выходы настраиваются по каждому металлу. После /reload invalidate recipe caches и планов заказов, но не уничтожать уже принадлежащие машине предметы.

Для SMELTING новых порошков использовать стандартные vanilla/Forge recipes и существующую плавильню, если её фильтр допускает конкретный ингредиент. Добавить тесты на /reload, несовместимый recipe type и модовые inputs.

## 4. Защита медной экономики: проверяемый инвариант

Модель ресурсов должна учитывать физические единицы меди, а не только CuE. Рекомендованный offline-тест строит ориентированный граф рецептов для меди/производных/эссенции/батарей и ищет циклы с положительным выходом меди или CuE при нулевом внешнем входе. При возможности округления тестировать малые и большие партии, container returns, NBT и byproducts.

Инварианты:

- Обычный энергетический цикл остаётся 64 слитка → 128 эссенций → 64 000 CuE.
- Один raw copper → 1 grit → 3 washed copper dust → не больше 3 copper ingots через дробилку/очиститель/плавильню (обновлённый баланс от автора).
- Медь, превращённая в plate/wire/frame, не возвращается в большем количестве, чем затрачено.
- Рецепты iron/gold 1 raw → 2 grit → 6 refined dust при трёхкратной очистке; проверять ванильные альтернативы и отсутствие циклов без нового сырого ресурса.
- Ни tailings, ни пустые батареи, ни модули не превращаются в эссенцию/медь с прибылью.
- Улучшение батареи I→VI **переносит**, а не дублирует CuE. В результате только один физический носитель заряда.
- Внешний FE input отсутствует; новый «промышленный реактор» не производит CuE из времени, света или воды без меди.

Тестировать отрицательные цены, 0-tick, NBT с переполнением, 2^31−1, near-Long.MAX_VALUE, malformed datapack и /reload на загруженной фабрике.

## 5. Контракт машин и портов

| Машина | Энергобуфер (предл.) | Предметные порты | Жидкость | Статус при паузе |
|---|---:|---|---|---|
| Дробилка | 20 000 | 2 входа, 2 выхода | нет | NO_INPUT, NO_ENERGY, OUTPUT_FULL |
| Пресс | 20 000 | вход, форма, 2 выхода | нет | MISSING_DIE, OUTPUT_FULL |
| Очиститель | 50 000 | вход, 2 выхода | вода 4 000 mB | NO_WATER, NO_ENERGY |
| Кристаллизатор | 100 000 | 3 входа, выход | нет | NO_ESSENCE, OUTPUT_FULL |
| Травильная камера | 100 000 | 2 входа, возврат тары, выход | вода 4 000 mB | NO_REAGENT, NO_WATER |
| Сборщик | 100 000 | 9 входов, шаблон, 2 выхода/возвраты | по рецепту позднее | INVALID_PATTERN, OUTPUT_FULL |
| Контроллер | 50 000 | карточки заказов, без физического предметного «телепорта» | нет | WAITING_INPUT, CHUNK_UNLOADED, NO_PERMISSION |
| Зарядная стойка | 100 000 | батарея/броня, один слот на устройство | нет | NO_ENERGY, ITEM_LOCKED |
| Матрица | 1 000 000 | входы и выход | нет | STRUCTURE_INCOMPLETE |
| Призменная решётка | реальные камеры | отдельные входы меди/линз/эссенции | нет | NO_SKY, NIGHT, LENS_BROKEN |

Размеры буферов и портов не должны обходить реальные ограничения сети I–III. В E0 сверить существующую архитектуру направлений граней, redstone и меню. Новые машины используют те же controls и язык статусов. Нельзя включать новую machine capability на клиенте как авторитетную.

## 6. Контроллер заказов: самостоятельный автокрафт

**Цель:** локальный автокрафт без SSN; совместимость с Progressive отдельно. Контроллер видит только загруженные endpoints, которые прошли ownership/claims, и не читает приватные хранилища другого игрока.

**Планировщик:**
1. Получить заказ (recipe output + count + optional NBT policy) с UUID и revision. Проверить отправителя, разрешение, лимиты.
2. Найти один из допустимых рецептов по whitelist. Построить ориентированный граф зависимостей (DAG) с ограничениями: максимум 128 узлов, глубина 16, не более 4 096 единиц в одном заказе (предложения).
3. Найти циклы через DFS; не использовать recipe ID, который создаёт самого себя, и не разрешать автоматический рекурсивный план из неизвестных NBT-контейнеров.
4. Составить спецификацию резервов. Фактическое изъятие материалов выполняется **не** в момент симуляции, а только после повторной проверки в серверном потоке; извлечённые предметы помещаются в собственный сериализуемый escrow.
5. Запустить доступные сборщики/прессы/очистители с ограничением параллельности; каждый этап имеет уникальный operation UUID, состояние и точный input fingerprint.
6. По завершении этапа output попадает в owned buffer контроллера или машины. Перенос по сети выполняется отдельно и подтверждает фактический accepted count. Если цель недоступна, результат ждёт, а не исчезает.
7. При отмене возвращать неиспользованные материалы через escrow/owned buffer. Уже потраченные CuE и завершённые промежуточные изделия не «откатываются» в сырьё; они остаются физическими предметами.
8. После save/reload восстанавливать queued/running/completed операции идемпотентно. Если рецепт исчез, ставить заказ на паузу с возвратом ещё не потреблённых входов.

**Конкуренция:** один физический слот/стак не может быть одновременно зарезервирован двумя контроллерами. Использовать локальный reservation/lease с revision и кратким временем жизни для доступных хранилищ; при невозможности гарантировать lease внешнего handler использовать **немедленное физическое извлечение в escrow** или отказ заказа. Не хранить «обещание предмета» как гарантированное владение. Обязательна защита от двух контроллеров через два SSN Bridge на один и тот же сундук.

**Интеграция SSN:** Progressive 1.4.0 уже умеет собственный рекурсивный автокрафт. Не перехватывать его планировщик и не менять его JAR. Опциональный адаптер может объявить промышленную машину как рецепт/endpoint, но только после проверки API, ревизий и отмен. Без SSN всё работает с сундуками, трубами и обычными IItemHandler.

**Производительность:** один глобальный budget планирования, не больше 2 мс/тик на все контроллеры в worst-case (предложенный верхний guard), не более 64 поисковых/резервационных операций на контроллер за посещение, минимум синхронизации клиенту. Сложные планы строить инкрементально по тикам, а не за один tick freeze.

## 7. Броня: данные, серверный цикл, эффекты

**Логическая сериализация предмета:**

~~~text
schemaVersion, chassisTier, pieceType, itemInstanceUuid, ownerPolicy
modules: [{moduleId, level, enabled, priority, configVersion}]
profiles: [{profileId, perModuleEnabled, triggerRules, priorityOverrides}]
activeProfileId, localCuE, lastAppliedRevision
chestOnly: batteryItemStack, matrixItemStack, emergencyReserveCuE
~~~

Никаких client-trusted CuE, moduleLevel, creative permission или «высоты взлёта». Сервер на каждом тике проверяет четыре **текущих** ItemStack, версии, владельца и политику; строит единый runtime context. Тик:

~~~text
1. read equipped stacks and authoritative battery
2. validate modules, slots, tier, world/dimension, server config
3. evaluate server-side triggers and permissions
4. sort enabled modules by safety + player priority
5. compute continuous cost and prospective event cost
6. reserve exact CuE from one physical battery/local allowed source
7. apply only paid effects; publish minimal state delta
8. revoke stale owned modifiers/flight grants; persist dirty state
~~~

**Событийные эффекты:** урон/щит, лечение, прыжок, добыча и падение требуют idempotency per event ID или серверный guard против двойного Forge callback. Платить только за реально применённую величину; если эффект отменён другим модом/claims, не списывать. Не применять один shield дважды в LivingHurtEvent + LivingDamageEvent.

**Potion/attribute coexistence:** по возможности реализовать эффекты через собственные проверяемые атрибуты/Forge events, а не выдавать длинные ванильные зелья. Для клиентского Night Vision допустима только визуализация при подтверждённом серверном состоянии. Если необходим MobEffect, хранить признак **своего** источника и не удалять чужой активный эффект; проверить refresh/remove/replace на Forge. Собственные атрибуты имеют стабильные UUID и снимаются при смене экипировки. Haste/Speed/Strength не должны умножаться сверх конфигурационного cap.

**Полёт:** собственный grant token, собственный origin для F3, режим/ревизия/время, аварийный резерв. Creative/spectator/другие моды могут выдавать mayfly независимо; удалять только свой grant. В Forge PlayerAbilities нет универсального «owner token» из коробки, поэтому требуется адаптер/контракт учёта источников, отдельные тесты на конкуренцию и безопасный fallback. Не обещать автоматическую совместимость с каждым модом полёта.

**NBT-дюпы:** ItemStack UUID не является магической защитой от копирования мира; он лишь помогает обнаруживать конфликтующие экземпляры в одном контексте. Вводить itemInstanceUuid, серверную проверку вложенной батареи, запрещать copy-based upgrade, сверять inventory fingerprints и проводить тесты death/transfer/rollback. Если чужой мод клонирует предмет/мир вне наших транзакций, глобальная абсолютная гарантия невозможна; документировать границы.

## 8. Сеть и безопасность пакетов

Предлагаемые C2S действия: setModuleEnabled, setModuleLevel, setModulePriority, setProfile, setTrigger, requestFlightMode, requestOrder, cancelOrder, configurePattern. Каждый пакет содержит **только намерение**, target identity (реальный слот/menu/block position), ожидаемую revision и ограниченные значения. На сервере проверять: игрок жив/в мире, меню открыто и соответствует BlockEntity, расстояние, загруженный чанк, owner/claims, rate limit, наличие предмета в реальном слоте, актуальную revision, допустимый уровень, разрешение F4, количество и cooldown.

Нельзя принимать из пакета готовый output ItemStack, произвольный recipe NBT, количество CuE, коэффициент усиления, позицию чужого блока или факт «я приземлился». Ограничить размер строк/списков и частоту (например, 10 команд/сек на игрока, 1 flight toggle/5 тиков). Не вызывать loadChunk из обработчика. На клиент слать агрегированный заряд и активные модули с разницей состояния, а не полный инвентарь каждого тика.

Синхронизация long CuE: использовать корректную сериализацию, не обрезать значение в 16-bit DataSlot. При 250 млн CuE обычный int ещё помещается, но арифметику и сериализацию лучше сразу проектировать как long. Любое изменение формата требует schemaVersion и тест старого сохранения.

## 9. Конфиги и политики сервера

Рекомендуемые ключи (имена проектные):

~~~text
industrial.enable_expansion = true
industrial.recipe_allow_datapacks = true
industrial.max_active_jobs_per_controller = 8
industrial.max_recipe_nodes = 128
industrial.max_recipe_depth = 16
industrial.max_order_items = 4096
industrial.max_plan_budget_ns_per_tick = 2000000
industrial.max_machine_operations_per_tick = 64
industrial.allow_unverified_modded_ores = false
industrial.purifier_water_mb_per_item = 250
industrial.multiblock_max_ports = 4

armor.enable = true
armor.allow_true_flight = true
armor.true_flight_dimension_denylist = []
armor.allow_pvp_damage_modules = false
armor.max_total_cue_per_tick = 900
armor.flight4_cue_per_tick = 480
armor.emergency_reserve_cue = 120000
armor.emergency_descent_cue_per_tick = 30
armor.emergency_max_ticks = 4000
armor.max_ore_scan_blocks_per_player_per_tick = 16
armor.require_full_tier3_for_true_flight = true
armor.max_profile_changes_per_second = 5
~~~

Изменение серверного конфига во время работы немедленно пересчитывает разрешения; F4 при запрете прекращается через безопасный переход, но не сохраняет бесплатное право mayfly. Значения конфигов проверять на min/max; negative/NaN/overflow отклонять. Локализация RU/EN и datapack IDs обязательны.

## 10. Мультиблоки и энергопорты

Мультиблок не должен хранить «теневой» дублирующий запас энергии. Контроллер знает координаты и UUID реальных камер/портов, но энергия находится в конкретных BlockEntity. Структура пересчитывается по placement/break/load/unload, не каждый тик. При потере части структуры операции ставятся на паузу, уже оплаченные/полученные предметы сохраняются.

Многопортовая шина: 4 физических порта III по 4 096 CuE/тик; суммарно не больше 16 384 **при четырёх независимых путях**, а не 16 384 через один провод III. Счётчики пропускной способности ведутся для каждого ребра/порта. При разборке мультиблока не дублировать батареи, линзы, эссенцию, pending output или заряд.

Солнечная решётка имеет N **отдельных** камер с N линзами и N партиями меди. Цена энергии на слиток остаётся 1 000 CuE. Прозрачная часть/кристалл не обязаны занимать физические блоки, если это только BER, но collision/claims/chunk bounds нужно согласовать в прототипе.

## 11. Тестовая матрица

| ID | Тест | Уровень | Критерий |
|---|---|---|---|
| IE-01 | все JSON-рецепты + /reload | JUnit/GameTest | валидация и pause без потерь |
| IE-02 | медная масса и CuE-циклы | JUnit property-based | нет бесконечного цикла без нового добытого raw-сырья |
| IE-03 | дробилка/пресс 1 и 64 шт. | GameTest | точные вход/выход/CuE |
| IE-04 | очиститель, пустой бак, ведро, контейнеры | GameTest | 250 mB, возврат, нет воды из воздуха |
| IE-05 | blocked output, energy brownout, redstone | GameTest | точная пауза, нет повторного списания |
| IE-06 | два контроллера и последний предмет | GameTest | один владелец резерва |
| IE-07 | DAG 3/16/128, цикл и исчезнувший рецепт | JUnit | bounded planning, понятный отказ |
| IE-08 | cancel/restart/chunk unload между фазами | GameTest/manual crash | нет потерь/дюпов; границы crash явно указаны |
| IE-09 | SSN absent / SSN 1.13.2 / Progressive 1.4.x | integration | без зависимости, без двойного владения |
| IE-10 | энергетические порты 256/1024/4096 | GameTest | нет обхода узкого места |
| IE-11 | батареи IV→V→VI с NBT/зарядом | GameTest | сохранение энергии, имён, нет дюпа |
| IE-12 | модули I–III, slots, invalid NBT | JUnit/GameTest | только допустимые уровни |
| IE-13 | полный сет и снятие части | GameTest | эффект отключён в том же серверном тике |
| IE-14 | shield/regen/прыжок, двойной event | GameTest | однократный эффект/платёж |
| IE-15 | vanilla potion/enchants + модуль | GameTest | нет overcap/стирания чужого эффекта |
| IE-16 | F1/F2/F3/F4, mayfly creative/другой мод | GameTest/manual | права и высота корректны |
| IE-17 | F4 разряд и резерв 120k | GameTest/manual | аварийная посадка, не бесплатный полёт |
| IE-18 | смерть, logout, dimension, respawn | GameTest | no ghost grant / no dup |
| IE-19 | PvP/claims, чужая машина и сканер | integration | отказ без списания и утечки |
| IE-20 | 50/100 машин + ~1 000 узлов + 20 игроков | performance/manual | p95 MSPT <45 мс, прирост ≤5 мс к baseline |
| IE-21 | рендер/шейдеры/JEI/RU-EN/HUD | manual client | читаемость, нет resource errors |
| IE-22 | datapack удаляет модуль/рецепт | GameTest | неизвестные данные сохраняются, не действуют |
| IE-23 | восстановление мира предыдущей alpha | migration test | старые блоки/предметы/заряд не повреждены |
| IE-24 | серверная синхронизация C2S/replay | JUnit/GameTest | повторный пакет не повторяет операцию |

Для IE-20 использовать утверждённый ранее ориентир Ryzen 5 5600X, 20 TPS, p95 общего MSPT ниже 45 мс и добавка мода не более 5 мс к сопоставимому baseline. Отчёт содержит JAR SHA-256, версии Forge/модпака, мир, длительность прогрева, сценарий, p50/p95/p99, heap/GC и оговорки. **Нельзя переносить старые результаты на новый JAR.**

## 12. Сборка, CI и границы автоматической проверки

Разрешённая статическая проверка:

~~~bash
./gradlew --no-daemon build compileGameTestJava --console=plain
~~~

На Windows использовать gradlew.bat. Она выполняет JUnit и **компилирует** GameTests, но **не запускает** Minecraft. По AGENTS.md клиент, GameTest-сервер, production probes и живой сервер запускает пользователь либо агент по отдельному явному разрешению. Любой документ должен честно разделять: «скомпилировано», «JUnit прошёл», «GameTests выполнены», «клиент проверен», «выделенный сервер проверен».

## 13. Миграции и выпуск

- Никогда не переименовывать существующие registry IDs/recipe IDs молча.
- Добавить schemaVersion к новым BlockEntity и предметам; неизвестные поля сохранять по возможности.
- Переход батарей IV→V→VI обязан сохранять прежний заряд; старые батареи I–IV остаются действительными.
- Не требовать новую броню или промышленный контроллер для старых машин и миров.
- Без SSN, JEI, claims-мода, новых мультиблоков и брони старая фабрика должна запускаться как прежде.
- При выключении expansion в конфиге предметы и блоки не удалять из мира: они становятся инертными, сохраняют NBT и сообщают причину.
- Release gate: чистый checkout собирается; новые рецепты проверены; миграции и отказоустойчивость проверены; нет известного дюпа/обхода claims; производительность подтверждена; пользователь принял игровые визуалы и полёт.

## 14. Что решить до первого коммита с кодом

T01. Выбрать точный serializer schema и package mapping после просмотра реальных классов 0.20.6. T02. Определить обработку жидкости без обязательного стороннего fluid-мода. T03. Подтвердить новую математику 1 raw copper → 3 ingots и 1 raw iron/gold → 6 ingots после очистки, включая энергозатраты. T04. Утвердить escrow/reservation и границы crash-атомарности между чанками. T05. Согласовать физические порты и точные скорости зарядки. T06. Выбрать политику Forge mayfly/совместимости с другими flight grants. T07. Утвердить server config, PvP и claims. T08. Проверить баланс на реальном количестве добываемой меди. T09. Разделить обязательный вертикальный срез E1 и поздние мультиблоки, не раздувая первый PR.

Изменения этих решений фиксируются в PR и [DECISIONS](DECISIONS.md); этот TDD нельзя считать подтверждением их реализации.