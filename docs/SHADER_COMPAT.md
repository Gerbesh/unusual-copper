# Совместимость шейдеров и ручной запуск

Дата проверки: 15 октября 2026. Minecraft 1.20.1; Unusual Copper 0.10.0-alpha.1.

## Ручной запуск разработки

Из `D:\Unusual copper` запустить PowerShell:

```powershell
.\gradlew.bat runClient
```

Это обычный клиент Forge для ручного исследования без автотеста. Существующие миры хранятся внутри `run/saves`, не в основном модпаке.

Чтобы добавить в этот dev-клиент тестовые Oculus, Xenon, EuphoriaPatcher и Iris Shader Folder:

```powershell
.\gradlew.bat -PshaderClient runClient
```

Опция `-PshaderClient` **не запускает автотест** и не закрывает клиент: можно самостоятельно создать мир и осмотреть машины. Зависимости предварительно скопированы в `.codex-tmp/compat` и ремаппятся ForgeGradle. Используется локальная копия шейдера и настроек в `run/shaderpacks` и `run/config/oculus.properties`. Точный Medium пока не компилируется в этой облегчённой среде, см. результаты ниже.

Для повторения строгого автоматизированного теста (на ошибках прерывает запуск):

```powershell
.\gradlew.bat --no-daemon -PsmokeShaders runClient
```

## Конфигурация оригинальной Better MC

Оригинальные файлы были прочитаны из `C:\Users\gerbe\curseforge\minecraft\Instances\Better MC [FORGE] BMC4`. Модификации пользовательской сборки НЕ производились.

- Forge 47.4.20, Oculus 1.8.0, Xenon 0.3.31, EuphoriaPatcher 1.9.3, Iris Shader Folder 1.4.1.
- Шейдер `Better MC - Medium` включает полноценную папку из 616 файлов, а не только `.txt`; шейдеры включены в Oculus.
- Исходный ZIP: `ComplementaryReimagined_r5.8.1.zip`; параметры Medium содержат `MCBL_MAIN_DEFINE=2`.

## Фактическая проверка

- **PASS:** Forge 47.4.10, Oculus + Xenon + EuphoriaPatcher, но активен базовый `ComplementaryReimagined_r5.8.1.zip`. `IrisApi.isShaderPackInUse()` вернул true, получены дневной и ночной скриншоты. Клиент штатно завершён.
- **FAIL:** `Better MC - Medium` с оригинальной папкой, на Forge 47.4.10 и 47.4.20. Oculus выбирает Medium, но падает компиляция `final.fsh`: `0(874): error C1503: undefined variable "_10"`; шейдеры принудительно отключаются. Strict smoke сознательно останавливает клиент вместо ложного успеха.
- **FAIL:** генерируемый EuphoriaPatcher вариант с идентичным Medium-набором параметров даёт ту же ошибку.
- **Отдельное наблюдение:** журнал оригинального Better MC от 14 октября содержит много успешных `Using shaderpack: Better MC - Medium` без аналогичного сообщения об ошибке. Значит, не следует утверждать, что исходный шейдерпак пользователя сломан: отличие изолированной среды пока не определено.
- Скриншоты базового контрольного пакета (НЕ Medium): `run/screenshots/cue-shaders-complementary-control-day.png` и `cue-shaders-complementary-control-night.png`. В сцене шесть ориентаций SSN Bridge, машины со стеклом, два вида труб и подсветка. Явных крупных визуальных артефактов на двух кадрах не видно; детальную приёмку материалов они не заменяют.

## Ручная проверка в исходной сборке

Для наиболее точной проверки создайте **копию** профиля Better MC и его тестовый мир, добавив собранный `build/libs/unusualcopper-0.10.0-alpha.1.jar` в папку `mods` копии. Откройте в новой копии Minecraft с активным `Better MC - Medium`; сначала испытайте модели, затем GUI, затем сеть и серверную автоматику. Не переносите незавершённую alpha в единственный сохранённый мир.

## Незакрытые задачи

Разобраться в различии pipeline компиляции Euphoria между исходной Better MC и облегчённым ForgeGradle-клиентом; запустить полноценную тестовую копию модпака; проверить FPS и 95-й процентиль MSPT на насыщенной сети, включая 50–100 машин. Клиентский smoke JEI со связкой SSN+Progressive+OpenPAC прошёл без шейдеров; эту связку с активным Medium ещё предстоит проверить.
