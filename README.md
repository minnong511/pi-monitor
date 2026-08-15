# Pi Monitor

Raspberry Pi의 시스템 정보를 수집하고 실시간으로 보여주는 모니터링 대시보드입니다.

## 디렉터리

```text
pi-monitor/
├── backend/     # Spring Boot API, WebSocket, DB 처리
├── frontend/    # Vue 실시간 대시보드
├── raspberry/   # Python 센서 수집기와 systemd 설치
├── scripts/     # Docker·Quick Tunnel 실행 스크립트
└── docker-compose.yml
```

## 주요 기능

- CPU 온도·사용률·메모리 수집 및 PostgreSQL 저장
- REST API 과거 데이터 조회 및 WebSocket 실시간 수신
- 기간별 그래프, 구간 통계, 온도 경고, 오프라인 판정
- 실시간 그래프 일시정지 및 CSV 다운로드

## 사용 기술

- Backend: Java, Spring Boot, Spring Data JPA, WebSocket/STOMP
- Frontend: Vue 3, Pinia, Axios, Chart.js
- Infra: PostgreSQL, Docker Compose, Vercel, Cloudflare Tunnel, systemd
- Collector: Python, psutil
