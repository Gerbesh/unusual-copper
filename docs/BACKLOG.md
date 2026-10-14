# Задачи разработки

Состояние при создании: **все 18 задач открыты, реализация запланирована**. Актуальный статус — в GitHub; этот список фиксирует исходный порядок и зависимости.

[Маршрут и критерии этапов](ROADMAP.md) · [Все milestones](https://github.com/Gerbesh/unusual-copper/milestones).

P0 — основа/стабильность; P1 — основные функции и оформление; P2 — интеграции. До 1.0 завершить все задачи и релизную матрицу.

## [M0 — Каркас и пробная модель](https://github.com/Gerbesh/unusual-copper/milestone/1)

| Задача | Приоритет | Зависит от |
|---|---|---|
| [UC-01: Forge MDK, JDK 17 и воспроизводимая сборка](https://github.com/Gerbesh/unusual-copper/issues/1) | P0 | Нет |
| [UC-02: Blockbench: пробная модель, палитра, стекло и экспорт](https://github.com/Gerbesh/unusual-copper/issues/2) | P0 | [UC-01](https://github.com/Gerbesh/unusual-copper/issues/1) |

## [M1 — Первый стак меди](https://github.com/Gerbesh/unusual-copper/milestone/2)

| Задача | Приоритет | Зависит от |
|---|---|---|
| [UC-03: Линза, эссенция и солнечный насытитель](https://github.com/Gerbesh/unusual-copper/issues/3) | P0 | [UC-01](https://github.com/Gerbesh/unusual-copper/issues/1) |
| [UC-04: Расщепитель эссенции и CuE capability](https://github.com/Gerbesh/unusual-copper/issues/4) | P0 | [UC-03](https://github.com/Gerbesh/unusual-copper/issues/3) |
| [UC-05: Энергопровод, ячейка I и граф сети](https://github.com/Gerbesh/unusual-copper/issues/5) | P0 | [UC-04](https://github.com/Gerbesh/unusual-copper/issues/4) |

## [M2 — Первая полезная фабрика](https://github.com/Gerbesh/unusual-copper/milestone/3)

| Задача | Приоритет | Зависит от |
|---|---|---|
| [UC-06: Резонансная плавильня: первый полезный потребитель](https://github.com/Gerbesh/unusual-copper/issues/6) | P0 | [UC-05](https://github.com/Gerbesh/unusual-copper/issues/5) |
| [UC-07: StorageEndpoint и предметные трубы без SSN](https://github.com/Gerbesh/unusual-copper/issues/7) | P0 | [UC-05](https://github.com/Gerbesh/unusual-copper/issues/5) |
| [UC-08: Жезл, грани, GUI, права и ограниченный планировщик](https://github.com/Gerbesh/unusual-copper/issues/8) | P0 | [UC-05](https://github.com/Gerbesh/unusual-copper/issues/5) |
| [UC-09: Культиватор ауры и магнитный коллектор](https://github.com/Gerbesh/unusual-copper/issues/9) | P1 | [UC-07](https://github.com/Gerbesh/unusual-copper/issues/7), [UC-08](https://github.com/Gerbesh/unusual-copper/issues/8) |

## [M3 — Фермы](https://github.com/Gerbesh/unusual-copper/milestone/4)

| Задача | Приоритет | Зависит от |
|---|---|---|
| [UC-10: Пастырь роста](https://github.com/Gerbesh/unusual-copper/issues/10) | P1 | [UC-09](https://github.com/Gerbesh/unusual-copper/issues/9) |
| [UC-11: Жнец сущностей: реальные смерть и дроп](https://github.com/Gerbesh/unusual-copper/issues/11) | P1 | [UC-08](https://github.com/Gerbesh/unusual-copper/issues/8), [UC-10](https://github.com/Gerbesh/unusual-copper/issues/10) |
| [UC-12: Арборист: безопасная рубка и пересадка](https://github.com/Gerbesh/unusual-copper/issues/12) | P1 | [UC-08](https://github.com/Gerbesh/unusual-copper/issues/8), [UC-11](https://github.com/Gerbesh/unusual-copper/issues/11) |

## [M4 — Сильная магия](https://github.com/Gerbesh/unusual-copper/milestone/5)

| Задача | Приоритет | Зависит от |
|---|---|---|
| [UC-13: Медный часовой: LOS и клиентский кристалл](https://github.com/Gerbesh/unusual-copper/issues/13) | P1 | [UC-08](https://github.com/Gerbesh/unusual-copper/issues/8), [UC-09](https://github.com/Gerbesh/unusual-copper/issues/9) |
| [UC-14: Алхимический автоклав и стадии варки](https://github.com/Gerbesh/unusual-copper/issues/14) | P1 | [UC-07](https://github.com/Gerbesh/unusual-copper/issues/7), [UC-08](https://github.com/Gerbesh/unusual-copper/issues/8) |
| [UC-15: Резонатор зачарований и реальные XP points](https://github.com/Gerbesh/unusual-copper/issues/15) | P1 | [UC-08](https://github.com/Gerbesh/unusual-copper/issues/8) |

## [M5 — Совместимость и релиз 1.0](https://github.com/Gerbesh/unusual-copper/milestone/6)

| Задача | Приоритет | Зависит от |
|---|---|---|
| [UC-16: Проверенные адаптеры SSN/Progressive и JEI](https://github.com/Gerbesh/unusual-copper/issues/16) | P2 | [UC-07](https://github.com/Gerbesh/unusual-copper/issues/7), [UC-14](https://github.com/Gerbesh/unusual-copper/issues/14), [UC-15](https://github.com/Gerbesh/unusual-copper/issues/15) |
| [UC-17: Баланс, профилирование и полная регрессия](https://github.com/Gerbesh/unusual-copper/issues/17) | P0 | [UC-12](https://github.com/Gerbesh/unusual-copper/issues/12), [UC-13](https://github.com/Gerbesh/unusual-copper/issues/13), [UC-14](https://github.com/Gerbesh/unusual-copper/issues/14), [UC-15](https://github.com/Gerbesh/unusual-copper/issues/15), [UC-16](https://github.com/Gerbesh/unusual-copper/issues/16) |
| [UC-18: Финальные ассеты, руководство и release 1.0](https://github.com/Gerbesh/unusual-copper/issues/18) | P1 | [UC-02](https://github.com/Gerbesh/unusual-copper/issues/2), [UC-17](https://github.com/Gerbesh/unusual-copper/issues/17) |
