# 필요한 라이브러리 임포트
from ultralytics import YOLO
import cv2 as cv
import time
import numpy as np
from PIL import ImageFont, ImageDraw, Image  # Pillow 모듈 임포트
import torch  # Ultralytics 결과(텐서) 처리에 필요
import paho.mqtt.client as mqtt  # MQTT 클라이언트 라이브러리 임포트
import json  # 불량 정보를 JSON 형태로 보내기 위해 임포트
import requests  # HTTP 요청을 보내기 위해 임포트
import os  # 파일 경로 처리를 위해 임포트
from datetime import datetime  # 스냅샷 파일명 생성을 위해 임포트
import threading  # API 호출을 비동기적으로 처리하기 위해 스레딩 모듈 임포트
import http.server  # MJPEG 스트리밍을 위한 HTTP 서버 모듈 임포트
import socketserver  # HTTP 서버 소켓 처리를 위한 모듈 임포트
import io  # 이미지 데이터를 메모리에서 처리하기 위한 모듈 임포트
import base64  # 이미지 데이터를 base64로 인코딩하기 위한 모듈 임포트 (필요시)

# --- 설정 변수 ---

# 1. 커스텀 학습된 YOLO 모델 가중치 파일 경로 (.pt)
MODEL_PATH = "wsl_seg_best.pt"

# 2. 라즈베리 파이 카메라 서버의 영상 스트림 주소 (URL)
# 로컬 웹캠을 사용하려면 0으로 설정하세요.
VIDEO_STREAM_URL = "http://192.168.0.124:8000/stream.mjpg"

# 3. 탐지 임계값 (Confidence Threshold) - 모델이 객체를 얼마나 확신할 때 감지할지 결정
CONF_THRESHOLD = 0.5  # 필요에 따라 조정하세요 (예: 0.3, 0.7 등)

# 4. IOU 임계값 (NMS IoU Threshold) - 겹치는 바운딩 박스 중 하나만 남길 기준
IOU_THRESHOLD = 0.5  # 필요에 따라 조정하세요 (예: 0.4, 0.6 등)

# 5. 결과 시각화 창 표시 여부
SHOW_DETECTION_WINDOW = True

# 6. OpenCV 텍스트 그리기 설정 (영문)
OPENCV_FONT = cv.FONT_HERSHEY_SIMPLEX  # 사용할 폰트 종류
OPENCV_FONT_SCALE = 0.7  # 폰트 크기 스케일
OPENCV_FONT_THICKNESS = 2  # 폰트 두께

# 7. 'Black Dot', 'dent', 'scratch', 'unriped' 불량 판별을 위한 사과 면적 대비 임계값 설정 (사과 면적의 백분율)
# 해당 불량 영역이 전체 사과 면적에서 차지하는 비율에 따라 분류합니다.
# 10% 미만: 정상 (이 목록에서는 보고하지 않음)
# 10% ~ 15%: 비상품
# 15% 초과: 불량
AREA_THRESHOLD_SUBSTANDARD_PERCENT = 10.0  # 비상품 판정 시작 임계값
AREA_THRESHOLD_DEFECTIVE_PERCENT = 15.0  # 불량 판정 시작 임계값

# 8. 감지(Inference)를 수행할 시간 간격 (초 단위)
# 이 시간 간격마다 새로운 프레임에 대해 객체 감지를 수행합니다.
INFERENCE_INTERVAL_SECONDS = 7  # 5초에서 10초 사이 값으로 설정 (예: 7초)

# 9. 불량 감지 시 스냅샷을 저장할 디렉토리 경로
SNAPSHOT_SAVE_DIR = (
    "snapshots/defects"  # 현재 스크립트 파일이 있는 위치에 'snapshots' 폴더 생성
)

# --- MQTT 설정 변수 ---
MQTT_BROKER_HOST = "localhost"  # MQTT 브로커 주소 (라즈베리 파이 또는 다른 서버 주소)
MQTT_BROKER_PORT = 1883  # MQTT 브로커 포트 (일반적으로 1883)
MQTT_TOPIC_STATUS = "defect_detection/status"  # 불량 감지 상태를 보낼 토픽 (Normal, Defect Detected) - 사용자 설정 반영 (이름 변경)
MQTT_TOPIC_DETAILS = "defect_detection/details"  # 불량 상세 정보를 보낼 토픽 (JSON)

# --- API 서버 설정 변수 ---
# API 서버의 주소와 포트, 엔드포인트
# API_SERVER_URL = "http://192.168.0.122:80/api/defect"
SPRING_BOOT_API_BASE_URL = "http://192.168.0.122:80"  # Spring Boot 서버 주소 및 포트
# 불량 정보 수신 엔드포인트 (Spring Boot DefectController의 @PostMapping("/api/defect"))
API_DEFECT_ENDPOINT = f"{SPRING_BOOT_API_BASE_URL}/api/defect"
# 스냅샷 이미지 제공 엔드포인트 (Spring Boot SnapshotController의 @GetMapping("/api/snapshots/{filename}"))
API_SNAPSHOT_BASE_URL = f"{SPRING_BOOT_API_BASE_URL}/api/snapshots"

# --- MJPEG 스트리밍 서버 설정 변수 ---
STREAM_HOST = "0.0.0.0"  # 스트리밍 서버 호스트 (모든 인터페이스에서 접근 허용)
STREAM_PORT = 8080  # 스트리밍 서버 포트

# --- 전역 변수 및 스레드 동기화 ---
# 처리된 최신 프레임을 저장할 전역 변수
latest_frame = None
# 전역 변수 접근 시 사용될 스레드 잠금 (Lock)
frame_lock = threading.Lock()

# --- 함수 정의 ---


# YOLO 모델 로드 함수
def load_yolo_model(model_path):
    """
    지정된 경로에서 YOLO 모델 가중치 파일을 로드합니다.

    Args:
        model_path (str): YOLO 모델 가중치 파일 (.pt) 경로.

    Returns:
        YOLO: 로드된 YOLO 모델 객체, 로드 실패 시 None 반환.
    """
    print(f"모델 로드 중: {model_path}")
    try:
        # 모델 로드 시 GPU 사용 가능 여부 확인 및 설정
        device = "cuda" if torch.cuda.is_available() else "cpu"
        print(f"사용 장치: {device}")
        model = YOLO(model_path).to(device)  # 모델을 지정된 장치로 이동
        print("모델 로드 완료.")
        return model
    except Exception as e:
        print(f"모델 로드 오류가 발생했습니다: {e}")
        print("모델 파일 경로를 확인하거나, 파일이 손상되지 않았는지 확인하세요.")
        return None


# 영상 스트림 열기 함수
def open_video_stream(stream_url):
    """
    지정된 URL 또는 로컬 카메라의 영상 스트림을 엽니다.

    Args:
        stream_url (str 또는 int): 영상 스트림 URL (예: 'http://.../stream.mjpg') 또는 로컬 카메라 인덱스 (예: 0).

    Returns:
        cv2.VideoCapture: 열린 VideoCapture 객체, 열기 실패 시 None 반환.
    """
    print(f"영상 스트림 연결 중: {stream_url}")
    # VideoCapture 초기화 시 cap_api를 명시적으로 설정하여 특정 백엔드 사용 가능
    # 예: cv.VideoCapture(stream_url, cv.CAP_FFMPEG)
    cap = cv.VideoCapture(stream_url)

    if not cap.isOpened():
        print(f"오류: 영상 스트림을 열 수 없습니다. 다음을 확인하세요:")
        print(f"- 영상 스트림 URL 또는 카메라 인덱스: {stream_url} 이 올바른지.")
        if isinstance(stream_url, str):  # URL인 경우 추가 안내
            print("- 라즈베리 파이 카메라 서버가 실행 중인지.")
            print("- 네트워크 방화벽 설정 (포트가 열려 있는지).")
            print("- OpenCV가 FFmpeg를 지원하며 네트워크 스트림 처리가 가능한지.")
        print("프로그램을 종료합니다.")
        return None
    print("영상 스트림 연결 성공.")
    return cap


# 객체 감지 및 세그멘테이션 추론 수행 함수
def perform_inference(model, frame, conf_threshold, iou_threshold):
    """
    주어진 프레임에 대해 YOLO 모델 추론을 수행합니다.

    Args:
        model (YOLO): 로드된 YOLO 모델 객체.
        frame (numpy.ndarray): 추론을 수행할 이미지 프레임.
        conf_threshold (float): 신뢰도 임계값.
        iou_threshold (float): NMS IOU 임계값.

    Returns:
        Results: YOLO 추론 결과 객체.
    """
    # 모델 추론 시 GPU 사용 설정 (모델 로드 시 이미 설정되었지만, predict 호출 시에도 명시 가능)
    # device = 'cuda' if torch.cuda.is_available() else 'cpu'
    results = model.predict(
        source=frame,
        conf=conf_threshold,
        iou=iou_threshold,
        verbose=False,  # 추론 과정 상세 출력 끔
        # device=device # predict 호출 시 장치 명시 (필요시 주석 해제)
    )
    return results


# # 감지 결과를 분석하여 불량 판별 함수 (개선된 로직)
def analyze_defects(
    results,
    model_names,
    area_threshold_substandard_percent,
    area_threshold_defective_percent,
):
    """
    YOLO 감지 결과를 분석하여 불량을 판별하고 DefectInfo DTO 구조에 맞는 딕셔너리 리스트를 생성합니다.

    Args:
        results (Results): YOLO 추론 결과 객체.
        model_names (dict): 클래스 인덱스와 이름 매핑 딕셔너리.
        area_threshold_substandard_percent (float): 사과 면적 대비 비상품 판정 백분율 임계값 (>= 이 값).
        area_threshold_defective_percent (float): 사과 면적 대비 불량 판정 백분율 임계값 (>= 이 값).

    Returns:
        list: 감지된 불량 정보를 DefectInfo DTO 구조에 맞는 딕셔너리로 담은 리스트 (정상 판정된 객체는 포함되지 않음).
    """
    detected_defects = []
    all_detected_instances = []

    # 감지 결과가 있는지 확인하고, 있다면 모든 인스턴스 정보 저장
    if results and results[0].boxes and results[0].masks:
        boxes = results[0].boxes
        masks = results[0].masks
        names = model_names

        # 클래스별 인덱스 찾기
        try:
            name_to_id = {v: k for k, v in names.items()}
            apple_class_id = name_to_id.get("Apple", -1)
            bruise_class_id = name_to_id.get("Bruise", -1)
            rotten_class_id = name_to_id.get("rotten", -1)
            stem_class_id = name_to_id.get("stem", -1)
            black_dot_class_id = name_to_id.get("Black Dot", -1)
            dent_class_id = name_to_id.get("dent", -1)
            scratch_class_id = name_to_id.get("scratch", -1)
            unriped_class_id = name_to_id.get("unriped", -1)

            # 필요한 클래스 ID가 유효한지 확인
            required_classes = [
                "Apple",
                "Bruise",
                "rotten",
                "stem",
                "Black Dot",
                "dent",
                "scratch",
                "unriped",
            ]
            missing_classes = [
                cls for cls in required_classes if name_to_id.get(cls, -1) == -1
            ]
            if missing_classes:
                print(
                    f"경고: 모델 클래스 이름에 다음 클래스가 없습니다: {missing_classes}. 해당 클래스에 대한 판정이 정확하지 않을 수 있습니다."
                )
                print(f"모델 클래스 목록: {names}")

        except Exception as e:
            print(f"오류: 모델 클래스 이름 처리 중 예상치 못한 오류 발생: {e}")
            print(f"모델 클래스 목록: {names}")
            # 오류 발생 시 모든 ID를 유효하지 않게 설정하여 해당 판정 로직 건너뛰기
            apple_class_id = bruise_class_id = rotten_class_id = stem_class_id = (
                black_dot_class_id
            ) = dent_class_id = scratch_class_id = unriped_class_id = -1

        # 모든 감지된 인스턴스 정보를 저장 (마스크 텐서 포함)
        for i in range(len(boxes)):
            class_id = int(boxes.cls[i])
            confidence = float(boxes.conf[i])
            class_name = names.get(class_id, f"Unknown Class {class_id}")
            box_coords = boxes.xyxy[i].tolist()
            # 마스크 텐서를 CPU로 이동하여 저장 (GPU 메모리 사용량 고려)
            mask_tensor = masks.data[i].cpu()

            all_detected_instances.append(
                {
                    "class_id": class_id,
                    "class_name": class_name,
                    "confidence": confidence,
                    "box": box_coords,
                    "mask": mask_tensor,
                    "area": mask_tensor.sum().item(),  # 마스크 면적 미리 계산
                }
            )

        # 'Bruise', 'rotten', 'stem'는 항상 '불량'으로 판정 (Defective)
        always_defective_ids = [
            id for id in [bruise_class_id, rotten_class_id, stem_class_id] if id != -1
        ]
        for instance in all_detected_instances:
            if instance["class_id"] in always_defective_ids:
                detailed_reason = instance["class_name"]
                # 영문 상세 사유 매핑 (DefectInfo DTO 필드명과 일치)
                if instance["class_id"] == bruise_class_id:
                    detailed_reason = "Bruise"
                elif instance["class_id"] == rotten_class_id:
                    detailed_reason = "Rotten"
                elif instance["class_id"] == stem_class_id:
                    detailed_reason = (
                        "Stem"  # Stem classified as defective? Confirm if needed
                    )

                # DefectInfo DTO 구조에 맞게 딕셔너리 생성
                detected_defects.append(
                    {
                        "clazz": instance[
                            "class_name"
                        ],  # class 대신 clazz 사용 (DefectInfo DTO 필드명)
                        "confidence": instance["confidence"],
                        "reason": "Defective",  # Always classified as Defective
                        "detailedReason": detailed_reason,  # Detailed reason in English (DefectInfo DTO 필드명)
                        "box": instance["box"],
                        "areaPercentOnApple": None,  # Area percentage not applicable for these (DefectInfo DTO 필드명)
                        "snapshotPath": None,  # API 전송 전 채워짐 (DefectInfo DTO 필드명)
                        "imageUrl": None,  # API 전송 전 채워짐 (DefectInfo DTO 필드명)
                    }
                )

        # 'Apple' 객체와 면적 기반 불량 객체 필터링
        apple_instances = [
            inst
            for inst in all_detected_instances
            if inst["class_id"] == apple_class_id
        ]
        area_based_defect_ids = [
            id
            for id in [
                black_dot_class_id,
                dent_class_id,
                scratch_class_id,
                unriped_class_id,
            ]
            if id != -1
        ]
        area_based_defect_instances = [
            inst
            for inst in all_detected_instances
            if inst["class_id"] in area_based_defect_ids
        ]

        # 전체 Apple 면적 계산
        total_apple_area = sum(inst["area"] for inst in apple_instances)

        # 면적 기반 불량 객체에 대해 개별 판정 (전체 Apple 면적 대비)
        if total_apple_area > 0 and area_based_defect_instances:
            for defect_instance in area_based_defect_instances:
                # 현재 불량 마스크와 전체 Apple 마스크의 교집합 면적 계산
                # 모든 Apple 마스크를 합쳐서 하나의 마스크로 만든 후 교집합 계산
                # 또는 각 Apple 마스크와 개별 교집합을 계산하여 합산
                # 여기서는 모든 Apple 마스크를 OR 연산으로 합쳐서 하나의 마스크로 만든 후 교집합 계산
                total_apple_mask = None
                if apple_instances:
                    # 첫 번째 Apple 마스크로 초기화
                    total_apple_mask = apple_instances[0]["mask"].clone().int()
                    # 나머지 Apple 마스크들을 OR 연산으로 합침
                    for i in range(1, len(apple_instances)):
                        total_apple_mask = (
                            total_apple_mask | apple_instances[i]["mask"].int()
                        )

                overlapping_area_on_apples = 0
                if total_apple_mask is not None:
                    # 불량 마스크와 합쳐진 전체 Apple 마스크의 교집합 계산
                    intersection_mask = defect_instance["mask"].int() * total_apple_mask
                    overlapping_area_on_apples = intersection_mask.sum().item()

                # 전체 Apple 면적 대비 겹치는 불량 영역 비율 계산
                area_percentage_on_total_apple = (
                    (overlapping_area_on_apples / total_apple_area) * 100
                    if total_apple_area > 0
                    else 0
                )

                detailed_reason = defect_instance["class_name"]
                # 영문 상세 사유 매핑 (DefectInfo DTO 필드명과 일치)
                if defect_instance["class_id"] == black_dot_class_id:
                    detailed_reason = "Black Dot"
                elif defect_instance["class_id"] == dent_class_id:
                    detailed_reason = "Dent"
                elif defect_instance["class_id"] == scratch_class_id:
                    detailed_reason = "Scratch"
                elif defect_instance["class_id"] == unriped_class_id:
                    detailed_reason = "Unriped"

                classification = "Normal"  # Default classification
                if area_percentage_on_total_apple >= area_threshold_defective_percent:
                    classification = "Defective"
                elif (
                    area_percentage_on_total_apple >= area_threshold_substandard_percent
                ):
                    classification = "Substandard"
                # else: classification remains "Normal"

                # Add to list only if classified as Defective or Substandard
                if classification in ["Defective", "Substandard"]:
                    # DefectInfo DTO 구조에 맞게 딕셔너리 생성
                    detected_defects.append(
                        {
                            "clazz": defect_instance[
                                "class_name"
                            ],  # class 대신 clazz 사용 (DefectInfo DTO 필드명)
                            "confidence": defect_instance["confidence"],
                            "reason": classification,  # Classification result (Defective or Substandard)
                            "detailedReason": detailed_reason,  # Detailed reason in English (DefectInfo DTO 필드명)
                            "box": defect_instance["box"],
                            "areaPercentOnApple": area_percentage_on_total_apple,  # Include area percentage (DefectInfo DTO 필드명)
                            "snapshotPath": None,  # API 전송 전 채워짐 (DefectInfo DTO 필드명)
                            "imageUrl": None,  # API 전송 전 채워짐 (DefectInfo DTO 필드명)
                        }
                    )
                    # print(f"Area-based Defect Classification: {defect_instance['class_name']} - {classification} ({area_percentage_on_total_apple:.2f}%)") # Debug print
        elif area_based_defect_instances and total_apple_area == 0:
            # If no Apple objects were detected, but area-based defect objects were
            # Area percentage calculation is not possible in this case.
            # Decide how to handle this case - currently ignores them in the detected_defects list.
            print(
                "Warning: 사과 객체 없이 면적 기반 불량 객체만 감지되었습니다. 면적 비율 판정 불가."
            )

    # Print summary of detected defects (excluding Normal)
    if detected_defects:
        print(f"\n--- 현재 프레임 불량 요약 ({len(detected_defects)}건) ---")
        for defect in detected_defects:
            # 'areaPercentOnApple' 키가 있을 경우에만 출력에 포함
            area_info = (
                f", 면적 비율: {defect['areaPercentOnApple']:.2f}%"
                if defect["areaPercentOnApple"] is not None
                else ""
            )
            print(
                f"- 클래스: {defect['clazz']}, 사유: {defect['reason']}, 상세 사유: {defect['detailedReason']}, 신뢰도: {defect['confidence']:.2f}{area_info}"
            )
        print("-------------------------------------\n")
        # time.sleep(1)  # Optional: pause after detection

    # Return the list of detected defects (excluding Normal) for API transmission and MQTT details
    return detected_defects


# 감지 및 불량 판별 결과를 이미지에 시각화 함수
def visualize_results(
    annotated_frame, detected_defects, font, font_scale, font_thickness
):
    """
    감지 및 불량 판별 결과를 이미지에 시각화합니다.

    Args:
        annotated_frame (numpy.ndarray): YOLO plot() 메소드로 기본 시각화된 이미지 프레임.
        detected_defects (list): 감지된 불량 정보를 담은 딕셔너리 리스트.
        font: OpenCV 폰트 종류 (예: cv.FONT_HERSHEY_SIMPLEX).
        font_scale (float): 폰트 크기 스케일.
        font_thickness (int): 폰트 두께.

    Returns:
        numpy.ndarray: 불량 정보가 추가로 시각화된 이미지 프레임.
    """
    # 불량 판별 결과에 따라 시각화에 추가 정보 그리기 (영문 텍스트)
    if detected_defects and annotated_frame is not None:
        for defect in detected_defects:
            # defect['box']는 [x1, y1, x2, y2] 리스트 형태입니다.
            x1, y1, x2, y2 = map(int, defect["box"])
            # 불량 사유와 신뢰도를 함께 표시 (영문)
            label_text = f"Defect: {defect['reason']} ({defect['confidence']:.2f})"
            # 면적 비율이 있는 경우 추가
            if defect.get("areaPercentOnApple") is not None:
                label_text += f" ({defect['areaPercentOnApple']:.2f}%)"

            # OpenCV를 사용하여 annotated_frame에 직접 그리기 예시 (불량 객체 박스)
            # 불량으로 판정된 객체에 빨간색 박스를 그립니다.
            box_color_bgr = (0, 0, 255)  # Default Red (Defective)
            if defect["reason"] == "Substandard":
                box_color_bgr = (0, 165, 255)  # Orange (BGR)
            cv.rectangle(annotated_frame, (x1, y1), (x2, y2), box_color_bgr, 2)

            # 텍스트 위치는 박스 좌측 상단 약간 위 (y1 - 폰트 크기)로 설정
            # 텍스트 크기를 미리 계산하여 위치 조정에 활용할 수 있습니다.
            (text_width, text_height), baseline = cv.getTextSize(
                label_text, font, font_scale, font_thickness
            )
            text_pos = (x1, y1 - 5)  # 5픽셀 여백 추가

            # 이미지가 너무 작아 텍스트 위치가 벗어나지 않도록 조정
            if text_pos[1] < text_height + 5:  # If text top is too close to top edge
                text_pos = (x1, y1 + text_height + 5)  # Draw below the box

            # 이미지 경계를 벗어나지 않도록 텍스트 위치 최종 조정
            text_pos = (max(0, text_pos[0]), max(0, text_pos[1]))
            if text_pos[0] + text_width > annotated_frame.shape[1]:
                text_pos = (annotated_frame.shape[1] - text_width, text_pos[1])

            # OpenCV의 putText 함수를 사용하여 텍스트 그리기
            cv.putText(
                annotated_frame,
                label_text,
                text_pos,
                font,
                font_scale,
                box_color_bgr,  # 텍스트 색상 (박스 색상과 동일하게)
                font_thickness,
                cv.LINE_AA,  # 안티앨리어싱 적용
            )

    return annotated_frame


# MQTT 클라이언트 연결 콜백 함수
# Callback API version 2에 맞게 properties 매개변수 추가
def on_connect(client, userdata, flags, rc, properties):  # <-- properties 매개변수 추가
    if rc == 0:
        print("MQTT 브로커 연결 성공")
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
            print("연결 거부: 잘못된 사용자 이름 또는 비밀번호")
        elif rc == 5:
            print("연결 거부: 승인되지 않음")


# MQTT 클라이언트 초기화 및 연결 함수
def initialize_mqtt_client(broker_host, broker_port):
    """
    MQTT 클라이언트를 초기화하고 브로커에 연결합니다.

    Args:
        broker_host (str): MQTT 브로커 주소.
        broker_port (int): MQTT 브로커 포트.

    Returns:
        mqtt.Client: 초기화되고 연결된 MQTT 클라이언트 객체, 연결 실패 시 None 반환.
    """
    print(f"MQTT 브로커 연결 시도: {broker_host}:{broker_port}")
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)  # API 버전 명시
    client.on_connect = on_connect  # 연결 콜백 함수 설정

    try:
        client.connect(broker_host, broker_port, 60)
        # 네트워크 루프 시작 (백그라운드 스레드에서 실행)
        client.loop_start()
        # 연결이 완료될 때까지 잠시 대기
        time.sleep(1)  # 필요에 따라 조정
        # 연결 상태 다시 확인
        if client.is_connected():
            return client
        else:
            print("MQTT 연결 시도 후 연결 상태 확인 실패.")
            return None
    except Exception as e:
        print(f"MQTT 브로커 연결 중 오류 발생: {e}")
        return None


# MQTT 메시지 발행 함수
def publish_mqtt_message(client, topic, message):
    """
    지정된 토픽으로 MQTT 메시지를 발행합니다.

    Args:
        client (mqtt.Client): 연결된 MQTT 클라이언트 객체.
        topic (str): 메시지를 발행할 토픽.
        message (str): 발행할 메시지 내용.
    """
    if client and client.is_connected():
        try:
            # 메시지를 바이트 형태로 인코딩하여 발행
            result = client.publish(
                topic, message.encode("utf-8"), qos=1
            )  # QoS 레벨 1 사용
            # 발행 결과 확인 (선택 사항)
            # result.wait_for_publish() # 메시지가 실제로 발행될 때까지 대기 (블록킹)
            # print(f"MQTT 메시지 발행 결과: {result.rc}") # rc=0이면 성공
        except Exception as e:
            print(f"MQTT 메시지 발행 중 오류 발생: {e}")
    # else:
    # print("MQTT 클라이언트가 연결되지 않았습니다. 메시지를 발행할 수 없습니다.") # 너무 자주 출력될 수 있으므로 주석 처리


# 불량 정보를 API 서버로 전송하는 함수 (스레드에서 실행될 함수)
def _send_defect_data_to_api_threaded(api_url, defect_data_list):
    """
    감지된 불량 정보를 HTTP POST 요청으로 API 서버에 전송합니다.
    이 함수는 별도의 스레드에서 실행됩니다.

    Args:
        api_url (str): 불량 정보를 수신할 API 엔드포인트 URL (예: /api/defect).
        defect_data_list (list): Spring Boot DefectInfo DTO 구조와 일치하는 딕셔너리 리스트.
    """
    # Spring Boot API는 빈 리스트를 받아서 정상 로그를 기록하므로, 빈 리스트도 전송합니다.
    # if not defect_data_list:
    #     # 보낼 불량 정보가 없으면 함수 종료 (로그 기록을 위해 이제는 항상 전송)
    #     return

    try:
        # 불량 정보를 JSON 형태로 변환
        json_data = json.dumps(defect_data_list, indent=4)  # 보기 좋게 들여쓰기 추가

        # HTTP POST 요청 전송
        # headers={'Content-Type': 'application/json'}를 명시하여 서버에 JSON 데이터임을 알립니다.
        print(
            f"API 서버로 불량 정보 전송 시도 (스레드): {api_url}"
        )  # 스레드에서 실행됨을 표시
        response = requests.post(
            api_url, data=json_data, headers={"Content-Type": "application/json"}
        )

        # 응답 확인
        if response.status_code == 200:
            print(
                f"API 요청 성공 (스레드): {api_url}, 응답: {response.text}"
            )  # 응답 본문 출력
        else:
            print(
                f"API 요청 실패 (스레드): {api_url}, 상태 코드: {response.status_code}, 응답: {response.text}"
            )

    except requests.exceptions.RequestException as e:
        print(f"API 요청 중 오류 발생 (스레드): {api_url}, 오류: {e}")
    except Exception as e:
        print(f"API 데이터 전송 중 예상치 못한 오류 발생 (스레드): {e}")


# 불량 정보를 API 서버로 비동기적으로 전송하는 함수
def send_defect_data_to_api_async(
    api_defect_endpoint, api_snapshot_base_url, detected_defects, snapshot_filepath=None
):
    """
    감지된 불량 정보를 Spring Boot API 서버에 비동기적으로 전송합니다.
    DefectInfo DTO 구조에 맞게 데이터를 구성하고 이미지 URL을 추가합니다.

    Args:
        api_defect_endpoint (str): 불량 정보를 수신할 API 엔드포인트 URL (예: /api/defect).
        api_snapshot_base_url (str): 스냅샷 이미지 제공 API의 기본 URL (예: http://<SpringIP>:<Port>/api/snapshots).
        detected_defects (list): analyze_defects 함수에서 반환된 불량 정보 딕셔너리 리스트.
        snapshot_filepath (str, optional): 불량 감지 시 저장된 스냅샷 파일의 로컬 경로.
    """
    # Spring Boot의 DefectInfo DTO 구조에 맞게 데이터 리스트 구성
    # analyze_defects 함수에서 이미 DefectInfo 구조에 맞게 딕셔너리를 구성했습니다.
    # 여기서 snapshotPath와 imageUrl 필드를 채워줍니다.
    defect_info_list = []
    image_url = None

    # 불량 감지 시에만 스냅샷 처리 및 imageUrl 생성
    if detected_defects and snapshot_filepath:
        try:
            # 로컬 파일 경로에서 파일 이름만 추출
            filename = os.path.basename(snapshot_filepath)
            # Spring Boot 이미지 제공 엔드포인트 URL 생성
            image_url = f"{api_snapshot_base_url}/{filename}"
            print(f"생성된 이미지 URL: {image_url}")
        except Exception as e:
            print(f"이미지 URL 생성 오류: {e}")
            image_url = None  # 오류 발생 시 URL 없음

    for defect in detected_defects:
        # analyze_defects에서 생성된 딕셔너리에 imageUrl과 snapshotPath 추가
        defect_copy = defect.copy()
        defect_copy["snapshotPath"] = (
            snapshot_filepath  # 로컬 경로 (DB에는 저장되지 않거나 @Transient)
        )
        defect_copy["imageUrl"] = image_url  # 웹에서 접근 가능한 URL
        defect_info_list.append(defect_copy)

    # API 호출을 처리할 새로운 스레드 생성
    # 불량 감지 여부와 상관없이 로그 기록을 위해 항상 API 호출
    api_thread = threading.Thread(
        target=_send_defect_data_to_api_threaded,
        args=(api_defect_endpoint, defect_info_list),
    )
    # 데몬 스레드로 설정하여 메인 스크립트 종료 시 함께 종료되도록 함
    api_thread.daemon = True
    # 스레드 시작
    api_thread.start()
    print("API 전송 스레드 시작됨.")


# 스냅샷 저장 함수 (로컬에 임시 저장)
def save_snapshot_local(frame, save_dir):
    """
    주어진 프레임을 지정된 로컬 디렉토리에 타임스탬프 파일명으로 저장합니다.

    Args:
        frame (numpy.ndarray): 저장할 이미지 프레임.
        save_dir (str): 스냅샷을 저장할 로컬 디렉토리 경로.

    Returns:
        str: 저장된 로컬 파일 경로 또는 저장 실패 시 None.
    """
    if frame is None:
        print("오류: 저장할 프레임이 없습니다.")
        return None

    # 저장 디렉토리가 없으면 생성
    if not os.path.exists(save_dir):
        try:
            os.makedirs(save_dir)
            print(f"로컬 스냅샷 임시 저장 디렉토리 생성: {save_dir}")
        except OSError as e:
            print(f"오류: 로컬 스냅샷 임시 저장 디렉토리 생성 실패: {e}")
            return None

    # 현재 시간을 기반으로 파일명 생성 (예: 20231027_143000.png)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = os.path.join(save_dir, f"defect_snapshot_{timestamp}.png").replace(
        "\\", "/"
    )  # 윈도우 경로 구분자 문제 방지

    try:
        # 이미지 파일 저장
        cv.imwrite(filename, frame)
        print(f"로컬 스냅샷 임시 저장 완료: {filename}")
        return filename
    except Exception as e:
        print(f"오류: 로컬 스냅샷 임시 저장 실패: {e}")
        return None


# MJPEG 스트리밍을 처리하는 HTTP 요청 핸들러
class MJPEGStreamHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        # MJPEG 스트림 응답 헤더 설정
        self.send_response(200)
        self.send_header("Content-type", "multipart/x-mixed-replace; boundary=frame")
        self.end_headers()

        while True:
            # 최신 프레임에 접근하기 위해 잠금 획득
            with frame_lock:
                frame = latest_frame  # 전역 변수에서 최신 프레임 가져오기

            if frame is not None:
                # 프레임을 JPEG 형식으로 인코딩
                ret, jpeg = cv.imencode(".jpg", frame)

                if ret:
                    # 이미지 데이터를 바이트 스트림으로 변환
                    frame_bytes = jpeg.tobytes()

                    # MJPEG 스트림 파트 헤더 전송
                    self.wfile.write(b"--frame\r\n")
                    self.wfile.write(b"Content-Type: image/jpeg\r\n")
                    self.wfile.write(
                        f"Content-Length: {len(frame_bytes)}\r\n".encode("utf-8")
                    )
                    self.wfile.write(b"\r\n")
                    # 이미지 데이터 전송
                    self.wfile.write(frame_bytes)
                    self.wfile.write(b"\r\n")

            # 다음 프레임 전송까지 잠시 대기 (스트림 속도 제어)
            # 너무 빠르게 보내면 네트워크 부하가 커질 수 있습니다.
            time.sleep(0.03)  # 약 30 FPS (1/30초)


# MJPEG 스트리밍 서버를 실행하는 함수
def run_mjpeg_stream_server(host, port):
    """
    지정된 호스트와 포트에서 MJPEG 스트리밍 서버를 실행합니다.

    Args:
        host (str): 서버 호스트 주소.
        port (int): 서버 포트.
    """
    try:
        # TCP 서버 생성 및 요청 핸들러 연결
        # Allow multiple connections by setting allow_reuse_address to True
        socketserver.TCPServer.allow_reuse_address = True
        server = socketserver.TCPServer((host, port), MJPEGStreamHandler)
        print(f"MJPEG 스트리밍 서버 시작됨: http://{host}:{port}/")
        # 서버 실행 (요청 대기)
        server.serve_forever()
    except Exception as e:
        print(f"MJPEG 스트리밍 서버 오류 발생: {e}")


# 메인 처리 루프 함수
def main_loop(
    model_path,
    stream_url,
    conf_threshold,
    iou_threshold,
    show_window,
    opencv_font,  # OpenCV font type 전달
    opencv_font_scale,  # OpenCV font scale 전달
    opencv_font_thickness,  # OpenCV font thickness 전달
    area_threshold_substandard_percent,  # 비상품 임계값 전달
    area_threshold_defective_percent,  # 불량 임계값 전달
    inference_interval_seconds,  # 감지 주기 변수 추가
    snapshot_save_dir,  # 스냅샷 저장 디렉토리 변수 추가
    mqtt_broker_host,
    mqtt_broker_port,
    mqtt_topic_status,
    mqtt_topic_details,
    spring_boot_api_base_url,  # 변수 이름 변경 반영,
    stream_host,  # 스트리밍 서버 호스트 전달
    stream_port,  # 스트리밍 서버 포트 전달
):
    """
    영상 스트림에서 프레임을 읽고 감지, 판별, 시각화 과정을 반복합니다.
    설정된 시간 간격마다 감지를 수행하고, 불량 감지 시 스냅샷을 저장하며 MQTT 신호와 API 요청을 보냅니다.
    """
    # 1. 모델 로드
    model = load_yolo_model(model_path)
    if model is None:
        return  # 모델 로드 실패 시 종료

    # 2. 영상 스트림 열기
    cap = open_video_stream(stream_url)
    if cap is None:
        return  # 스트림 열기 실패 시 종료

    # 3. MQTT 클라이언트 초기화 및 연결
    mqtt_client = initialize_mqtt_client(mqtt_broker_host, mqtt_broker_port)
    # MQTT 연결 실패 시에도 프로그램은 계속 실행되도록 합니다. (필요에 따라 종료하도록 수정 가능)
    if mqtt_client is None:
        print("MQTT 연결에 실패했습니다. 불량 감지 시 MQTT 메시지를 보내지 않습니다.")

    # MJPEG 스트리밍 서버 시작 (별도 스레드)
    stream_server_thread = threading.Thread(
        target=run_mjpeg_stream_server, args=(stream_host, stream_port)
    )
    stream_server_thread.daemon = True  # 메인 스레드 종료 시 함께 종료되도록 데몬 설정
    stream_server_thread.start()
    print(f"MJPEG 스트리밍 서버 스레드 시작됨: http://{stream_host}:{stream_port}/")

    print("\n영상 스트림으로부터 프레임을 읽고 감지를 시작합니다.")
    print(f"감지 결과를 보려면 {show_window} 설정을 확인하세요.")
    print(f"감지는 약 {inference_interval_seconds}초 간격으로 수행됩니다.")
    print(f"불량 감지 시 스냅샷은 '{snapshot_save_dir}' 디렉토리에 저장됩니다.")
    print(f"Spring Boot API 기본 URL: {spring_boot_api_base_url}")
    print("'q' 키를 누르면 감지를 종료합니다.")

    # 마지막 추론(감지) 시간을 기록하는 변수 초기화
    last_inference_time = (
        time.time() - inference_interval_seconds
    )  # 시작 시 바로 추론하도록 초기값 설정

    # Optional: FPS 계산을 위한 변수 초기화
    start_time = time.time()
    frame_count = 0

    while True:
        # 영상 스트림에서 프레임 하나를 읽어옵니다.
        ret, frame = cap.read()

        # 프레임을 제대로 읽지 못했다면 (예: 스트림 종료, 연결 끊김 등) 루프 종료
        if not ret:
            print(
                "영상 스트림에서 프레임을 더 이상 읽을 수 없습니다. 스트림이 종료되었거나 오류가 발생했습니다."
            )
            break

        # 현재 시간 확인
        current_time = time.time()

        # 설정된 감지 간격이 지났는지 확인
        if current_time - last_inference_time >= inference_interval_seconds:
            print(f"\n--- 감지 수행 (약 {inference_interval_seconds}초 경과) ---")
            # 마지막 추론 시간 업데이트
            last_inference_time = current_time

            # 4. 객체 감지 및 세그멘테이션 추론 수행
            results = perform_inference(model, frame, conf_threshold, iou_threshold)

            # 5. 감지 결과를 분석하여 불량 판별
            # analyze_defects 함수는 이제 DefectInfo DTO 구조에 맞는 딕셔너리 리스트를 반환
            detected_defects_list = analyze_defects(
                results,
                model.names,
                area_threshold_substandard_percent,
                area_threshold_defective_percent,
            )

            # 6. 감지 결과 기본 시각화 (Ultralytics plot 사용)
            # plot() 메소드로 마스크, 박스만 그리고 라벨/신뢰도는 제외하여 한글 깨짐 방지
            # results[0] 객체가 비어있을 수 있으므로 확인 후 plot 호출
            annotated_frame = frame.copy()  # 기본 프레임 복사
            if results and results[0].boxes:  # 박스 결과가 있을 때만 plot 시도
                # masks=True는 세그멘테이션 모델인 경우에만 유효
                plot_masks = (
                    hasattr(results[0], "masks") and results[0].masks is not None
                )
                annotated_frame = results[0].plot(
                    masks=plot_masks,
                    boxes=True,
                    labels=False,
                    conf=False,  # labels=False로 설정하여 기본 라벨 제거
                )

            # 7. 불량 판별 결과에 따라 시각화에 추가 정보 그리기 (OpenCV 영문 텍스트 사용)
            # visualize_results 함수는 이제 DefectInfo 딕셔너리 리스트를 받음
            annotated_frame = visualize_results(
                annotated_frame,
                detected_defects_list,
                opencv_font,
                opencv_font_scale,
                opencv_font_thickness,
            )

            # --- 불량 감지 시 MQTT 신호 및 API 요청, 스냅샷 저장 ---
            # 불량 감지 여부와 관계없이 항상 스냅샷 저장 (로그에 사용)
            print("검출 결과 처리 중...")

            # 스냅샷 저장
            snapshot_filepath = save_snapshot_local(annotated_frame, snapshot_save_dir)

            # 불량 정보를 API 서버로 비동기적으로 전송
            # 불량 감지 여부와 상관없이 로그 기록을 위해 항상 API 호출
            # API 엔드포인트와 스냅샷 기본 URL을 구성하여 전달
            api_defect_endpoint = f"{spring_boot_api_base_url}/api/defect"
            api_snapshot_base_url = f"{spring_boot_api_base_url}/api/snapshots"

            send_defect_data_to_api_async(
                api_defect_endpoint,
                api_snapshot_base_url,
                detected_defects_list,  # analyze_defects에서 반환된 DefectInfo 딕셔너리 리스트 전달
                snapshot_filepath,  # 저장된 스냅샷 파일 경로 전달
            )

            # MQTT 메시지 발행 (선택 사항: API 전송으로 대체 가능)
            # 불량 감지 상태 신호 보내기 (간단한 메시지)
            status_message = "Defect Detected" if detected_defects_list else "Normal"
            publish_mqtt_message(mqtt_client, mqtt_topic_status, status_message)
            print(f"MQTT 상태 메시지 발행: '{status_message}' to '{mqtt_topic_status}'")

            # 불량 상세 정보 보내기 (JSON 형태) - 이제 API 전송 데이터와 동일한 구조
            details_message = json.dumps(detected_defects_list, indent=4)
            publish_mqtt_message(mqtt_client, mqtt_topic_details, details_message)
            print(
                f"MQTT 상세 메시지 발행: {len(detected_defects_list)}건의 불량 정보 (API 구조) to '{mqtt_topic_details}'"
            )

        # 8. 결과 화면 표시 (설정된 경우)
        # 감지 및 시각화가 수행된 annotated_frame을 표시하거나, 감지가 없었으면 원본 frame 표시
        display_frame = (
            annotated_frame
            if "annotated_frame" in locals() and annotated_frame is not None
            else frame
        )

        # MJPEG 스트리밍 서버를 위해 최신 프레임을 전역 변수에 저장
        with frame_lock:
            latest_frame = display_frame.copy()  # 스레드 안전하게 복사하여 저장

        if show_window and display_frame is not None:
            cv.imshow("YOLO Object Detection from Stream", display_frame)

        # 키 입력을 대기하고 'q' 키가 눌리면 루프 종료
        if cv.waitKey(1) & 0xFF == ord("q"):
            break

    # --- 자원 해제 ---
    cap.release()  # cv2.VideoCapture 객체 해제 (스트림 연결 종료)
    if show_window:
        cv.destroyAllWindows()  # 생성된 모든 OpenCV 창 닫기

    # MQTT 클라이언트 연결 종료
    if mqtt_client:
        mqtt_client.loop_stop()  # 네트워크 루프 중지
        mqtt_client.disconnect()  # 브로커 연결 해제
        print("MQTT 클라이언트 연결 종료.")

    print("\n감지 및 영상 스트림 처리가 종료되었습니다.")


# --- 스크립트 실행 ---
if __name__ == "__main__":

    main_loop(
        MODEL_PATH,
        VIDEO_STREAM_URL,
        CONF_THRESHOLD,
        IOU_THRESHOLD,
        SHOW_DETECTION_WINDOW,
        OPENCV_FONT,
        OPENCV_FONT_SCALE,
        OPENCV_FONT_THICKNESS,
        AREA_THRESHOLD_SUBSTANDARD_PERCENT,
        AREA_THRESHOLD_DEFECTIVE_PERCENT,
        INFERENCE_INTERVAL_SECONDS,
        SNAPSHOT_SAVE_DIR,
        MQTT_BROKER_HOST,
        MQTT_BROKER_PORT,
        MQTT_TOPIC_STATUS,  # 상태 토픽 이름 변경 반영
        MQTT_TOPIC_DETAILS,
        SPRING_BOOT_API_BASE_URL,  # Spring Boot API 기본 URL 전달
        STREAM_HOST,
        STREAM_PORT,
    )
