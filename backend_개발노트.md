
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


# websocket 

cd ~/pi-monitor/backend
mkdir -p src/main/java/com/raspmonitor/rasp_monitor/config
nano src/main/java/com/raspmonitor/rasp_monitor/config/WebSocketConfig.java

# Websocket 설정 코드 

package com.raspmonitor.rasp_monitor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }
}

# 통신 구조 

WebSocket 연결
ws://localhost:8080/ws

클라이언트 → 서버 메시지 전송
/app/sensors

서버 → 구독자 메시지 발행
/topic/sensors

# 프론트엔드가 다른 포트라면

프론트엔드가 Vite의 http://localhost:5173에서 실행된다면 엔드포인트에 허용 출처를 추가

@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOrigins("http://localhost:5173");
}

React 개발 서버가 3000번 포트라면 다음처럼 여러 개를 지정

registry.addEndpoint("/ws")
        .setAllowedOrigins(
                "http://localhost:3000",
                "http://localhost:5173"
        );

# 테스트 최종 파일구조 


backend/src/main/java/com/raspmonitor/rasp_monitor/
├── config/
│   └── WebSocketConfig.java
├── controller/
├── domain/
├── dto/
├── repository/
└── service/

# 백엔드 최종 테스트 
Python → POST /api/sensors → DB 저장 → Spring이 /topic/sensors로 발행 → 터미널 구독기

Python 랜덤 센서
    │ POST /api/sensors
    ▼
Spring Controller
    │
    ├─ DB 저장
    │
    └─ /topic/sensors 발행
              │
              ▼
      Python 터미널 구독기

# 테스트 구조 

┌──────────────────────────┐
│ random_sensor_sender.py  │
│ 랜덤 센서 데이터 생성     │
└────────────┬─────────────┘
             │ HTTP POST /api/sensors
             │ JSON 데이터
             ▼
┌──────────────────────────┐
│ SensorDataController     │
│ 요청 수신 및 검증         │
└────────────┬─────────────┘
             │ sensorDataService.create()
             ▼
┌──────────────────────────┐
│ SensorDataService        │
│ Entity 생성, 트랜잭션 처리 │
└────────────┬─────────────┘
             │ repository.save()
             ▼
┌──────────────────────────┐
│ PostgreSQL               │
│ sensor_data 테이블 저장   │
└────────────┬─────────────┘
             │ 저장된 SensorData 반환
             ▼
┌──────────────────────────┐
│ SimpMessagingTemplate    │
│ /topic/sensors로 발행     │
└────────────┬─────────────┘
             │ STOMP MESSAGE
             ▼
┌──────────────────────────┐
│ sensor_subscriber.py     │
│ 실시간 센서 데이터 출력   │
└──────────────────────────┘

실제 프론트엔드에서는 이런 구조 사용 

1. GET /api/sensors/latest로 현재 데이터 조회
2. /ws에 WebSocket 연결
3. /topic/sensors 구독
4. 이후 새 데이터가 올 때마다 화면 갱신

REST = 데이터 입력
PostgreSQL = 데이터 보관
WebSocket = 새 데이터 실시간 전달

# 보안 

센서 POST 요청
→ API Key 또는 인증 토큰 검사
→ 정상 라즈베리파이만 저장 허용

Dashboard GET/WebSocket
→ 인증된 사용자만 조회 허용

# 라즈베리파이에서 실행할 서비스

postgresql.service
pi-monitor-backend.service
pi-monitor-sensor.service
cloudflared.service

systemctl status postgresql
systemctl status pi-monitor-backend
systemctl status pi-monitor-sensor
systemctl status cloudflared

