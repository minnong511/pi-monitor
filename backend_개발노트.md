
# PostgreSQL 준비 

Docker 없이 Ubuntu/WSL에 PostgreSQL을 설치

sudo apt update
sudo apt install postgresql postgresql-contrib
sudo service postgresql start

DB 사용자와 데이터베이스를 생성

sudo -u postgres psql 

실행 

CREATE USER pi_monitor WITH PASSWORD 'local_password';
CREATE DATABASE pi_monitor OWNER pi_monitor;
\q

PostgreSQL에서 사용자와 데이터베이스를 각각 생성

#  
# application.properties 

Spring Boot는 spring.datasource.* 설정과 JDBC URL을 이용해 드라이버 및 DataSource를 자동 구성

개꿀 ㅋ 

1. Entity 설정
2. DTO
3. Repository 
4. Service
5. Controller

실행 및 테스트 

환경 변수 설정 

spring.datasource.password
            ↓
Spring Boot가 DB 접속에 사용할 비밀번호

${SPRING_DATASOURCE_PASSWORD}
            ↓
운영체제 환경변수에서 실제 값을 가져옴

export SPRING_DATASOURCE_PASSWORD='local_password'

-> 운영체제 환경변수에 입력하면 터미널에 환경변수 등록 

./mvnw spring-boot:run

-> 을 실행하면 Maven이 실행한 Spring Boot 애플리케이션도 그 환경변수를 전달받는다. 

결과적으로는 spring.datasource.password=local_password

가 환경변수를 전달받는다. 

application.properties와 Git 저장소에는 남지 않는다. 

# DB 연결확인

Password for user pi_monitor: 
 current_database | current_user 
------------------+--------------
 pi_monitor       | pi_monitor
(1 row)

연결 확인 

# Spring Boot 확인 

2026-08-15T14:33:55.989+09:00  INFO 116841 --- [rasp_monitor] [           main] c.r.rasp_monitor.RaspMonitorApplication  : Started RaspMonitorApplication in 2.17 seconds (process running for 2.351)

# 저장 및 조회 테스 

(base) lee@minnong511:~/pi-monitor$ curl -i -X POST http://localhost:8080/messages \
  -H "Content-Type: application/json" \
  -d '{"content":"hello postgres"}'
HTTP/1.1 201 
Content-Type: application/json
Content-Length: 35
Date: Sat, 15 Aug 2026 05:36:38 GMT

{"content":"hello postgres","id":1}(base) lee@minnong511:~/pi-monitor$ curl http://localhost:8080/messages
[{"content":"hello postgres","id":1}](base) lee@minnong511:~/pi-monitor$ 

포스트맨으로도 확인이 잘 된다.

DB 구축 끝!

# 이제는 진짜 기능 작성

다음 엔터티 생성

id
temperature
cpuUsage
memoryUsage
createdAt

# Repository 생성

JpaRepository<SensorData, Long>

Java Object
↓
Repository
↓
Database

# Step 6. Sensor REST API 구현

POST /api/sensors

{
  "temperature": 52.3,
  "cpuUsage": 18.5,
  "memoryUsage": 41.2
}

Database에 정상적으로 저장되는지 확인

# Step 7. Sensor 조회 API 구현

다음 API를 추가한다.

GET /api/sensors

그리고:

GET /api/sensors/latest

를 만든다.

이 단계까지 완료되면 기본적인 CRUD 구조를 이해할 수 있다.

# PostgreSQL 환경변수 영구 설정

모든 Bash 터미널에서 동일한 DB 설정을 사용하기 위해 프로젝트 외부에 환경변수 파일을 둔다.

```bash
mkdir -p ~/.config/pi-monitor
nano ~/.config/pi-monitor/env
```

환경변수 파일에는 실제 값 대신 아래 구조를 참고하여 작성한다.

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/pi_monitor'
export SPRING_DATASOURCE_USERNAME='pi_monitor'
export SPRING_DATASOURCE_PASSWORD='<DB_PASSWORD>'
```

환경변수 파일은 본인만 읽을 수 있도록 권한을 제한한다.

```bash
chmod 600 ~/.config/pi-monitor/env
```

`~/.bashrc` 마지막에 다음 내용을 추가한다.

```bash
if [ -f "$HOME/.config/pi-monitor/env" ]; then
    source "$HOME/.config/pi-monitor/env"
fi
```

현재 터미널에 즉시 반영한다.

```bash
source ~/.bashrc
```

비밀번호를 출력하지 않고 환경변수 설정 여부를 확인한다.

```bash
if [ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  echo "DB 환경변수 설정 완료"
else
  echo "DB 환경변수 설정 실패"
fi
```

PostgreSQL 연결을 확인한다.

```bash
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" \
psql -h localhost -U pi_monitor -d pi_monitor \
-c "SELECT current_database(), current_user;"
```

환경변수 파일은 Git 저장소 밖에 있으며 실제 비밀번호는 개발노트와 `application.properties`에 기록하지 않는다.

# Spring Boot 서버 가동 확인

`Started RaspMonitorApplication` 로그를 확인하여 Spring Boot와 PostgreSQL 연결이 정상적으로 완료된 것을 확인했다.
