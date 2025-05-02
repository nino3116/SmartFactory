from ultralytics import YOLO
import cv2 as cv
import time


# 1. 커스텀 학습된 YOLO 모델 가중치 파일 경로 (.pt)
MODEL_PATH = "wsl_seg_best.pt"

# 2. 라즈베리 파이 카메라 서버의 영상 스트림 주소 (URL)
VIDEO_STREAM_URL = "http://192.168.0.124:8000/stream.mjpg"

# 3. 탐지 임계값 (Confidence Threshold)
CONF_THRESHOLD = 0.4  # 필요에 따라 조정하세요 (예: 0.3, 0.7 등)

# 4. IOU 임계값 (NMS IoU Threshold)

# 5. 결과 시각화 창 표시 여부
SHOW_DETECTION_WINDOW = True

# model= YOLO("wsl_best.pt")

# --- YOLO 모델 로드 ---
try:
    print(f"모델 로드 중: {MODEL_PATH}")
    model = YOLO(MODEL_PATH)
    print("모델 로드 완료.")
except Exception as e:
    print(f"모델 로드 오류가 발생했습니다: {e}")
    print("모델 파일 경로를 확인하거나, 파일이 손상되지 않았는지 확인하세요.")
    exit()

# --- 영상 스트림 열기 ---
print(f"영상 스트림 연결 중: {VIDEO_STREAM_URL}")
# cv2.VideoCapture 객체를 생성하여 영상 스트림을 엽니다.
# OpenCV가 FFmpeg와 함께 빌드되어 있어야 네트워크 스트림 처리가 가능합니다.
cap = cv.VideoCapture(VIDEO_STREAM_URL)

# 스트림이 제대로 열렸는지 확인
if not cap.isOpened():
    print(f"오류: 영상 스트림을 열 수 없습니다. 다음을 확인하세요:")
    print(f"- 영상 스트림 URL: {VIDEO_STREAM_URL} 이 올바른지.")
    print("- 라즈베리 파이 카메라 서버가 실행 중인지.")
    print("- 네트워크 방화벽 설정 (포트가 열려 있는지).")
    print("- OpenCV가 FFmpeg를 지원하며 네트워크 스트림 처리가 가능한지.")
    exit()
print("영상 스트림 연결 성공.")

# --- 영상 프레임 읽고 감지 수행 ---
print("\n영상 스트림으로부터 프레임을 읽고 감지를 시작합니다.")
print(f"감지 결과를 보려면 {SHOW_DETECTION_WINDOW} 설정을 확인하세요.")
print("'q' 키를 누르면 감지를 종료합니다.")

# Optional: FPS 계산을 위한 변수 초기화
# start_time = time.time()
# frame_count = 0

while True:
    # 영상 스트림에서 프레임 하나를 읽어옵니다.
    ret, frame = cap.read()

    # 프레임을 제대로 읽지 못했다면 (예: 스트림 종료, 연결 끊김 등) 루프 종료
    if not ret:
        print(
            "영상 스트림에서 프레임을 더 이상 읽을 수 없습니다. 스트림이 종료되었거나 오류가 발생했습니다."
        )
        break

    # Optional: 읽은 프레임 크기 확인 (디버깅용)
    if frame is not None:
        print(f"읽은 프레임 크기: {frame.shape}")

    # 현재 프레임에 대해 객체 감지 수행
    # model.predict() 메소드는 이미지 파일 경로, OpenCV 프레임(NumPy 배열), PIL Image 등 다양한 소스를 입력받습니다.
    # source=frame으로 읽은 OpenCV 프레임을 직접 전달합니다.
    # conf, iou 인자로 탐지 결과 필터링 임계값을 설정합니다.
    # verbose=False로 설정하면 감지 과정 상세 출력을 끕니다.
    results = model.predict(
        source=frame,
        conf=CONF_THRESHOLD,
        # show=True # Ultralytics 자체 시각화 (여기서는 cv2.imshow 사용을 위해 끕니다)
        verbose=False,
    )

    # 감지 결과 프레임 가져오기
    # model.predict(source=frame)은 해당 프레임에 대한 결과 객체의 리스트를 반환합니다.
    # 결과가 하나뿐이므로 results[0]을 사용합니다.
    # .plot() 메소드는 탐지 결과 (바운딩 박스, 라벨 등)가 그려진 NumPy 배열 형태의 이미지를 반환합니다.
    annotated_frame = results[0].plot()  # <-- results[0] 객체에 .plot() 메소드 호출

    # 결과 화면 표시 (설정된 경우)
    if SHOW_DETECTION_WINDOW:
        # cv2.imshow() 함수를 사용하여 시각화된 프레임을 창에 표시합니다.
        cv.imshow("YOLO Object Detection from RPi Stream", annotated_frame)

    # Optional: FPS 계산 및 출력
    # frame_count += 1
    # if (time.time() - start_time) > 1: # 1초마다 FPS 계산
    #    fps = frame_count / (time.time() - start_time)
    #    print(f"처리 FPS: {fps:.2f}")
    #    frame_count = 0
    #    start_time = time.time()

    # 키 입력을 대기하고 'q' 키가 눌리면 루프 종료
    # cv2.waitKey(1)은 1ms 동안 키 입력을 대기합니다.
    if cv.waitKey(1) & 0xFF == ord("q"):
        break

# --- 자원 해제 ---
cap.release()  # cv2.VideoCapture 객체 해제 (스트림 연결 종료)
if SHOW_DETECTION_WINDOW:
    cv.destroyAllWindows()  # 생성된 모든 OpenCV 창 닫기

print("\n감지 및 영상 스트림 처리가 종료되었습니다.")
