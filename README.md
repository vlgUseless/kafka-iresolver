# Анализ обращений Kafka с iResolver

Лабораторная работа № 5. Программа анализирует открытые задачи: находит похожие исправленные обращения, страницы документации, кандидатов в исполнители и рекомендации правил Drools.

## Запуск

Windows x64 с PowerShell 7 или macOS; для подготовки зависимостей и данных — Python 3.9+.

На macOS из корня проекта:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python scripts/setup.py
.venv/bin/python scripts/fetch_data.py
.venv/bin/python scripts/prepare_docs.py
bash run.sh --test
bash run.sh
open output/report.html
```

`setup.py` устанавливает локальный JDK 11 x64. На Apple Silicon нужен Rosetta: если он отсутствует, выполните `softwareupdate --install-rosetta --agree-to-license`. Старые нативные библиотеки ND4J запускаются через Java x64. Системная Java не изменяется. `bash run.sh` компилирует исходники и обучает отсутствующие модели; `bash run.sh --train` принудительно переобучает их. Для следующих запусков достаточно `bash run.sh`.

В Windows после подготовки:

```powershell
.\run.ps1
.\run.ps1 -Test
```

Результат — `output/report.html`; данные расчётов — `output/results.json`. `-Test` выполняет 10 проверок DRL, снимка и обучения моделей. Повторное обучение: `.\run.ps1 -Train`.

## Подготовка после клонирования

```powershell
python scripts/setup.py
python -m pip install -r requirements.txt
python scripts/fetch_data.py
python scripts/prepare_docs.py
.\run.ps1 -Train
```

`setup.py` скачивает iResolver 1.1.0, JDK 11 для выбранной ОС и руководства Kafka. Архивы проверяются по SHA-256. Системная Java не изменяется. Новая загрузка Jira получает актуальные задачи; исходный эксперимент выполнен 01.11.2026 на 1000 исправленных задачах и 5 открытых ошибках. Подготовка документации дала 493 страницы. Параметры обучения и пути задаются в `config/configuration.yml`.

## Код

Исходники находятся в `src/main/java/ru/polytech/iresolver`:

| Класс | Назначение |
| --- | --- |
| `Experiment` | Последовательность запуска |
| `SnapshotClient` | Чтение снимка и поиск карточки по ключу |
| `JiraIssueMapper` | Преобразование JSON в объекты iResolver |
| `ModelService` | Загрузка и обучение Doc2Vec |
| `AnalysisService` | Вызов процессоров iResolver и Drools |
| `ResultWriter` | Запись HTML, JSON и YAML |

Проверки — `src/test/java/ru/polytech/iresolver/ExperimentTest.java`. Новое правило — [`rules/KafkaReproduction.drl`](rules/KafkaReproduction.drl): если описание ошибки Kafka не содержит маркеров воспроизведения, предложить запросить уточнения. Это проверка по словам; она может ошибаться. Неиспользуемые исходные правила находятся в `rules/disabled`.

[Как работает алгоритм с диаграммами](docs/ALGORITHM.md).

Обучение, поиск и HTML-генератор используются из [iResolver](https://github.com/kovaloid/iresolver/tree/2480ab354472e504a0825298023366b7f047eba2). Сторонняя лицензия — `THIRD_PARTY_LICENSE.txt`. Учебный LaTeX-отчёт хранится отдельно, в соседнем каталоге `Задание_5/Отчёт`.

Данные, модели, PDF-руководства, Java, библиотеки и результаты запуска исключены из Git. Ранг инженеров отражает участие в похожих задачах; сходство не доказывает применимость исправления. Программа читает снимок и формирует подсказки, без изменений в Jira.
