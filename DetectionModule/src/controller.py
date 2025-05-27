import paho.mqtt.client as mqtt
import subprocess
import os
import time
import sys  # sys.executable을 사용하여 현재 파이썬 인터프리터 경로 가져오기

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
status_msg = "Unknown"  # 컨트롤러 상태 메시지

# --- MQTT 콜백 함수 ---


# 브로커 연결 시 호출
def on_connect(client, userdata, flags, rc, properties):
    if rc == 0:
        global status_msg
        print("MQTT 브로커 연결 성공")
        # 연결 성공 후 명령 토픽 구독
        client.subscribe(MQTT_TOPIC_COMMAND)
        print(f"토픽 구독: {MQTT_TOPIC_COMMAND}")
        # 컨트롤러 시작 상태 알림
        status_msg = "Controller Started"
        publish_status(status_msg)
    else:
        print(f"MQTT 브로커 연결 실패: {rc}")
        # 연결 실패 코드를 기반으로 추가 디버깅 정보 제공 가능
        if rc == 1:
            print("연결 거부: 잘못된 프로토콜 버전")
        elif rc == 2:
            print("연결 거부: 잘못된 클라이언트 ID")
        elif rc == 3:
            print("연결 거부: 서버 사용 불가")
        elif rc == 4:
            print("연결 거부: 잘못된 사용자 이름 또는 비밀워드")
        elif rc == 5:
            print("연결 거부: 승인되지 않음")


# 메시지 수신 시 호출
def on_message(client, userdata, msg):
    global apple_defect_process
    global status_msg
    print(f"메시지 수신 - 토픽: {msg.topic}, 메시지: {msg.payload.decode()}")

    if msg.topic == MQTT_TOPIC_COMMAND:
        command = (
            msg.payload.decode().strip().upper()
        )  # 메시지를 문자열로 디코딩하고 대문자로 변환

        if command == "START":
            if apple_defect_process is None or apple_defect_process.poll() is not None:
                # apple_defect.py가 실행 중이 아니거나 종료된 경우
                print(f"{APPLE_DEFECT_SCRIPT_PATH} 실행 시작...")
                try:
                    # subprocess.Popen을 사용하여 새 프로세스로 스크립트 실행
                    # sys.executable을 사용하여 현재 파이썬 인터프리터로 실행
                    # stdout과 stderr를 subprocess.PIPE로 설정하여 출력을 캡처하거나 리다이렉션할 수 있습니다.
                    # 여기서는 간단히 None으로 두어 기본 동작(터미널에 출력)을 따릅니다.
                    apple_defect_process = subprocess.Popen(
                        [sys.executable, APPLE_DEFECT_SCRIPT_PATH]
                    )
                    print(
                        f"{APPLE_DEFECT_SCRIPT_PATH} 실행됨 (PID: {apple_defect_process.pid})"
                    )
                    status_msg = "Script Started"
                    publish_status(status_msg)
                except FileNotFoundError:
                    print(
                        f"오류: 스크립트 파일을 찾을 수 없습니다: {APPLE_DEFECT_SCRIPT_PATH}"
                    )
                    status_msg = "Error: Script File Not Found"
                    publish_status(status_msg)
                except Exception as e:
                    print(f"오류: 스크립트 실행 중 예외 발생: {e}")
                    status_msg = f"Error: Script Execution Failed - {e}"
                    publish_status(status_msg)
            else:
                print(
                    f"{APPLE_DEFECT_SCRIPT_PATH} 이미 실행 중입니다 (PID: {apple_defect_process.pid})."
                )
                status_msg = "Script Already Running"
                publish_status(status_msg)

        elif command == "STOP":
            if apple_defect_process is not None and apple_defect_process.poll() is None:
                # apple_defect.py가 실행 중인 경우
                print(
                    f"{APPLE_DEFECT_SCRIPT_PATH} 실행 중지 시도 (PID: {apple_defect_process.pid})..."
                )
                try:
                    # 프로세스 종료 신호 보내기 (Graceful shutdown 시도)
                    apple_defect_process.terminate()
                    # 프로세스가 종료될 때까지 잠시 대기
                    try:
                        apple_defect_process.wait(timeout=5)  # 5초 대기
                        print(f"{APPLE_DEFECT_SCRIPT_PATH} 정상적으로 종료됨.")
                        status_msg = "Script Stopped"
                        publish_status(status_msg)
                    except subprocess.TimeoutExpired:
                        # 5초 안에 종료되지 않으면 강제 종료
                        print(
                            f"{APPLE_DEFECT_SCRIPT_PATH} 종료 시간 초과, 강제 종료 시도..."
                        )
                        apple_defect_process.kill()
                        print(f"{APPLE_DEFECT_SCRIPT_PATH} 강제 종료됨.")
                        status_msg = "Script Force Stopped"
                        publish_status(status_msg)
                    finally:
                        apple_defect_process = None  # 프로세스 변수 초기화

                except ProcessLookupError:
                    print(
                        f"경고: PID {apple_defect_process.pid} 프로세스를 찾을 수 없습니다. 이미 종료되었을 수 있습니다."
                    )
                    apple_defect_process = None
                    status_msg = (
                        "Warning: Script Process Not Found (Possibly Already Stopped)"
                    )
                    publish_status(status_msg)
                except Exception as e:
                    print(f"오류: 스크립트 종료 중 예외 발생: {e}")
                    status_msg = f"Error: Script Termination Failed - {e}"
                    publish_status(status_msg)
            else:
                print(f"{APPLE_DEFECT_SCRIPT_PATH} 실행 중이 아닙니다.")
                status_msg = "Script Not Running"
                publish_status(status_msg)
        elif command == "STATUS_REQUEST":
            print("상태 요청 수신")
            publish_status(status_msg)
            print(f"컨트롤러 상태 발행 {status_msg} to {MQTT_TOPIC_STATUS}")

        else:
            print(f"알 수 없는 명령: {command}")
            publish_status(f"Unknown Command: {command}")


# 상태 메시지 발행 함수
def publish_status(status_message):
    """컨트롤러 상태를 MQTT로 발행합니다."""
    try:
        client.publish(MQTT_TOPIC_STATUS, status_message, qos=1)
        print(f"컨트롤러 상태 발행: '{status_message}' to '{MQTT_TOPIC_STATUS}'")
    except Exception as e:
        print(f"상태 메시지 발행 중 오류 발생: {e}")


# --- 메인 실행 로직 ---
if __name__ == "__main__":
    print("MQTT 컨트롤러 시작 중...")
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)  # API 버전 명시
    client.on_connect = on_connect
    client.on_message = on_message

    try:
        print(f"MQTT 브로커 연결 시도: {MQTT_BROKER_HOST}:{MQTT_BROKER_PORT}")
        client.connect(MQTT_BROKER_HOST, MQTT_BROKER_PORT, 60)

        # MQTT 네트워크 루프를 백그라운드 스레드에서 실행
        client.loop_start()

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
                print(f"{APPLE_DEFECT_SCRIPT_PATH} 프로세스 종료 시간 초과, 강제 종료.")
                apple_defect_process.kill()
            except Exception as e:
                print(f"프로세스 종료 중 오류 발생: {e}")

        # MQTT 클라이언트 연결 종료
        if "client" in locals() and client.is_connected():
            print("MQTT 클라이언트 연결 종료.")
            publish_status("Controller Stopped")  # 종료 상태 알림
            client.loop_stop()
            client.disconnect()

    print("컨트롤러 종료.")
