# Требования к сборке

Дата актуализации: 2026-05-29.

## Поддерживаемое окружение

- JDK: 21 LTS.
- Maven: 3.9.16 через Maven Wrapper.
- Кодировка исходников и отчетов: UTF-8.

Основной способ запуска сборки:

```bash
./mvnw clean verify
```

Для сборки отдельного модуля вместе с зависимыми модулями:

```bash
./mvnw -pl services/build-service -am verify
```

## Maven profiles для запуска сервисов

В parent POM определены Maven profiles `local` и `test`.
Профиль `local` активен по умолчанию и используется Spring Boot Maven Plugin при запуске executor-сервиса:

```bash
./mvnw -pl services/build-service -am spring-boot:run
```

Эквивалентная явная команда:

```bash
./mvnw -Plocal -pl services/build-service -am spring-boot:run
```

Для запуска сервиса с test-конфигурацией:

```bash
./mvnw -Ptest -pl services/build-service -am spring-boot:run
```

Maven profiles управляют параметром `spring.profiles.active`.

## Maven Wrapper

В репозитории используется Maven Wrapper `3.3.4` в режиме `only-script`.
Wrapper закрепляет Maven `3.9.16` в `.mvn/wrapper/maven-wrapper.properties` и не хранит бинарный `maven-wrapper.jar` в репозитории.

Первый запуск `./mvnw` скачает Maven-дистрибутив в локальный Maven user home:

```text
~/.m2/wrapper/dists/
```

В изолированных окружениях можно перенести этот cache во временную директорию:

```bash
MAVEN_USER_HOME=/tmp/cicd-maven-home ./mvnw clean verify
```

## Enforcer-проверки

Корневой `pom.xml` включает Maven Enforcer на фазе `validate`.

Если используется не JDK 21, сборка падает с сообщением:

```text
Требуется JDK 21. Проверьте JAVA_HOME и запускайте сборку через ./mvnw.
```

Если используется Maven ниже закрепленной версии или Maven 4 preview, сборка падает с сообщением:

```text
Требуется Maven 3.9.16. Используйте Maven Wrapper: ./mvnw.
```

Это ожидаемое поведение: оно защищает многомодульную сборку executor-слоя от разных локальных версий Maven и JDK.
