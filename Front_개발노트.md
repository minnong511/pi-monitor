Pinia가 센서 상태를 관리하고, Dashboard 컴포넌트들이 그 상태를 읽는 구조

GET /api/sensors ─┐
                  ├─ Pinia Store ─ Card / Chart / Table
/topic/sensors ───┘

# 1. 백엔드 CORS 허용

Vue는 localhost:5173, Spring은 localhost:8080이므로 브라우저에서는 서로 다른 출처

따라서 websocketConfig.java의 엔드포인트 부분을 수정해야한다. 

@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOrigins(
                    "http://localhost:5173",
                    "http://127.0.0.1:5173"
            );
}

REST API용 파일을 생성

package com.raspmonitor.rasp_monitor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173"
                )
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}

변경 후 Spring 서버를 재시작 

# 2. Vue 3 프로젝트 생성 


# 정상이라면 

페이지 최초 진입 시 GET /api/sensors로 과거 20개 표시
WebSocket 연결 후 Online 표시
랜덤 데이터가 들어오면 Status Card 즉시 갱신
온도 Chart에 새 점 추가
Recent Data Table 맨 위에 새 행 추가
데이터가 20개를 넘으면 가장 오래된 데이터 제거

브라우저 개발자 도구의 Network → WS → ws → Messages에서도 STOMP의 CONNECTED, MESSAGE 프레임을 확인 가능 