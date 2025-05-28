import paho.mqtt.client as mqtt
import subprocess
import os
import time
import sys  # sys.executable을 사용하여 현재 파이썬 인터프리터 경로 가져오기
import json  # JSON 처리를 위해 추가

# --- 설정 변수 ---
MQTT_BROKER_HOST = "192.168.0.124"  # MQTT 브로커 주소
MQTT_BROKER_PORT = 1883  # MQTT 브로커 포트
MQTT_TOPIC_COMMAND = "apple_defect/command"  # 명령을 받을 토픽 (예: "START", "STOP")
MQTT_TOPIC_STATUS = "apple_defect/controller_status"  # 컨트롤러 상태를 알릴 토픽

# 제어할 파이썬 스크립트 경로
# controller.py와 apple_defect.py가 같은 디렉토리에 있다고 가정합니다.
# 만약 다른 위치에 있다면 절대 경로를 사용하거나 경로를 조정하세요.
APPLE_DEFECT_SCRIPT_PATH = os.path.join(os.path.dirname(__file__), "apple_defect.py")

# --- 전역 변수 ---
# 실행 중인 apple_defect.py 프로세스를 저장할 변수
apple_defect_process = None
# status_msg = "Unknown"  # 컨트롤러 상태 메시지 (이제 JSON으로 대체되므로 직접 사용하지 않습니다.)

# --- MQTT 클라이언트 인스턴스 ---
mqtt_client = None  # 전역 MQTT 클라이언트 변수 초기화

# --- 함수 정의 ---


def publish_status(status_str, message_str=""):
    """
    컨트롤러의 현재 상태를 JSON 형태로 MQTT로 게시합니다.
    :param status_str: 컨트롤러의 주요 상태 (예: "Initialized", "Running", "Stopped", "Error")
    :param message_str: 상태에 대한 추가 설명 메시지
    """
    global mqtt_client
    if mqtt_client is None:
        print("Error: MQTT client is not initialized.")
        return

    status_payload = {
        "status": status_str,
        "message": message_str,
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
    }
    try:
        json_payload = json.dumps(status_payload)
        mqtt_client.publish(MQTT_TOPIC_STATUS, json_payload, qos=1, retain=False)
        print(f"Published status to '{MQTT_TOPIC_STATUS}': {json_payload}")
    except Exception as e:
        print(f"Error publishing status: {e}")


def start_apple_defect_script():
    """
    apple_defect.py 스크립트를 새 프로세스로 시작합니다.
    """
    global apple_defect_process
    if apple_defect_process is None or apple_defect_process.poll() is not None:
        print(f"Starting {APPLE_DEFECT_SCRIPT_PATH}...")
        try:
            # sys.executable을 사용하여 현재 파이썬 인터프리터로 스크립트 실행
            apple_defect_process = subprocess.Popen(
                [sys.executable, APPLE_DEFECT_SCRIPT_PATH]
            )
            print(
                f"{APPLE_DEFECT_SCRIPT_PATH} started with PID: {apple_defect_process.pid}"
            )
            publish_status(
                "Started",
                f"감지 스크립트가 PID {apple_defect_process.pid}로 시작되었습니다.",
            )
        except FileNotFoundError:
            print(
                f"Error: Python interpreter or script not found at {APPLE_DEFECT_SCRIPT_PATH}"
            )
            publish_status(
                "Error",
                f"스크립트 실행 파일을 찾을 수 없습니다: {APPLE_DEFECT_SCRIPT_PATH}",
            )
        except Exception as e:
            print(f"An error occurred while starting {APPLE_DEFECT_SCRIPT_PATH}: {e}")
            publish_status("Error", f"스크립트 시작 중 예외 발생: {e}")
    else:
        print(f"{APPLE_DEFECT_SCRIPT_PATH} is already running.")
        publish_status("Already Running", "감지 스크립트가 이미 실행 중입니다.")


def stop_apple_defect_script():
    """
    실행 중인 apple_defect.py 스크립트 프로세스를 종료합니다.
    """
    global apple_defect_process
    if apple_defect_process is not None and apple_defect_process.poll() is None:
        print(
            f"Stopping {APPLE_DEFECT_SCRIPT_PATH} (PID: {apple_defect_process.pid})..."
        )
        try:
            apple_defect_process.terminate()  # 부드러운 종료 시도
            apple_defect_process.wait(timeout=5)  # 5초 대기
            print(f"{APPLE_DEFECT_SCRIPT_PATH} stopped.")
            publish_status("Stopped", "감지 스크립트가 성공적으로 중지되었습니다.")
        except subprocess.TimeoutExpired:
            print(
                f"{APPLE_DEFECT_SCRIPT_PATH} did not terminate gracefully, killing process..."
            )
            apple_defect_process.kill()  # 강제 종료
            apple_defect_process.wait()
            print(f"{APPLE_DEFECT_SCRIPT_PATH} killed.")
            publish_status("Stopped (Forced)", "감지 스크립트가 강제 종료되었습니다.")
        except Exception as e:
            print(f"An error occurred while stopping {APPLE_DEFECT_SCRIPT_PATH}: {e}")
            publish_status("Error", f"스크립트 중지 중 예외 발생: {e}")
        finally:
            apple_defect_process = None
    else:
        print(f"{APPLE_DEFECT_SCRIPT_PATH} is not running or already stopped.")
        publish_status("Not Running", "감지 스크립트가 현재 실행 중이 아닙니다.")


# --- MQTT 콜백 함수 ---


# 브로커 연결 시 호출
def on_connect(client, userdata, flags, rc, properties):
    if rc == 0:
        print("MQTT 브로커 연결 성공")
        client.subscribe(MQTT_TOPIC_COMMAND, qos=1)
        print(f"'{MQTT_TOPIC_COMMAND}' 토픽 구독 완료.")
        publish_status("Initialized", "컨트롤러가 MQTT 브로커에 연결되었습니다.")
    else:
        print(f"MQTT 브로커 연결 실패: {rc}")
        publish_status("Error", f"MQTT 브로커 연결 실패 (코드: {rc})")


# 메시지 수신 시 호출
def on_message(client, userdata, msg):
    message_payload = msg.payload.decode("utf-8").strip().upper()
    print(f"Received message on topic '{msg.topic}': '{message_payload}'")

    if msg.topic == MQTT_TOPIC_COMMAND:
        if message_payload == "START":
            start_apple_defect_script()
        elif message_payload == "STOP":
            stop_apple_defect_script()
        elif message_payload == "STATUS_REQUEST":
            # 현재 상태를 다시 게시
            current_script_status = (
                "Running"
                if (
                    apple_defect_process is not None
                    and apple_defect_process.poll() is None
                )
                else "Not Running"
            )
            publish_status(current_script_status, "컨트롤러 상태 요청에 응답합니다.")
        else:
            print(f"Unknown command received: {message_payload}")
            publish_status("Warning", f"알 수 없는 명령 수신: {message_payload}")


# --- 메인 실행 블록 ---
if __name__ == "__main__":
    mqtt_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)  # API 버전 2로 설정
    mqtt_client.on_connect = on_connect
    mqtt_client.on_message = on_message

    try:
        mqtt_client.connect(MQTT_BROKER_HOST, MQTT_BROKER_PORT, 60)

        # MQTT 네트워크 루프를 백그라운드 스레드에서 실행
        mqtt_client.loop_start()

        print("컨트롤러 실행 중. 명령 대기...")
        print(f"'{MQTT_TOPIC_COMMAND}' 토픽으로 'START' 또는 'STOP' 메시지를 보내세요.")
        print(f"컨트롤러 상태는 '{MQTT_TOPIC_STATUS}' 토픽으로 발행됩니다.")
        print("종료하려면 Ctrl+C를 누르세요.")

        # 메인 스레드는 MQTT 루프가 백그라운드에서 실행되는 동안 대기
        # Ctrl+C 인터럽트를 처리하여 종료
        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        print("\n컨트롤러 종료 요청됨.")
    except Exception as e:
        print(f"\n컨트롤러 실행 중 오류 발생: {e}")
    finally:
        # 컨트롤러 종료 시 실행 중인 apple_defect.py 프로세스 종료 시도
        if apple_defect_process is not None and apple_defect_process.poll() is None:
            print(f"{APPLE_DEFECT_SCRIPT_PATH} 프로세스 종료 시도...")
            try:
                apple_defect_process.terminate()
                apple_defect_process.wait(timeout=5)
                print(f"{APPLE_DEFECT_SCRIPT_PATH} 프로세스 종료 완료.")
            except subprocess.TimeoutExpired:
                print(
                    f"{APPLE_DEFECT_SCRIPT_PATH} 프로세스 종료 시간 초과, 강제 종료..."
                )
                apple_defect_process.kill()
                apple_defect_process.wait()
                print(f"{APPLE_DEFECT_SCRIPT_PATH} 프로세스 강제 종료 완료.")
            except Exception as e:
                print(f"Error terminating {APPLE_DEFECT_SCRIPT_PATH}: {e}")
            finally:
                apple_defect_process = None
                publish_status(
                    "Stopped (Forced)", "컨트롤러가 종료되어 스크립트가 중지되었습니다."
                )  # 종료 시 최종 상태 게시
        else:
            publish_status(
                "Stopped",
                "컨트롤러가 종료되었습니다. 스크립트가 실행 중이지 않았습니다.",
            )  # 종료 시 최종 상태 게시

        if mqtt_client:
            mqtt_client.loop_stop()
            mqtt_client.disconnect()
            print("MQTT 연결 해제.")
        print("컨트롤러 종료.")
