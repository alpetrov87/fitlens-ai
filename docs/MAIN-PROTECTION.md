# Защита на main

Pipeline-ът `.github/workflows/android.yml` вече изпълнява build, unit tests, lint и публикува APK artifact при push в main и при Pull Request. Той няма deployment или Google Play публикация. `CODEOWNERS` насочва review към @alpetrov87.

## Необходима настройка в GitHub

**Правилата все още не са активирани от Codex:** GitHub API връща Forbidden за административните операции. CODEOWNERS и workflow файловете сами по себе си не забраняват директни push-ове.

Собственикът трябва да отвори Settings → Rules → Rulesets → New branch ruleset (на телефон може да е нужна Desktop site):

1. Име `Protect main`, Enforcement status **Active**.
2. Target branches → Include → Default branch (или branch name `main`).
3. Без bypass потребители/роли, ако правилото трябва да важи и за администратора.
4. Включи **Restrict deletions**, **Block force pushes**.
5. Включи **Require a pull request before merging**.
6. Включи **Require status checks to pass**, добави проверката **build** от GitHub Actions / Android debug APK и **Require branches to be up to date**. Ако проверката не е налична, изчакай първия main build.
7. Включи **Require conversation resolution**.
8. За самостоятелен проект остави Required approvals на **0**: собственикът не може да одобрява собствените си PR-и. Ако има втори доверен reviewer, задай **1**, Require review from Code Owners и Dismiss stale approvals.
9. Save changes. Не включвай Restrict updates без подходящ модел за bypass: то може да блокира и нормалните PR merge операции.

Алтернатива: Settings → Branches → Add classic branch protection rule → pattern `main`, същите настройки и Do not allow bypassing the above settings.

## Достъп до репозиторито

Публично репо не означава, че всеки може да push-ва. Това могат само собственикът и акаунти с write/admin достъп. Прегледай Settings → Collaborators and teams и остави write права само на доверени хора. Външни потребители могат да предлагат PR-и от fork, но не могат сами да ги обединят.

Всички бъдещи промени: отделен feature branch → PR → успешен build → merge. Защитите се проверяват в GitHub UI след запазването им; не приемай наличието на този документ за доказателство, че са включени.
