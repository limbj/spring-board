# Spring Board

**Spring MVC와 MyBatis로 구현한 게시판 — 검색, 페이징, Ajax 댓글, 첨부파일**

게시글 작성부터 댓글과 파일 관리까지 이어지는 웹 게시판입니다. XML 기반 Spring MVC 구조를 유지하면서 Java 17, Spring 7, Jakarta Servlet 환경으로 정리했습니다. 기본 H2 데이터베이스가 자동으로 준비되어 Oracle 설치 없이 실행할 수 있습니다.

> 로그인 없이 이용하는 공개 게시판입니다. 누구나 게시글과 댓글을 수정·삭제할 수 있으며, 작성자 이름은 인증 수단이 아닙니다.

## 주요 기능

| 기능 | 구현 내용 |
| --- | --- |
| 게시글 | 등록·조회·수정·삭제, 최신 글 우선 정렬 |
| 검색 · 페이징 | 제목·내용·작성자 조합 검색, 검색 조건 유지, 페이지 범위 보정 |
| 댓글 | REST API와 Ajax로 등록·수정·삭제, 댓글 수 반영, 댓글 페이징 |
| 첨부파일 | 여러 파일 업로드, 이미지 섬네일·원본 보기, 파일 다운로드 |
| 파일 관리 | 업로드 취소, 수정 시 제외한 파일 제거, 저장하지 않은 파일의 예약 정리 |
| 실행 환경 | 기본 파일 H2 자동 초기화, 환경변수로 외부 Oracle 연결 |
| 입력 · 요청 보호 | 서버 입력 검증, 출력 이스케이프, CSRF 토큰, 보안 응답 헤더 |

첨부파일은 게시글당 최대 10개, 파일당 5 MiB 미만입니다. PNG/JPG/JPEG/GIF 이미지와 PDF/TXT/DOCX/XLSX/PPTX 문서를 지원합니다. 이미지는 검증 후 첫 프레임을 PNG로 재인코딩하여 저장하므로 GIF 애니메이션은 정지 이미지가 됩니다.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| 언어 · 서버 | Java 17, Apache Tomcat 11, Jakarta Servlet 6.1 |
| 프레임워크 | Spring MVC 7, Spring Transaction, AOP, Scheduling |
| 데이터 접근 | MyBatis 3.5 / MyBatis-Spring 4.1, HikariCP |
| 데이터베이스 | H2 파일 DB 기본, Oracle 외부 연결 옵션 |
| 화면 | JSP, Jakarta JSTL, jQuery 4, CSS |
| JSON · 로그 | Jackson 3, SLF4J, Logback |
| 빌드 · 테스트 | Maven, JUnit 4, Spring MockMvc, 실제 WAR/H2 통합 테스트 |

Spring 버전과 Jakarta 전환 기준은 [Spring 공식 지원 안내](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions), [MyBatis-Spring 요구 환경](https://mybatis.org/spring/), [Tomcat 버전 안내](https://tomcat.apache.org/whichversion.html)를 참고했습니다.

## 빠른 실행

프로젝트 폴더에서 실행합니다. Windows 스크립트는 Java 17 이상이 없으면 공식 Temurin JDK를 프로젝트 내부에 준비하고, 첫 빌드 시 Maven과 함께 체크섬을 확인합니다. 최초 준비에는 인터넷 연결이 필요합니다.

```powershell
.\build.cmd
.\run-local.cmd
```

브라우저에서 [http://localhost:8080/spring-board/](http://localhost:8080/spring-board/)를 엽니다.

- H2 데이터는 `data/spring-board.mv.db`, 첨부파일은 `data/uploads/`에 저장됩니다.
- `Ctrl+C`로 서버를 종료하며, 다시 실행해도 작성한 데이터가 유지됩니다.
- 다른 포트: `powershell -ExecutionPolicy Bypass -File .\run-local.ps1 -Port 8081`
- Maven을 직접 사용하는 환경: `mvn clean verify` 후 `target/spring-board.war`를 Tomcat 11에 배포합니다.

JDK 설정, Eclipse 가져오기, Oracle 연결은 [실행 가이드](docs/SETUP.md)에 정리했습니다.

## 구현에서 다룬 문제

**댓글 수와 동시 요청**

댓글 변경과 게시글의 댓글 수 갱신을 한 트랜잭션으로 처리합니다. 게시글 행을 먼저 잠그는 순서를 통일해, 같은 댓글을 동시에 삭제하더라도 실제 삭제된 경우에만 댓글 수가 줄어들도록 했습니다.

**첨부파일 저장과 DB 반영 순서**

업로드는 서버가 발급한 파일 정보를 세션에 기록합니다. 게시글 저장 시 이 정보와 기존 첨부 정보를 검증하고, DB 커밋 후 제외한 파일을 삭제합니다. 수정 화면에서는 기존 파일 목록이 로딩되기 전 저장을 막고, 첨부 매개변수가 생략된 요청에서는 기존 첨부를 보존합니다.

**업로드와 예약 정리의 충돌**

파일 등록·삭제·정리 작업은 같은 저장소 잠금을 사용합니다. 매일 02:00에 과거 날짜 폴더 전체를 살펴보고, 수정 시각이 48시간을 넘긴 UUID 관리 파일 중 DB에 연결되지 않은 파일만 삭제합니다. 오늘·어제 폴더와 저장소 밖 경로는 정리 대상에서 제외합니다. 이 잠금은 단일 애플리케이션 인스턴스를 대상으로 합니다.

**사용자 입력과 파일 경로**

JSP와 Ajax 출력은 이스케이프하고, 변경 요청은 세션 CSRF 토큰을 확인합니다. 저장소 밖 경로와 심볼릭 링크를 거절하며, 파일 형식과 크기를 서버에서 검사합니다. 이미지 보기 응답은 PNG로 한정하고 일반 문서는 다운로드로 제공합니다.

## 구조

```text
src/main/java/io/github/limbj/board/
  controller/       화면·JSON·업로드 요청
  service/          트랜잭션과 데이터 변경
  mapper/           MyBatis 인터페이스
  domain/           게시글·댓글·첨부·검색 조건
  upload/, task/    파일 저장소와 예약 정리
  configuration/    DB 구성과 타입 변환
  security/         CSRF·응답 헤더 필터
src/main/resources/ MyBatis SQL, H2 스키마, 로그·환경 기본값
src/main/webapp/    JSP와 CSS·JavaScript
src/test/           단위·서비스·실제 HTTP 통합 테스트
docs/               실행 가이드와 화면 이미지
```

요청 흐름과 주요 API는 [설계 문서](docs/ARCHITECTURE.md)에 정리했습니다.

## 검증

```bash
mvn test          # 입력·컨트롤러·파일·보안 단위 테스트
mvn verify        # 실제 H2 트랜잭션 및 WAR HTTP 통합 테스트까지 실행
```

2026-10-05 로컬 검증: 전체 87개 중 85개 성공, 실패 0개. Windows 심볼릭 링크 생성 권한이 필요한 2개는 해당 환경에서 생략했습니다. GitHub Actions에는 Java 17에서 `mvn clean verify`를 실행하는 워크플로를 포함했습니다.

통합 테스트는 임시 DB와 임시 업로드 폴더를 사용합니다. 한국어 게시글과 댓글 CRUD, 검색·정렬·페이징, 파일 저장·다운로드·삭제, 동시 댓글 변경, CSRF와 업로드 경계를 확인합니다.

Oracle 연결 옵션과 DDL은 제공하지만 실제 Oracle 서버 연결 검증은 수행하지 않았습니다. Oracle 사용 시 별도 검증이 필요합니다.

## 참고

『코드로 배우는 스프링 웹 프로젝트』의 게시판 예제를 바탕으로 시작한 학습 프로젝트입니다. 기존 MVC 구조를 바탕으로 실행 환경, 화면, 입력 검증, 파일 관리, 동시 요청 처리와 테스트를 정리했습니다.

포트폴리오와 로컬 학습을 위한 공개 게시판이며, 회원 인증·작성자 권한 확인은 구현하지 않았습니다. 포함된 외부 라이브러리 안내는 [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md)를 참고하세요.
