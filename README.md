# FitLens AI — Android MVP

Първа версия с Kotlin и Jetpack Compose. Без Garmin ключ приложението показва примерни крачки, активни минути, разстояние, калории и макронутриенти. С личен Garmin Personal API key може да се синхронизират дневните Garmin показатели. MyFitnessPal данните могат да се добавят чрез CSV импорт.

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

## Архитектура и Garmin Personal API

`MainActivity` свързва зависимостите → `DashboardViewModel` управлява `StateFlow` с Loading/Ready/Error → Compose наблюдава състоянието с lifecycle awareness. `HealthRepository` предоставя общ `Dashboard`; `DefaultHealthRepository` комбинира `ActivitySource` и `NutritionSource`. В MVP те са `DemoActivitySource` и `DemoNutritionSource`. Preview е наличен в `DashboardScreen.kt`.

За Garmin има личен Personal API адаптер, който се управлява от таблото. При синхронизация той изтегля дневните крачки, разстояние, активни минути, записан прием на калории и списъка с активности за текущата дата. Обобщенията и общите детайли за всяка активност се пазят локално; спортният `typeKey` се пази отворен, така че нови Garmin спортове не изискват промяна на модела. API ключът се криптира с Android Keystore, а краткотрайният токен остава само в паметта.

MyFitnessPal продължава да работи чрез CSV импорт. Няма AI модел или медицински препоръки. Импортираните обобщения се пазят локално на устройството.

## Импорт от Garmin Connect и MyFitnessPal

На таблото избери **Избери Garmin CSV** или **Избери MyFitnessPal CSV**, после избери CSV файл от Android file picker. Обработката е локална: запазват се само обобщените числа в private app storage; CSV файлът, access tokens и пароли не се запазват и не се качват. Избраните обобщения преживяват рестарт на приложението. Нов импорт от същия доставчик и дата обновява този доставчик; Garmin и MyFitnessPal редовете за една дата се съчетават. Бутонът **Изтрий импортираните данни** премахва всички записани импорти от приложението.

**Garmin CSV:** една дневна сума на ред, дата и поне едно поле `Steps`, `Active Minutes`/`Intensity Minutes`, `Distance (km)` или `Distance (m)`.

```csv
Calendar Date,Steps,Active Minutes,Distance (km)
2026-10-22,7840,42,5.6
```

**MyFitnessPal CSV:** хранителни редове с дата и поне едно от `Calories`, `Protein (g)`, `Carbs (g)`, `Fat (g)`. Редовете за отделните хранения/храни на една дата се сумират. Поддържат се запетая, точка и запетая и tab като CSV разделител, UTF-8, ISO дата, американска `M/D/YYYY` дата и българска `D.MM.YYYY` дата.

```csv
Date,Calories,Protein (g),Carbs (g),Fat (g)
2026-10-22,620,35,60,18
2026-10-22,410,28,45,12
```

Всеки източник показва отделно най-новата дата от своя файл. Метриките, които липсват от избрания файл, остават изрично обозначени като демо. Тази версия импортира CSV; не импортира Garmin FIT/GPX/TCX архиви и не влиза в Garmin Connect или MyFitnessPal вместо потребителя.

### Garmin Personal API: първоначална настройка

1. Впиши се в Garmin Connect Web със своя акаунт и отвори [Personal API keys](https://connect.garmin.com/app/settings/personalApiToken).
2. Избери **Create Key**, приеми условията и задай срок на ключа. Garmin посочва срокове до една година; екранът на Garmin Connect показва наличните опции.
3. Копирай ключа, когато бъде показан. Той се вижда само веднъж. Не го изпращай в чат, issue или commit.
4. Инсталирай актуалния debug APK, отвори секцията **Garmin Personal API**, постави ключа и избери **Свържи и синхронизирай**. Приложението заявява access token и тегли днешното обобщение. На таблото източникът се показва като Garmin Personal API.
5. Следващия път натисни **Изтегли данните за днес**. Синхронизират се крачки, дистанция, активно време, Garmin Connect дневните калории от храна и активности за днешната локална дата. За всяка активност се пазят Garmin ID, име, спортен type key, локален начален час, продължителност, дистанция, калории, крачки, среден/максимален пулс и изкачване, когато Garmin ги предоставя. Приложението показва тези данни под обобщението за активностите.
6. Garmin API описва спортовете като `activityType.typeKey` (примерите в схемата включват running, cycling и swimming); полето не е ограничено до фиксиран enum. Така се запазват и други върнати спортове, включително отделните части на multisport сесия.
7. Ключът се пази криптиран локално с Android Keystore. Токенът не се записва; нов 24-часов token се заявява при всяка синхронизация, защото Garmin Personal API няма refresh token.
8. **Изтрий ключа и Garmin данните** премахва ключа от приложението, локалния Android Keystore ключ, обобщенията и записаните активности. За отнемане от Garmin Connect използвай управлението на Personal API ключовете в Garmin профила.

Интеграцията следва официалните [Personal API инструкции](https://developer.garmin.com/gc-developer-program/personal/personal-api/), [FAQ](https://developer.garmin.com/gc-developer-program/personal/personal-faq/) и [API описание](https://developer.garmin.com/gc-developer-program/personal/swagger-docs/). Използва `usersummary/daily` за крачки/калории и `activitylist-service/activities/search/activities` с днешните `startDate`/`endDate`. Това пази summary данните за активностите, не оригинални FIT/GPX тракове или цялата подробна телеметрия. Garmin Personal API е за личните данни на собственика на ключа, не за комерсиално multi-user приложение. Трябва да имаш Garmin Connect акаунт и да приемеш условията им.

### MyFitnessPal и бъдещ API достъп

- MyFitnessPal: тази версия приема CSV без директен API достъп. Официалният API/support сайт не беше достъпен за проверка в тази среда, затова наличието на self-service API и условията за партньорство остават непотвърдени. Не използваме scraping, неофициални endpoints или session cookies. CSV импортът е наличният път за добавяне на MyFitnessPal данни.

Garmin API е личен ключов поток, а не OAuth client/backend интеграция. MyFitnessPal API статусът не е потвърден; преди да добавяме директна връзка, трябва да се използва официално документиран или одобрен от MyFitnessPal достъп.
