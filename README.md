# Pi Monitor

> [대시보드 바로가기](https://monitor-ochre-three.vercel.app)

Raspberry Pi의 CPU 온도, CPU 사용률, 메모리 사용률을 수집하고 실시간으로 보여주는 모니터링 대시보드입니다.

Vue, Spring Boot, WebSocket, Docker를 연습하려고 만들었습니다. Let's go.

## 전체 구조

```text
Raspberry Pi
  sensor_sender.py
    │ POST /api/sensors
    ▼
Spring Boot :8080
  ├─ PostgreSQL에 데이터 저장
  └─ /topic/sensors로 실시간 메시지 전송
    ▼
Vue Dashboard
  ├─ 상태 카드
  ├─ 차트
  ├─ 최근 데이터 표
  └─ CSV 다운로드

외부 접속
Vercel Vue Dashboard
  → Cloudflare Tunnel
  → Raspberry Pi Spring Boot
```

## 작동 과정

### 1. Raspberry Pi가 센서 데이터를 수집

`raspberry/sensor_sender.py`가 기본 5초 간격으로 아래 값을 읽습니다.

- CPU 온도, `/sys/class/thermal/thermal_zone0/temp` 값 사용
- CPU 사용률, `psutil.cpu_percent()` 사용
- 메모리 사용률, `psutil.virtual_memory().percent` 사용

수집한 데이터는 아래처럼 `POST /api/sensors`로 보냅니다.

```json
{
  "temperature": 54.2,
  "cpuUsage": 18.4,
  "memoryUsage": 42.7
}
```

기본 주소는 `http://localhost:8080/api/sensors`입니다. 백엔드가 다른 서버에 있다면 `PI_MONITOR_API_URL` 환경 변수로 바꿀 수 있습니다.

전송이 실패해도 수집기는 종료되지 않습니다. 오류를 로그에 남기고 다음 주기에 다시 시도합니다. 다만 실패한 시점의 데이터는 따로 쌓아 두지 않으므로 서버가 내려간 동안의 데이터는 유실됩니다.

### 2. Spring Boot가 저장하고 실시간으로 전송

백엔드는 센서 데이터를 받으면 아래 순서로 처리합니다.

1. 요청값을 확인합니다. CPU, 메모리 사용률은 0부터 100 사이여야 합니다.
2. PostgreSQL의 `sensor_data` 테이블에 저장합니다.
3. 저장에 성공한 데이터를 `/topic/sensors`로 전송합니다.
4. 저장된 데이터, `id`, `createdAt`을 HTTP `201 Created`로 응답합니다.

DB 저장이 실패하면 WebSocket 메시지도 보내지지 않습니다. 즉 화면에 들어오는 실시간 데이터는 DB에 저장된 데이터 기준입니다.

조회 API는 아래와 같습니다.

| API | 설명 |
| --- | --- |
| `POST /api/sensors` | 센서 데이터 저장, 저장 후 실시간 메시지 전송 |
| `GET /api/sensors` | 저장된 센서 데이터 전체 조회, 최신순 |
| `GET /api/sensors/latest` | 가장 최신 센서 데이터 한 건 조회 |
| `GET /api/sensors/recent?limit=20` | 최근 데이터 제한 조회, 최신순 |
| `GET /api/sensors/history?from&to&page&size` | 기간 내 원본 데이터를 시간순 페이지로 조회 |
| `GET /api/sensors/history/summary?from&to&bucketSeconds` | 기간별 차트·통계용 버킷 집계 조회 |
| `GET /api/sensors/history/export?from&to` | 기간 내 원본 데이터를 CSV로 스트리밍 다운로드 |

오래된 데이터는 매일 한국 시간 새벽 3시에 삭제합니다. 현재 보관 기간은 14일이며, `history`와 `export` API는 최대 14일 구간만 조회할 수 있습니다. `from`과 `to`는 한국 시간 기준 ISO 날짜·시간으로 전달하고, `from`은 `to`보다 이전이어야 합니다. 원본 페이지 조회의 기본 크기는 100건, 최대 크기는 1,000건입니다.

### 3. Dashboard가 과거 데이터와 실시간 데이터를 합침

대시보드를 열면 먼저 `GET /api/sensors/recent?limit=20`로 최근 표와 상태 카드를 채웁니다. 그래프는 현재 보고 있는 기간만 `history/summary`로 요청하므로, 페이지 진입 시 전체 이력을 내려받지 않습니다. 그 다음 `VITE_WS_URL`로 STOMP WebSocket 연결을 열고 `/topic/sensors`를 구독합니다.

```text
페이지 진입
  → GET /api/sensors/recent로 최근 데이터 조회
  → GET /api/sensors/history/summary로 현재 그래프 구간 조회
  → /ws WebSocket 연결
  → /topic/sensors 구독
  → 새 데이터 수신
  → Pinia Store 갱신
  → 카드, 차트, 표 자동 갱신
```

Pinia Store는 수신한 데이터의 `id`를 기록합니다. REST 조회 데이터와 WebSocket 데이터가 겹쳐도 같은 데이터를 두 번 표시하지 않습니다.

WebSocket 연결이 끊기면 5초 간격으로 재연결을 시도합니다. 연결되면 최근 데이터와 현재 그래프 구간을 다시 조회해, 연결되어 있지 않던 동안의 기록을 보완합니다.

### 4. 화면에서 보여주는 내용

상태 카드는 최신 데이터 기준으로 아래 값을 보여줍니다.

- CPU Temperature
- CPU Usage
- Memory Usage
- Online Status

`Online`은 WebSocket만 연결됐다고 바로 표시하지 않습니다. WebSocket이 연결되어 있고, 마지막 데이터가 20초 안에 들어와야 `Online`입니다. 수집기가 멈추거나 데이터 수신이 끊기면 `Offline`으로 바뀝니다.

온도가 75°C 이상이면 온도 카드와 대시보드에 경고를 표시합니다.

차트에서는 아래 기능을 사용할 수 있습니다.

- 온도, CPU 사용률, 메모리 사용률 전환
- 1시간, 6시간, 24시간, 7일, 14일 범위 선택
- 이전/다음 구간 탐색과 `현재로` 복귀
- 평균, 최솟값, 최댓값 통계
- 75°C 경고선 표시
- 과거 구간을 보는 중 새 데이터 알림
- 선택한 기간의 원본 데이터 CSV 다운로드

표는 최근 20개 데이터를 보여줍니다. 차트와 통계는 서버가 선택 기간을 시간 버킷으로 집계한 결과를 사용하고, CSV는 선택한 기간의 원본 데이터를 모두 다운로드합니다.

### 5. Docker와 Raspberry Pi 서비스

`docker-compose.yml`은 PostgreSQL과 Spring Boot 백엔드를 실행합니다.

```text
PostgreSQL Container
  └─ postgres_data Volume, 센서 데이터 유지

Spring Boot Container
  └─ postgres:5432로 DB 연결
  └─ host 8080 포트 공개
```

PostgreSQL이 `healthy` 상태가 된 뒤 백엔드가 실행됩니다. Raspberry Pi 센서 수집기는 `systemd` 서비스 `pi-monitor-sensor`로 등록합니다. 재부팅 후에도 자동 실행되고, 실행 중 오류가 나면 5초 뒤 다시 시작합니다.

```bash
# Backend, PostgreSQL 실행
./scripts/pi-compose.sh up -d --build

# 센서 수집기 상태 확인
sudo systemctl status pi-monitor-sensor

# 최신 데이터 확인
curl http://localhost:8080/api/sensors/latest
```

`docker compose down -v`는 PostgreSQL 볼륨까지 지웁니다. 데이터를 유지하려면 사용하지 않습니다.

### 6. Vercel과 Cloudflare Tunnel 외부 접속

Vue 화면은 Vercel에서 제공합니다. Vercel이 Raspberry Pi API를 대신 제공하는 구조는 아닙니다. 사용자의 브라우저가 Cloudflare Tunnel을 통해 Raspberry Pi의 백엔드에 직접 연결합니다.

```text
Browser
  → Vercel에서 Vue 파일 다운로드
  → HTTPS, WSS 요청
  → Cloudflare Tunnel
  → Raspberry Pi localhost:8080
```

Vercel 환경 변수는 아래처럼 설정합니다.

```text
VITE_API_URL=https://<quick-tunnel-host>.trycloudflare.com
VITE_WS_URL=wss://<quick-tunnel-host>.trycloudflare.com/ws
```

Quick Tunnel 주소가 바뀌면 Vercel 환경 변수도 바꾸고 다시 배포해야 합니다. `scripts/quick-tunnel.sh`는 백엔드가 정상인지 먼저 확인한 뒤 터널을 실행합니다.

외부 인터넷이나 Tunnel이 끊겨도 Raspberry Pi 내부의 수집기, 백엔드, PostgreSQL이 살아 있으면 센서 데이터 수집과 저장은 계속됩니다. 반대로 Spring Boot나 PostgreSQL이 멈추면 수집기는 재시도하지만 그 사이의 데이터는 저장되지 않습니다.

## 디렉터리

```text
pi-monitor/
├── backend/     # Spring Boot API, WebSocket, DB 처리
├── frontend/    # Vue 실시간 대시보드
├── raspberry/   # Python 센서 수집기, systemd 설치
├── scripts/     # Docker, Quick Tunnel 실행 스크립트
└── docker-compose.yml
```

## 사용 기술

- Backend: Java, Spring Boot, Spring Data JPA, WebSocket, STOMP
- Frontend: Vue 3, Pinia, Axios, Chart.js
- Infra: PostgreSQL, Docker Compose, Vercel, Cloudflare Tunnel, systemd
- Collector: Python, psutil

## 운영 문서

- [가동 가이드](가동_가이드.md)
- [라즈베리파이 센서 서비스 설정](라즈베리파이_센서서비스_설정.md)
- [배포 메모](배포.md)
