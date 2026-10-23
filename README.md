# FitLens AI — Android MVP

Първа демо версия с Kotlin и Jetpack Compose. Работи офлайн, без акаунт, ключове или разрешения за лични данни. Екранът показва примерни крачки, активни минути, разстояние, калории и макронутриенти, демо съвет и статус на интеграциите. Бутонът за обновяване презарежда същите фиксирани демо стойности.

## Изтегляне на APK от GitHub

1. Отвори **Actions → Android debug APK** в това репозитори.
2. Избери успешния run за желания branch/commit. Pipeline се изпълнява при pull request, push към `main`/`feature/**` и ръчно чрез **Run workflow**.
3. В **Artifacts** изтегли `fitlens-ai-debug-<номер>` (нужен е GitHub вход).
4. Разархивирай ZIP файла. Инсталационният файл е `app-debug.apk`. Artifacts се пазят 14 дни; при изтичане стартирай workflow отново.

Pipeline компилира APK, изпълнява unit тестовете и Android Lint. Artifact се публикува само след успешни проверки. Няма deployment, качване в Google Play или автоматично инсталиране.

## Инсталиране на телефон

Изисква Android 8.0/API 26 или по-нов.

- Прехвърли `app-debug.apk` на телефона и го отвори. Ако Android поиска, разреши „Install unknown apps“ само за приложението, с което отваряш файла. След инсталиране можеш да отнемеш това разрешение.
- Или включи Developer options → USB debugging, свържи телефона, потвърди връзката и изпълни:

```sh
adb devices
adb install -r app-debug.apk
```

Отвори **FitLens AI**. Трябва да видиш „ДЕМО РЕЖИМ“, 7840 крачки и 1640/2200 kcal. Превърти до интеграциите и тествай „Обнови демо данните“.

APK е подписан с development debug ключ и не е production release. Различни CI машини могат да използват различни debug ключове: при `INSTALL_FAILED_UPDATE_INCOMPATIBLE` деинсталирай предишната демо версия (`adb uninstall com.fitlens.ai`, изтрива нейните данни) и инсталирай отново. Не използвай debug APK за реални здравни данни.

## Локално разработване

Android Studio с JDK 17 (JDK 21 също се поддържа от Gradle 8.9), Android SDK Platform 35, Build Tools 34.0.0 и Platform Tools. Gradle 8.9 се изтегля през включения wrapper с SHA-256 проверка.

Отвори проекта в Android Studio, изчакай Gradle sync и натисни Run с избран емулатор или телефон. От терминал:

```sh
export ANDROID_HOME=/path/to/android-sdk
# или задай sdk.dir=/path/to/android-sdk в локален local.properties
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Отчети: `app/build/reports/tests/testDebugUnitTest/index.html` и `app/build/reports/lint-results-debug.html`. Unit тестовете проверяват композицията на доставчиците, демо стойностите и предаването на грешки. Това не замества UI проверка на телефон/емулатор.

## Архитектура и бъдещи интеграции

`MainActivity` свързва зависимостите → `DashboardViewModel` управлява `StateFlow` с Loading/Ready/Error → Compose наблюдава състоянието с lifecycle awareness. `HealthRepository` предоставя общ `Dashboard`; `DefaultHealthRepository` комбинира `ActivitySource` и `NutritionSource`. В MVP те са `DemoActivitySource` и `DemoNutritionSource`. Preview е наличен в `DashboardScreen.kt`.

За Garmin/MyFitnessPal добави реални адаптери зад съответните интерфейси и ги подай на repository, без да променяш екрана. Преди това са нужни одобрен API достъп/партньорство според условията на доставчика, потребителско съгласие, backend OAuth поток, сигурно съхранение на токени, синхронизация, обработка на часови зони, единици, rate limits и изтриване на данни. Наличието на публичен API не е предпоставка на тази демо версия. Не вграждай client secrets или access tokens в APK/репозитори. Реалните адаптери трябва да получат собствени contract/integration тестове.

В момента няма мрежови заявки, истинска Garmin/MyFitnessPal връзка, AI модел, трайно съхранение или медицински препоръки.
