# back

Pokémon GO 데이터 관리 및 코인 컬렉션 조회를 위한 Spring Boot 백엔드입니다.

## 기술 스택

- Java 21
- Spring Boot 3.3.4 (Web, Thymeleaf)
- MyBatis (MariaDB)
- Apache POI (엑셀 생성)
- Lombok

## 실행 방법

```bash
mvn spring-boot:run
```

또는 Windows에서:

```bash
back.bat
```

기본 포트는 `8090`이며, `jdbc:mariadb://localhost:3306/third` 데이터베이스가 필요합니다. 접속 정보는 `src/main/resources/application.yml`에서 설정합니다.

### 빌드

```bash
mvn clean package
```

`target/back.jar`가 생성됩니다.

### 테스트

```bash
mvn test
```

## 데이터 디렉토리

Pokédex/CalcyDex 외부 소스 파일은 `pogo.data.directory` 설정 경로(기본값 `~/Documents/정진우/data`)에 캐시됩니다. `THIRD_DATA_DIR` 환경변수로 변경할 수 있습니다.

## 주요 기능

- **coin** — 코인 컬렉션 요약 조회 API (`/api/coin`)
- **pogo.table** — `POGO_5F`/`POGO_5S`/`POGO_5T` 테이블 CRUD (`/api/pogo/{tbl}`)
- **pogo.excel** — 엑셀 리포트(5pc, 5y) 미리보기/다운로드 (`/api/pogo/5pc`, `/api/pogo/5y`)
- **pogo.pokedex / pogo.calcydex** — 외부 Pokédex/CalcyDex 데이터를 단계별로 다운로드·가공하여 DB에 적재하는 파이프라인 (`/api/pogo/dex/update`)

더 자세한 아키텍처 설명은 [CLAUDE.md](CLAUDE.md)를 참고하세요.
