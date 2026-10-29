# Личен Garmin Workout backend — 0.23.0

Тази услуга създава/обновява структурирани силови тренировки чрез неофициалния
`python-garminconnect`, pinned към commit
`3eee7a4db6c2242967aae1780b8839599ea7dcbb`. Не е официален Garmin OAuth и може да
бъде ограничена или повредена от промени при доставчика. Не импортира записани
FIT активности като тренировки. Не е публична multi-user услуга.

Кодът и mock тестовете не доказват реално Garmin приемане. Не е внедряван на
потребителски сървър и не е използван с реални Garmin credentials в Codex.
Входът/MFA се изпълняват **само в доверената конзола на твоя сървър**. Парола
не се пази; Garmin сесията се пази криптирана с Fernet. Не изпращай парола,
MFA код, сесия, pairing ключ или vault.key към Codex/ChatGPT/GitHub.

## Изисквания

- Собствен Linux/Proxmox VM/LXC с Docker Engine и Compose plugin.
- Доверен администраторски достъп, непривилегирован потребител, дисково
  криптиране/защитени backup-и за секретите. Достъпът до Docker е привилегирован.
- HTTPS адрес с валиден доверен сертификат; за предпочитане достъпен само през
  лична мрежа/VPN. Android не приема HTTP или изключване на TLS проверките.
- Garmin акаунт и нормално завършване на MFA, ако Garmin го поиска.
- Изходящ мрежов достъп към Garmin; при CAPTCHA/WAF блокиране спри, без bypass.
  Използва се един обикновен `mobile+requests` SSO вариант. Browser-impersonation
  и alternate-login fallback след rate limit са изключени. Този консервативен
  вход може да бъде отказан от Garmin; не е гарантиран работещ за всеки акаунт.

Няма нужно USB свързване на часовника. След реално приемане в Garmin Connect
използвай неговото нормално Send to Device/Bluetooth синхронизиране. Това трябва
да се провери на Enduro 3, не се потвърждава само от workout ID.

## 1. Изтегли и компилирай на собствения сървър

Изпълни от клонираното репозитори като непривилегирован потребител:

```sh
git fetch origin feature/workout-builder-garmin
git switch feature/workout-builder-garmin
printf 'BAIR_UID=%s\nBAIR_GID=%s\n' "$(id -u)" "$(id -g)" > backend/workouts/.env
docker compose --env-file backend/workouts/.env -f backend/workouts/compose.yml build
```

Провери стойностите в `.env`; не използвай UID 0 за нормалната услуга. Файлът
съдържа само UID/GID, не credentials. Той и `private/` са gitignored и са
изключени от Docker build context.

## 2. Създай личните ключове локално, без да ги публикуваш

```sh
mkdir -p backend/workouts/private
chmod 700 backend/workouts/private
docker run --rm --user "$(id -u):$(id -g)" \
  -v "$PWD/backend/workouts/private:/bootstrap" \
  bair-workouts-local python -m backend.workouts.manage init --directory /bootstrap
```

Генерират се отделни случайни `private/secrets/vault.key` и `app.token`, с права
0600. Съществуващи ключове не се презаписват. `private/data/` е 0700. Backend
контейнерът получава секретите read-only и записва само в `/data`. Не качвай
тази директория в Git, build context, artifacts или публични backup-и.

## 3. Garmin вход и MFA само на сървъра

Спри нормалната услуга преди конзолен вход, за да не сменяш акаунта по време на
заявка. Това е локална настройка, не изисква парола в Android:

```sh
docker compose --env-file backend/workouts/.env -f backend/workouts/compose.yml stop backend
docker compose --env-file backend/workouts/.env -f backend/workouts/compose.yml run --rm -it backend \
  python -m backend.workouts.manage login
```

Въведи Garmin email, парола и поискан MFA код. Паролата/MFA не се показват при
писане и не се записват в конфигурация. Не използвай command-line аргументи или
GitHub Secrets за тези данни. При успех се създава само
`private/data/garmin-session.enc`. Ако входът е блокиран, не заобикаляй Garmin
контролите и не приемай създаването на локален файл за доказано свързване.

## 4. Стартирай и осигури HTTPS

```sh
docker compose --env-file backend/workouts/.env -f backend/workouts/compose.yml up -d
```

Контейнерът публикува **само `127.0.0.1:8088`**. Не отваряй този HTTP порт към
интернет. Настрой собствен TLS reverse proxy на същия host. Например Caddy:

```caddyfile
garmin.example.com {
    reverse_proxy 127.0.0.1:8088
}
```

Замени домейна със свой и настрой DNS/издаването на сертификат според мрежата.
Примерът не настройва DNS, firewall, VPN или реален сертификат вместо теб.
Използвай един backend instance/worker и един и същ постоянен data volume.
Публичен multi-user достъп изисква отделен security review и не се поддържа.

## 5. Свържи Android

1. Инсталирай актуалния **bAIr Workouts Test** APK от GitHub Actions.
2. Тренировки → избери тренировка → **Изпрати към Garmin**.
3. Въведи само корена на HTTPS адреса, например `https://garmin.example.com`.
   Не се приемат парола в URL, query, fragment или `/api` path.
4. Прочети `backend/workouts/private/secrets/app.token` в личната си сървърна
   конзола и постави стойността в полето **Ключ за backend връзката**. Това
   **не е Garmin Personal API ключ**. Не публикувай ключа в чат или screenshot.
5. Потвърди доверието към собствения сървър и избери **Запази и провери връзката**.
   При валидна Garmin сесия трябва да пише, че backend и Garmin са свързани.
6. Прегледай и изрично разреши общите упражнения без точен каталожен mapping.
7. При вече изпратена и редактирана тренировка избери **Обнови съществуващата**
   или **Създай нова**. Повторно изпращане на вече потвърдено същото съдържание
   не създава дубликат; за отделна идентична тренировка първо дублирай локално.
8. Натисни **Изпрати към Garmin** и потвърди workout/адрес/действие в диалога.
9. Успех се показва само при действително върнат положителен Garmin workout ID.
   Провери името, четирите загрявания, времената и двата кръга в Garmin Connect.
10. Изпрати към Enduro 3 през Garmin Connect и направи Bluetooth sync. Провери
    показването на самия часовник — тази стъпка остава реален потребителски тест.

Backend pairing ключът е обвързан с HTTPS origin и криптиран с Android
Keystore. При промяна на адреса трябва изрично да се въведе ключ за новия адрес;
старият не се препраща автоматично. HTTP redirects не се следват. Garmin
парола, refresh token и сесия не се изпращат към телефона.

## Неясен резултат, дубликати и смяна на акаунт

Преди Garmin POST/PUT сървърът записва durable intent в private SQLite ledger.
Същият ключ/съдържание връща вече получения ID. Ledger пази само account
binding, request/payload hashes, статус и ID — не сесии, credentials или body.
Успешният ID остава достъпен след рестарт. При timeout/crash/невалиден отговор
заявката остава pending и не се изпраща повторно сляпо.

В Android избери **Провери резултата (без ново изпращане)**. Това прави GET на
съществуващия резултат, не нов POST. Ако сървърът е приключил след timeout,
локалният ID/статус се възстановяват. Ако сървърът е прекъснал между Garmin
приемането и записването на ID, автоматично разрешаване не е възможно: провери
Garmin Connect и не изтривай ledger/pending записа с цел сляпо повторение.
Няма автоматично дистанционно reconciliation по име или бутон за blind reset.

Ledger е разделен по Garmin account binding. Android пази отделно акаунта за
потвърдени и чакащи изпращания и отказва update/recovery през друг backend или
акаунт. Ново съдържание, редактирано докато изпращането върви, не се презаписва
от отговора; изтрит локален workout не се възкресява. Локалното изтриване не
изтрива вече създаден workout от Garmin Connect.

## Изтриване и изтичане на сесия

- **Прекъсни и изтрий Garmin сесията на сървъра** изисква мрежа, изтрива
  криптираната сесия и след потвърден отговор изчиства ключа от телефона.
- **Забрави достъпа само на този телефон** работи локално; ясно предупреждава,
  че сесията на сървъра остава.
- Конзолно изтриване: `python -m backend.workouts.manage disconnect` през
  `docker compose run --rm backend ...`, при спрян нормален backend.
- Това е премахване на локални сървърни токени, не обещание за Garmin-side
  revocation. За отнемане на вече издадени Garmin сесии използвай наличните
  настройки за сигурност на Garmin акаунта.
- При изтекла/отхвърлена Garmin сесия повтори стъпка 3 с нормалния MFA процес.
- За отнемане на backend pairing ключа генерирай нов `app.token` локално,
  рестартирай услугата и въведи новия ключ на телефона. Vault ключът е отделен;
  не го сменяй без план за сесиите и backup-ите.

## Проверки и граници

```sh
python -m pip install -r backend/workouts/requirements-test.txt
python -m unittest discover -s backend/workouts/tests -v
```

Тестовете използват fake Garmin отговори, не реални credentials. CI също
компилира Docker image с pinned upstream клиента и проверява импортите без
вход. Услугата няма endpoint за email/парола/MFA, няма аналитика, CORS достъп
или публичен workflow-dispatch trigger. Не логва тела, Authorization headers
или provider responses. HTTP API изисква случаен bearer ключ, сравняван по
hash с constant-time comparison; има request/rate/size/schema ограничения.

Реално свързване, Garmin приемане и Enduro 3 видимост остават непотвърдени,
докато не се изпълнят с твоя собствен внедрен backend и акаунт.
