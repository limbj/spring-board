# 실행 가이드

## 요구 환경

- JDK 17 이상 (Windows 스크립트는 없으면 프로젝트 안에 자동 준비)
- 최초 빌드를 위한 인터넷 연결
- Windows 빠른 실행 스크립트 또는 Maven 3.9 이상
- 별도 WAS 배포 시 Tomcat 11

Windows에서 Java 8만 있거나 Java가 없으면 스크립트가 공식 Temurin 17.0.20.1+1 ZIP(약 191 MB)을 내려받아 SHA-256 확인 후 `.tools/jdk17/`에 준비합니다. 시스템 Java 설정은 바꾸지 않습니다. 직접 WAS에 배포할 때는 Java 17과 Tomcat 11이 필요합니다. JDK는 [Eclipse Temurin 공식 배포](https://adoptium.net/temurin/releases/?version=17)에서 준비할 수 있습니다.

## Windows에서 실행

프로젝트 폴더에서 CMD 또는 PowerShell을 엽니다. 설치한 JDK 17 이상을 지정하려면 `JAVA_HOME`을 설정할 수 있습니다. 자동 준비를 사용할 때는 아래 첫 줄을 생략합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17'
.\build.cmd
.\run-local.cmd
```

예시 경로는 실제 설치 폴더로 바꿉니다. 빌드 스크립트는 Maven 3.9.16을 프로젝트의 `.tools/`에 준비하고 SHA-512를 검증합니다. 의존성은 같은 폴더의 로컬 저장소에 내려받습니다.

접속 주소: [http://localhost:8080/spring-board/](http://localhost:8080/spring-board/)

기본 실행기는 127.0.0.1에 바인딩됩니다. 포트 변경은 다음 명령으로 가능합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\run-local.ps1 -Port 8081
```

`Ctrl+C`로 종료합니다. 서버 실행 중 같은 폴더의 DB를 사용하는 두 번째 서버를 시작하면 DB 파일 잠금으로 실패할 수 있습니다.

## Maven · Tomcat

```bash
mvn clean verify
```

`target/spring-board.war`를 Tomcat 11의 `webapps/`에 배포합니다. Tomcat 실행 프로세스의 작업 폴더 기준으로 `data/`가 만들어지므로, 외부 서버에서는 아래 데이터·업로드 경로를 절대 경로로 지정하는 편이 좋습니다.

Eclipse에서는 Maven Existing Projects로 가져온 뒤 JDK 17과 Tomcat 11 런타임을 선택합니다. `.project`, `.classpath`, `.settings/`는 소스에 포함하지 않습니다.

## 데이터 · 업로드 폴더

| 설정 | 기본값 | 환경변수 |
| --- | --- | --- |
| DB 프로필 | `h2` | `SPRING_BOARD_DB_PROFILE` |
| 데이터 폴더 | `./data` | `SPRING_BOARD_DATA_DIR` |
| 업로드 폴더 | `./data/uploads` | `SPRING_BOARD_UPLOAD_DIR` |
| JDBC URL | H2 파일 URL 자동 생성 | `SPRING_BOARD_DB_URL` |
| DB 계정 | H2: `sa` | `SPRING_BOARD_DB_USER` |
| DB 비밀번호 | H2: 빈 값 | `SPRING_BOARD_DB_PASSWORD` |

H2 스키마는 최초 실행 시 자동 생성합니다. 재시작 시 기존 테이블과 데이터를 지우지 않습니다. H2는 Oracle 호환 모드를 사용합니다.

JVM 속성으로는 `springboard.db.profile/url/user/password`, `springboard.dataDir`, `springboard.uploadDir`를 지정할 수 있습니다. JVM 속성이 환경변수보다 우선합니다.

기본 경로 밖으로 DB나 업로드 위치를 바꿀 때에는 저장소 외부 폴더를 사용하세요. `.gitignore`의 폴더 규칙은 프로젝트의 기본 `data/`, `uploads/`, `runtime/` 경로를 대상으로 합니다.

## Oracle 연결 옵션

Oracle JDBC 드라이버를 포함하며 Oracle 19c 이상을 대상으로 연결 환경을 구성할 수 있습니다. 실제 Oracle 서버 검증은 수행하지 않았습니다.

1. 비어 있는 게시판 전용 스키마의 소유자 계정으로 루트 `ddl.sql`을 한 번 실행합니다. 기존 데이터가 있는 스키마에는 이 신규 생성용 DDL을 그대로 적용하지 말고, 백업 후 기존 테이블·열·제약조건과의 호환성을 먼저 확인합니다.
2. 서버를 시작하는 터미널 또는 WAS에 환경변수를 설정합니다.

```powershell
$env:SPRING_BOARD_DB_PROFILE = 'oracle'
$env:SPRING_BOARD_DB_URL = 'jdbc:oracle:thin:@//localhost:1521/ORCLPDB1'
$env:SPRING_BOARD_DB_USER = 'board_user'
$env:SPRING_BOARD_DB_PASSWORD = '<실제 비밀번호>'
.\run-local.cmd
```

Oracle 프로필에서는 자동 DDL을 실행하지 않습니다. 실제 비밀번호를 소스나 README에 저장하지 마세요.

## GitHub에 올리는 파일

소스·테스트·Maven 설정·DDL·실행 스크립트·문서·화면 이미지를 올립니다. `.gitignore`가 빌드 산출물, 도구 캐시, 실행기 번들, DB, 업로드, 로그와 IDE 설정을 제외합니다.

Repo 이름: `spring-board`

Repo 설명: `Spring MVC와 MyBatis로 구현한 검색·댓글·첨부파일 게시판`

GitHub에 빈 저장소를 만든 뒤 프로젝트 폴더에서 실행합니다. 처음 생성할 때 README·.gitignore를 추가하지 않으면 이 폴더의 파일과 바로 연결할 수 있습니다.

```bash
git init
git add .
git status
git commit -m "Initial commit: Spring Board"
git branch -M main
git remote add origin https://github.com/limbj/spring-board.git
git push -u origin main
```

위 주소는 `limbj/spring-board`로 저장소를 만들었을 때의 예시입니다. `git status`에서 DB·업로드·비밀번호 파일이 포함되지 않았는지 확인하세요.
