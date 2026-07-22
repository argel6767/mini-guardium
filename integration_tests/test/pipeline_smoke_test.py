import json
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone


INGESTION_API = "http://localhost:18080"
EVALUATION_API = "http://localhost:18081"
POLL_TIMEOUT_SECONDS = 60
POLL_INTERVAL_SECONDS = 1
SOURCE_IP = "198.51.100.42"


def request_json(url: str, method: str = "GET", body: dict[str, object] | None = None) -> tuple[int, dict]:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})

    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        response_body = error.read().decode("utf-8", errors="replace")
        raise AssertionError(f"{method} {url} returned HTTP {error.code}: {response_body}") from error


def wait_for_ingestion(ingestion_id: int) -> None:
    deadline = time.monotonic() + POLL_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        status_code, response = request_json(f"{INGESTION_API}/events/{ingestion_id}")
        assert status_code == 200
        status = response["status"]
        if status == "PROCESSED":
            print(f"Ingestion event {ingestion_id} reached PROCESSED")
            return
        if status == "FAILED":
            raise AssertionError(f"Ingestion event {ingestion_id} reached FAILED")
        time.sleep(POLL_INTERVAL_SECONDS)
    raise AssertionError(f"Ingestion event {ingestion_id} did not reach PROCESSED within the timeout")


def wait_for_alert(created_from: str) -> None:
    query = urllib.parse.urlencode({
        "username": "alice",
        "tableName": "customer_accounts",
        "createdFrom": created_from,
        "size": 25,
    })
    deadline = time.monotonic() + POLL_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        status_code, response = request_json(f"{EVALUATION_API}/alerts?{query}")
        assert status_code == 200
        matching_alerts = [
            alert for alert in response["content"]
            if alert["accessEvent"]["sourceIp"] == SOURCE_IP
            and alert["accessEvent"]["queryType"] == "DELETE"
        ]
        if matching_alerts:
            alert = matching_alerts[0]
            assert alert["ruleName"] == "ACCESS_EVENT_RISK"
            assert alert["severity"] in {"HIGH", "CRITICAL"}
            print(f"Evaluation created {alert['severity']} alert {alert['id']}")
            return
        time.sleep(POLL_INTERVAL_SECONDS)
    raise AssertionError("Evaluation service did not expose the expected alert within the timeout")


def main() -> None:
    created_from = (datetime.now(timezone.utc) - timedelta(seconds=2)).isoformat().replace("+00:00", "Z")
    occurred_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    event = {
        "username": "alice",
        "tableName": "customer_accounts",
        "queryType": "DELETE",
        "occurredAt": occurred_at,
        "rowCount": 5_000,
        "sourceIp": SOURCE_IP,
        "queryText": "DELETE FROM customer_accounts",
    }
    status_code, response = request_json(f"{INGESTION_API}/events", method="POST", body=event)
    assert status_code == 202, f"Expected HTTP 202, received {status_code}"
    assert response["status"] == "PENDING"
    ingestion_id = response["ingestionId"]
    print(f"Accepted ingestion event {ingestion_id}")
    wait_for_ingestion(ingestion_id)
    wait_for_alert(created_from)
    print("Service-to-service pipeline smoke test passed")


if __name__ == "__main__":
    main()

