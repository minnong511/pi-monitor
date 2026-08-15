import json
import logging
import os
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

import psutil


API_URL = os.environ.get(
    "PI_MONITOR_API_URL",
    "http://localhost:8080/api/sensors",
)

SEND_INTERVAL_SECONDS = float(
    os.environ.get("PI_MONITOR_SEND_INTERVAL_SECONDS", "5")
)

REQUEST_TIMEOUT_SECONDS = float(
    os.environ.get("PI_MONITOR_REQUEST_TIMEOUT_SECONDS", "5")
)

TEMPERATURE_FILE = Path(
    os.environ.get(
        "PI_MONITOR_TEMPERATURE_FILE",
        "/sys/class/thermal/thermal_zone0/temp",
    )
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)


def read_temperature() -> float:
    raw_temperature = TEMPERATURE_FILE.read_text().strip()

    return round(
        int(raw_temperature) / 1000,
        1,
    )


def collect_sensor_data() -> dict:
    return {
        "temperature": read_temperature(),
        "cpuUsage": round(
            psutil.cpu_percent(interval=1),
            1,
        ),
        "memoryUsage": round(
            psutil.virtual_memory().percent,
            1,
        ),
    }


def send_sensor_data(sensor_data: dict) -> dict:
    body = json.dumps(sensor_data).encode("utf-8")

    request = Request(
        API_URL,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
        },
    )

    with urlopen(
        request,
        timeout=REQUEST_TIMEOUT_SECONDS,
    ) as response:
        return json.load(response)


def main() -> None:
    if SEND_INTERVAL_SECONDS <= 0:
        raise ValueError(
            "PI_MONITOR_SEND_INTERVAL_SECONDS must be greater than 0"
        )

    logging.info("센서 수집기 시작: %s", API_URL)

    while True:
        try:
            sensor_data = collect_sensor_data()
            saved_data = send_sensor_data(sensor_data)

            logging.info(
                "전송 성공: %s",
                json.dumps(
                    saved_data,
                    ensure_ascii=False,
                ),
            )

        except HTTPError as error:
            logging.error(
                "HTTP 오류: %s %s",
                error.code,
                error.reason,
            )

        except (URLError, TimeoutError) as error:
            logging.error(
                "서버 연결 오류: %s",
                error,
            )

        except OSError:
            logging.exception(
                "센서 데이터 수집 오류"
            )

        except Exception:
            logging.exception(
                "예상하지 못한 오류"
            )

        time.sleep(SEND_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()
