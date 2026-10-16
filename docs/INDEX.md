# Документация Unusual Copper

Актуальная версия: **0.20.6-alpha.1**, сопутствующий SSN Progressive: **1.4.0**. [Изменения](../CHANGELOG.md) · [Манифест сборки](../releases/0.20.6-alpha.1.json).

| Тема | Документы |
|---|---|
| Начало и устройство мода | [Архитектура](ARCHITECTURE.md), [маршрут M0–M5](ROADMAP.md), [backlog](BACKLOG.md), [решения](DECISIONS.md) |
| Энергия | [Три класса](ENERGY_TIERS.md), [приоритеты и управление](ENERGY_CONTROL.md), [батареи](BATTERIES.md) |
| Транспорт и хранилища | [Настройка труб](ITEM_PIPES.md), [архитектура транспорта](ITEM_TRANSPORT.md), [визуальные маршруты](ITEM_TRANSIT_VISUALS.md), [индекс инвентарей](STORAGE_INDEX.md), [SSN Bridge](SSN_BRIDGE.md) |
| Растения и предметы | [Выбор области](AREA_CULTIVATION.md), [сбор и пересадка](CULTIVATOR_HARVEST.md), [магнитный коллектор](MAGNETIC_COLLECTOR.md), [Арборист](ARBORIST.md) |
| Существа | [Жнец](MOB_REAPER.md), [учёт популяции](MOB_POPULATIONS.md), [Пастырь роста](GROWTH_SHEPHERD.md), [кормушки Quark](QUARK_TROUGHS.md) |
| Магические машины | [Медный часовой](COPPER_SENTINEL.md), [автоклав](AETHERIC_BREWER.md), [стабильность запасов](BREWER_STOCK_0_19.md), [зачарования](ENCHANTMENT_RESONATOR.md), [ремонт](REPAIR_CRYSTAL.md) |
| Добыча | [Резонансный экстрактор](RESONANT_EXTRACTOR.md) |
| Визуал | [Blockbench и стиль](MODELING.md), [визуальный проход](VISUALS.md), [интерфейсы выбора](MACHINE_SELECTION_UI.md), [шейдеры](SHADER_COMPAT.md) |
| Проверки | [Матрица приёмки](TEST_PLAN.md), [профили клиента](TEST_CLIENT.md), [производительность](PERFORMANCE.md), [аудит](AUDIT_2026-10-16.md) |
| Исходная идея | [GDD/TDD v0.2](Unusual_Copper_GDD_TDD_v0.2.md), [визуальный референс](references/visual-style.png) |

`docs/verification/` содержит исторические отчёты разных версий, включая локальные пути и результаты прежних запусков. Для актуальной публикации используйте манифест версии; он отделяет выполненные JUnit от только скомпилированных игровых сценариев. Текущая инструкция проекта не разрешает автоматический запуск игры для проверки без отдельного запроса пользователя.
