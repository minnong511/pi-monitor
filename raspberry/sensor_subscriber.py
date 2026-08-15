import json

from websocket import create_connection


WS_URL = "ws://localhost:8080/ws"


def extract_body(frame: str) -> str:
    if "\n\n" not in frame:
        return ""

    return frame.split("\n\n", 1)[1].rstrip("\x00")


ws = create_connection(
    WS_URL,
    timeout=5,
    origin="http://localhost:8080"
)

try:
    connect_frame = (
        "CONNECT\n"
        "accept-version:1.2\n"
        "host:localhost\n"
        "\n"
        "\x00"
    )

    ws.send(connect_frame)

    response = ws.recv()

    if not response.startswith("CONNECTED"):
        raise RuntimeError(f"STOMP 연결 실패:\n{response}")

    print("WebSocket 연결 성공")
    print("/topic/sensors 구독 시작")

    subscribe_frame = (
        "SUBSCRIBE\n"
        "id:sensor-terminal\n"
        "destination:/topic/sensors\n"
        "ack:auto\n"
        "\n"
        "\x00"
    )

    ws.send(subscribe_frame)

    # 센서 데이터가 올 때까지 시간 제한 없이 대기합니다.
    ws.settimeout(None)

    while True:
        frame = ws.recv()

        if not frame or not frame.startswith("MESSAGE"):
            continue

        body = extract_body(frame)

        try:
            sensor_data = json.loads(body)

            print("\n센서 데이터 수신")
            print(json.dumps(
                sensor_data,
                indent=2,
                ensure_ascii=False
            ))

        except json.JSONDecodeError:
            print(f"\n메시지 수신: {body}")

except KeyboardInterrupt:
    print("\n구독을 종료합니다.")

finally:
    ws.close()
