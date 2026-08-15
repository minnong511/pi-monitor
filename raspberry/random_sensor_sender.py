import json
import random
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


API_URL = "http://localhost:8080/api/sensors"
SEND_INTERVAL_SECONDS = 5


def generate_sensor_data() -> dict:
    return {
        "temperature": round(random.uniform(40.0, 75.0), 1),
        "cpuUsage": round(random.uniform(0.0, 100.0), 1),
        "memoryUsage": round(random.uniform(20.0, 90.0), 1),
    }


def send_sensor_data(sensor_data: dict) -> dict:
    body = json.dumps(sensor_data).encode("utf-8")

    request = Request(
        API_URL,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json"
        }
    )

    with urlopen(request, timeout=5) as response:
        return json.load(response)


try:
    while True:
        sensor_data = generate_sensor_data()

        try:
            saved_data = send_sensor_data(sensor_data)

            print(
                "전송 및 저장 성공:",
                json.dumps(saved_data, ensure_ascii=False)
            )

        except HTTPError as error:
            print(
                f"HTTP 오류: {error.code} {error.reason}"
            )

        except URLError as error:
            print(
                f"서버 연결 오류: {error.reason}"
            )

        time.sleep(SEND_INTERVAL_SECONDS)

except KeyboardInterrupt:
    print("\n센서 데이터 전송을 종료합니다.")
