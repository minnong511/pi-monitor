
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