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
import boto3  # AWS S3 업로드를 위한 boto3 라이브러리 임포트
from botocore.exceptions import (
    NoCredentialsError,
    PartialCredentialsError,
)  # AWS 자격 증명 오류 처리를 위해 임포트

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
AREA_THRESHOLD_SUBSTANDARD_PERCENT = 15.0  # 비상품 판정 시작 임계값
AREA_THRESHOLD_DEFECTIVE_PERCENT = 30.0  # 불량 판정 시작 임계값

# 8. 감지(Inference)를 수행할 시간 간격 (초 단위)
# 이 시간 간격마다 새로운 프레임에 대해 객체 감지를 수행합니다.
INFERENCE_INTERVAL_SECONDS = 10  # 5초에서 10초 사이 값으로 설정 (예: 7초)

# 9. 불량 감지 시 스냅샷을 저장할 디렉토리 경로
# SNAPSHOT_SAVE_DIR = (
#     "snapshots/defects"  # 현재 스크립트 파일이 있는 위치에 'snapshots' 폴더 생성
# )
# 스냅샷 저장 기본 디렉토리
SNAPSHOT_BASE_DIR = "snapshots"
# 정상 이미지 저장 디렉토리 (SNAPSHOT_BASE_DIR 하위)
NORMAL_DIR = os.path.join(SNAPSHOT_BASE_DIR, "normal")
# 불량 이미지 저장 디렉토리 (SNAPSHOT_BASE_DIR 하위)
DEFECTS_DIR = os.path.join(SNAPSHOT_BASE_DIR, "defects")

# 7. 스냅샷을 임시 저장할 로컬 디렉토리 경로 (S3 업로드 전)
SNAPSHOT_TEMP_DIR = (
    "temp_snapshots"  # 현재 스크립트 파일이 있는 위치에 'temp_snapshots' 폴더 생성
)

# --- AWS S3 설정 변수 ---
# S3 버킷 이름과 리전을 여기에 설정하세요.
S3_BUCKET_NAME = "your-unique-apple-defect-bucket-name"  # <-- 실제 버킷 이름으로 변경!
AWS_REGION = "ap-northeast-2"  # <-- 실제 AWS 리전으로 변경! (예: 서울 리전)
# S3에 저장될 객체의 기본 경로 (예: snapshots/)
S3_OBJECT_BASE_PATH = "snapshots/"

# S3 객체 하위 경로 (정상/불량)
S3_NORMAL_SUBPATH = "normal/"
S3_DEFECTS_SUBPATH = "defects/"

# --- MQTT 설정 변수 ---
MQTT_BROKER_HOST = (
    "192.168.0.124"  # MQTT 브로커 주소 (라즈베리 파이 또는 다른 서버 주소)
)
MQTT_BROKER_PORT = 1883  # MQTT 브로커 포트 (일반적으로 1883)
MQTT_TOPIC_STATUS = "defect_detection/status"  # 불량 감지 상태를 보낼 토픽 (Normal, Defect Detected) - 사용자 설정 반영 (이름 변경)
MQTT_TOPIC_DETAILS = "defect_detection/details"  # 불량 상세 정보를 보낼 토픽 (JSON)

# --- API 서버 설정 변수 ---
# API 서버의 주소와 포트, 엔드포인트
API_DETECTION_RESULT_URL = "http://localhost:80/api/defect"  # Spring Boot DefectController의 /api/defect 엔드포인트
# API_SERVER_URL = "http://localhost:80/api/defect"
# SPRING_BOOT_API_BASE_URL = "http://localhost:80"  # Spring Boot 서버 주소 및 포트
# # 불량 정보 수신 엔드포인트 (Spring Boot DefectController의 @PostMapping("/api/defect"))
# API_DEFECT_ENDPOINT = f"{SPRING_BOOT_API_BASE_URL}/api/defect"
# # 스냅샷 이미지 제공 엔드포인트 (Spring Boot SnapshotController의 @GetMapping("/api/snapshots/{filename}"))
# API_SNAPSHOT_BASE_URL = f"{SPRING_BOOT_API_BASE_URL}/api/snapshots"

# --- MJPEG 스트리밍 서버 설정 변수 ---
STREAM_HOST = "localhost"  # 스트리밍 서버 호스트 (모든 인터페이스에서 접근 허용)
STREAM_PORT = 8000  # 스트리밍 서버 포트

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


# 감지 결과를 분석하여 불량 판별 함수 (개선)
def analyze_defects(
    results,
    model_names,
    area_threshold_substandard_percent,
    area_threshold_defective_percent,
):
    """
    YOLO 감지 결과를 분석하여 불량을 판별하고 분류합니다.

    Args:
        results (Results): YOLO 추론 결과 객체.
        model_names (dict): 클래스 인덱스와 이름 매핑 딕셔너리.
        area_threshold_substandard_percent (float): 사과 면적 대비 비상품 판정 백분율 임계값 (>= 이 값).
        area_threshold_defective_percent (float): 사과 면적 대비 불량 판정 백분율 임계값 (>= 이 값).

    Returns:
        list: 감지된 불량 정보를 담은 딕셔너리 리스트 (정상 판정된 객체는 포함되지 않음).
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
            class_name = names.get(
                class_id, f"Unknown Class {class_id}"
            )  # 모델에 없는 클래스 이름 처리
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

        # 'Bruise', 'rotten', 'stem'는 항상 '불량'으로 판정
        always_defective_ids = [
            id for id in [bruise_class_id, rotten_class_id, stem_class_id] if id != -1
        ]
        for instance in all_detected_instances:
            if instance["class_id"] in always_defective_ids:
                # 상세 사유 매핑 (모델 클래스 이름 또는 정의된 이름 사용)
                detailed_reason = instance["class_name"]  # 기본값은 모델 클래스 이름
                if instance["class_id"] == bruise_class_id:
                    detailed_reason = "Bruise"
                elif instance["class_id"] == rotten_class_id:
                    detailed_reason = "Rotten"
                elif instance["class_id"] == stem_class_id:
                    detailed_reason = (
                        "Stem"  # Stem classified as defective? Confirm if needed
                    )

                detected_defects.append(
                    {
                        "clazz": instance[
                            "class_name"
                        ],  # Spring Boot DTO 필드명과 일치
                        "confidence": instance["confidence"],
                        "reason": "Defective",  # Always classified as Defective
                        "detailed_reason": detailed_reason,  # Detailed reason in English
                        "box": instance["box"],
                        "areaPercentOnApple": None,  # Area percentage not applicable for these
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

                # 상세 사유 매핑 (모델 클래스 이름 또는 정의된 이름 사용)
                detailed_reason = defect_instance[
                    "class_name"
                ]  # 기본값은 모델 클래스 이름
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
                    detected_defects.append(
                        {
                            "clazz": defect_instance[
                                "class_name"
                            ],  # Spring Boot DTO 필드명과 일치
                            "confidence": defect_instance["confidence"],
                            "reason": classification,  # Classification result (Defective or Substandard)
                            "detailed_reason": detailed_reason,  # Detailed reason in English
                            "box": defect_instance["box"],
                            "areaPercentOnApple": area_percentage_on_total_apple,  # Include area percentage
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

    # 디버깅을 위해 감지된 불량 객체 리스트 출력
    # print("\n--- analyze_defects 결과 (detected_defects) ---")
    # for defect in detected_defects:
    #      print(defect)
    # print("---------------------------------------------\n")

    # Print summary of detected defects (excluding Normal)
    if detected_defects:
        print(f"\n--- 현재 프레임 불량 요약 ({len(detected_defects)} found) ---")
        for defect in detected_defects:
            area_info = (
                f", Area %: {defect.get('areaPercentOnApple', None):.2f}%"
                if defect.get("areaPercentOnApple", None) is not None
                else ""
            )  # Use .get() for safety
            print(
                f"- Class: {defect.get('clazz', 'N/A')}, Classification: {defect.get('reason', 'N/A')}, Reason: {defect.get('detailed_reason', 'N/A')}, Confidence: {defect.get('confidence', 0):.2f}{area_info}"  # Use .get() for safety
            )
        print("-------------------------------------\n")
        # time.sleep(1)  # Optional: pause after detection

    # Return the list of detected defects (excluding Normal)
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
            label_text = f"{defect['reason']}: {defect.get('detailed_reason')} ({defect['confidence']:.2f})"
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


# 감지 결과를 API 서버로 전송하는 함수 (스레드에서 실행될 함수)
# 이 함수는 이제 DetectionResultDto 구조에 맞는 단일 딕셔너리를 받습니다.
def _send_detection_result_to_api_threaded(
    api_url, detection_result_data
):  # 함수 이름 및 인자 변경
    """
    감지 결과를 HTTP POST 요청으로 API 서버에 전송합니다.
    이 함수는 별도의 스레드에서 실행됩니다.

    Args:
        api_url (str): 감지 결과를 수신할 API 엔드포인트 URL.
        detection_result_data (dict): 감지 결과를 담은 딕셔너리 (DetectionResultDto 구조).
    """
    if not detection_result_data:
        # 보낼 데이터가 없으면 함수 종료 (이 경우는 거의 발생하지 않아야 함)
        print("경고: 보낼 감지 결과 데이터가 비어있습니다. API 전송 스킵.")
        return

    try:
        # 데이터를 JSON 형태로 변환
        json_data = json.dumps(
            detection_result_data, indent=4
        )  # 보기 좋게 들여쓰기 추가

        # HTTP POST 요청 전송
        # headers={'Content-Type': 'application/json'}를 명시하여 서버에 JSON 데이터임을 알립니다.
        print(
            f"API 서버로 감지 결과 전송 시도 (스레드): {api_url}"
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


# 감지 결과를 API 서버로 비동기적으로 전송하는 함수
# 이 함수는 이제 DetectionResultDto 구조에 맞는 단일 딕셔너리를 받습니다.
def send_detection_result_to_api_async(
    api_url, detection_result_data
):  # 함수 이름 및 인자 변경
    """
    감지 결과를 별도의 스레드에서 API 서버에 전송합니다.

    Args:
        api_url (str): 감지 결과를 수신할 API 엔드포인트 URL.
        detection_result_data (dict): 감지 결과를 담은 딕셔너리 (DetectionResultDto 구조).
    """
    if not detection_result_data:
        # 보낼 데이터가 없으면 함수 종료
        print("경고: 보낼 감지 결과 데이터가 비어있습니다. API 전송 스레드 시작 스킵.")
        return

    # API 호출을 처리할 새로운 스레드 생성
    api_thread = threading.Thread(
        target=_send_detection_result_to_api_threaded,
        args=(api_url, detection_result_data),
    )  # 함수 이름 변경
    # 데몬 스레드로 설정하여 메인 스크립트 종료 시 함께 종료되도록 함
    api_thread.daemon = True
    # 스레드 시작
    api_thread.start()
    print("API 전송 스레드 시작됨.")


# # 스냅샷 저장 함수 (로컬에 임시 저장)
# def save_snapshot_local(frame, detected_defects_list, snapshot_base_dir):  # 인자 추가
#     """
#     감지 결과에 따라 스냅샷을 로컬 디렉토리에 저장하고 파일 경로를 반환합니다.
#     불량 감지 시 snapshots/defects에, 정상 감지 시 snapshots/normal에 저장합니다.
#     """
#     if frame is None:
#         print("오류: 저장할 프레임이 없습니다.")
#         return None

#     timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

#     # 스냅샷 저장 기본 디렉토리 및 하위 디렉토리 설정
#     normal_dir = os.path.join(snapshot_base_dir, "normal")
#     defects_dir = os.path.join(snapshot_base_dir, "defects")

#     # 디렉토리 생성 확인
#     os.makedirs(normal_dir, exist_ok=True)
#     os.makedirs(defects_dir, exist_ok=True)

#     # 불량 감지 여부에 따라 저장 디렉토리 결정
#     if detected_defects_list:
#         # 불량 감지 시 defects 디렉토리에 저장
#         filename = f"defect_snapshot_{timestamp}.png"
#         save_dir = defects_dir
#     else:
#         # 정상 감지 시 normal 디렉토리에 저장
#         filename = f"normal_snapshot_{timestamp}.png"
#         save_dir = normal_dir

#     file_path = os.path.join(save_dir, filename)

#     try:
#         cv.imwrite(file_path, frame)
#         print(f"스냅샷 저장 완료: {file_path}")
#         # 저장된 파일의 상대 경로 반환 (Spring Boot에서 접근 가능하도록)
#         # Spring Boot가 SNAPSHOT_BASE_DIR을 정적 리소스로 제공한다고 가정
#         # 예: snapshots/defects/defect_snapshot_20231027_103000.png
#         # snapshot_base_dir을 기준으로 상대 경로 생성
#         relative_path = os.path.join(
#             os.path.basename(snapshot_base_dir), os.path.basename(save_dir), filename
#         ).replace(
#             "\\", "/"
#         )  # 경로 구분자 통일
#         return relative_path
#     except Exception as e:
#         print(f"스냅샷 저장 중 오류 발생: {e}")
#         return None


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

    # 현재 시간을 기반으로 파일명 생성 (예: 20231027_143000_123456.png)
    timestamp = datetime.now().strftime(
        "%Y%m%d_%H%M%S_%f"
    )  # 밀리초 추가하여 고유성 높임
    filename = os.path.join(
        save_dir, f"snapshot_{timestamp}.png"
    )  # 파일명 접두사 변경 (defect_snapshot -> snapshot)

    try:
        # 이미지 파일 저장
        cv.imwrite(filename, frame)
        print(f"로컬 스냅샷 임시 저장 완료: {filename}")
        return filename
    except Exception as e:
        print(f"오류: 로컬 스냅샷 임시 저장 실패: {e}")
        return None


# AWS S3에 파일 업로드 함수 (감지 결과에 따라 S3 객체 경로 분리)
def upload_file_to_s3(
    s3_client, bucket_name, file_path, s3_object_base_path, defect_detected
):  # 인자 추가
    """
    로컬 파일을 S3 버킷에 업로드하고 공개 URL을 반환합니다.
    감지 결과에 따라 다른 S3 객체 경로에 저장합니다.

    Args:
        s3_client: 초기화된 boto3 S3 클라이언트 객체.
        bucket_name (str): 대상 S3 버킷 이름.
        file_path (str): 업로드할 로컬 파일의 전체 경로.
        s3_object_base_path (str): S3에 저장될 객체의 기본 경로 (폴더 경로). 예: "snapshots/"
        defect_detected (bool): 불량이 감지되었는지 여부.

    Returns:
        str: 업로드된 객체의 공개 URL (성공 시), 또는 None (실패 시).
    """
    if s3_client is None:
        print("S3 클라이언트가 초기화되지 않았습니다. 업로드를 건너뛰니다.")
        return None

    # 감지 결과에 따라 S3 객체 하위 경로 결정
    sub_path = S3_DEFECTS_SUBPATH if defect_detected else S3_NORMAL_SUBPATH

    # S3에 저장될 객체 이름 생성 (예: snapshots/defects/snapshot_YYYYMMDD_HHMMSS_f.png)
    # 파일 경로를 S3 객체 키로 사용할 때, OS별 경로 구분자(\ 또는 /)를
    # S3에서 사용하는 '/'로 통일하는 것이 좋습니다.
    object_name = os.path.join(
        s3_object_base_path, sub_path, os.path.basename(file_path)
    ).replace("\\", "/")

    try:
        # 파일 업로드
        # ExtraArgs={'ACL': 'public-read'} 를 사용하여 업로드된 객체에 공개 읽기 권한 부여
        # 버킷 정책으로 퍼블릭 액세스를 제어하는 경우 이 부분은 필요 없을 수 있습니다.
        # ContentType을 명시하여 브라우저에서 올바르게 해석하도록 할 수 있습니다.
        s3_client.upload_file(
            file_path,
            bucket_name,
            object_name,
            ExtraArgs={
                # "ACL": "public-read",
                "ContentType": "image/png",
            },  # PNG 이미지로 가정
        )
        print(f"'{file_path}' 파일을 S3에 업로드 성공: '{object_name}'")

        # 업로드된 객체의 공개 URL 생성
        # S3 객체 URL 형식: https://[버킷이름].s3.[리전].amazonaws.com/[객체이름]
        # 또는 가상 호스팅 방식: https://[버킷이름].s3.amazonaws.com/[객체이름]
        # 또는 웹사이트 호스팅 방식: http://[버킷이름].s3-website.[리전].amazonaws.com/[객체이름]

        # 기본 URL 형식 (가장 일반적)
        # 클라이언트 생성 시 사용한 리전 정보를 활용하여 URL 생성
        public_url = f"https://{bucket_name}.s3.{s3_client.meta.region_name}.amazonaws.com/{object_name}"

        # 버킷이 웹사이트 호스팅으로 설정된 경우 다른 URL 형식을 사용할 수 있습니다.
        # public_url = f"http://{bucket_name}.s3-website.{s3_client.meta.region_name}.amazonaws.com/{object_name}"

        return public_url

    except FileNotFoundError:
        print(f"오류: 업로드할 파일을 찾을 수 없습니다: {file_path}")
        return None
    except NoCredentialsError:
        print(
            "AWS 자격 증명을 찾을 수 없습니다. AWS CLI 설정 또는 환경 변수를 확인하세요."
        )
        return None
    except PartialCredentialsError:
        print("불완전한 AWS 자격 증명이 감지되었습니다. AWS 자격 증명을 확인하세요.")
        return None
    except Exception as e:
        print(f"S3 업로드 중 오류 발생: {e}")
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
    opencv_font,
    opencv_font_scale,
    opencv_font_thickness,
    area_threshold_substandard_percent,
    area_threshold_defective_percent,
    inference_interval_seconds,
    snapshot_temp_dir,  # 임시 저장 디렉토리 변수 이름 변경
    s3_bucket_name,
    aws_region,
    s3_object_base_path,  # S3 객체 기본 경로 매개변수 추가
    mqtt_broker_host,
    mqtt_broker_port,
    mqtt_topic_status,
    mqtt_topic_details,
    api_detection_result_url,  # API 엔드포인트 URL 변수 이름 변경
    stream_host,
    stream_port,
):
    """
    영상 스트림에서 프레임을 읽고 감지, 판별, 시각화 과정을 반복합니다.
    설정된 시간 간격마다 감지를 수행하고, 불량 감지 시 스냅샷을 저장하며 MQTT 신호와 API 요청을 보냅니다.
    """
    global latest_frame  # 전역 변수 사용 선언

    # 1. 모델 로드
    model = load_yolo_model(model_path)
    if model is None:
        return

    # 2. 영상 스트림 열기
    cap = open_video_stream(stream_url)
    if cap is None:
        return

    # 3. MQTT 클라이언트 초기화 및 연결
    mqtt_client = initialize_mqtt_client(mqtt_broker_host, mqtt_broker_port)
    if mqtt_client is None:
        print("MQTT 연결에 실패했습니다. 불량 감지 시 MQTT 메시지를 보내지 않습니다.")

    # 4. AWS S3 클라이언트 초기화
    s3_client = None  # 초기화
    if (
        s3_bucket_name and aws_region
    ):  # 버킷 이름과 리전이 설정된 경우에만 S3 클라이언트 생성 시도
        try:
            s3_client = boto3.client("s3", region_name=aws_region)
            # S3 버킷 존재 여부 확인 (선택 사항)
            # s3_client.head_bucket(Bucket=s3_bucket_name)
            print(
                f"AWS S3 클라이언트 생성 완료. 버킷: {s3_bucket_name}, 리전: {aws_region}"
            )
        except NoCredentialsError:
            print("AWS 자격 증명을 찾을 수 없습니다. S3 기능을 사용할 수 없습니다.")
        except PartialCredentialsError:
            print(
                "불완전한 AWS 자격 증명이 감지되었습니다. AWS 자격 증명을 확인하세요."
            )
        except Exception as e:
            print(
                f"AWS S3 클라이언트 생성 또는 버킷 확인 오류: {e}. S3 기능을 사용할 수 없습니다."
            )
            s3_client = None  # 오류 발생 시 클라이언트를 None으로 설정
    else:
        print(
            "S3 버킷 이름 또는 리전이 설정되지 않았습니다. S3 기능을 사용할 수 없습니다."
        )

    # 5. MJPEG 스트리밍 서버 시작 (별도 스레드)
    stream_server_thread = threading.Thread(
        target=run_mjpeg_stream_server, args=(stream_host, stream_port)
    )
    stream_server_thread.daemon = True  # 메인 스레드 종료 시 함께 종료되도록 데몬 설정
    stream_server_thread.start()
    print("MJPEG 스트리밍 서버 스레드 시작됨.")

    print("\n영상 스트림으로부터 프레임을 읽고 감지를 시작합니다.")
    print(
        f"처리된 영상은 http://{stream_host}:{stream_port}/ 에서 MJPEG 스트림으로 확인 가능합니다."
    )
    print(f"감지는 약 {inference_interval_seconds}초 간격으로 수행됩니다.")
    if s3_client:
        print(
            f"스냅샷은 '{snapshot_temp_dir}'에 임시 저장 후 S3 버킷 '{s3_bucket_name}' ({aws_region})의 '{s3_object_base_path}' 경로에 업로드됩니다."
        )
    else:
        print(f"스냅샷은 '{snapshot_temp_dir}'에 임시 저장만 됩니다 (S3 설정 없음).")
    print(f"Spring Boot API URL: {api_detection_result_url}")  # 변수 이름 변경 반영
    print("'q' 키를 누르면 감지를 종료합니다.")

    # 마지막 추론(감지) 시간을 기록하는 변수 초기화
    last_inference_time = time.time() - inference_interval_seconds

    # Optional: FPS 계산을 위한 변수 초기화
    # start_time = time.time()
    # frame_count = 0

    while True:
        ret, frame = cap.read()
        if not ret:
            print(
                "영상 스트림에서 프레임을 더 이상 읽을 수 없습니다. 스트림이 종료되었거나 오류가 발생했습니다."
            )
            break

        current_time = time.time()

        if current_time - last_inference_time >= inference_interval_seconds:
            print(f"\n--- 감지 수행 (약 {inference_interval_seconds}초 경과) ---")
            last_inference_time = current_time

            # 6. 객체 감지 및 세그멘테이션 추론 수행
            results = perform_inference(model, frame, conf_threshold, iou_threshold)

            # 7. 감지 결과를 분석하여 불량 판별
            # analyze_defects 함수는 불량만 담은 딕셔너리 리스트를 반환
            detected_defects_list = analyze_defects(
                results,
                model.names,
                area_threshold_substandard_percent,
                area_threshold_defective_percent,
            )

            # 8. 감지 결과 기본 시각화 (Ultralytics plot 사용)
            annotated_frame = frame.copy()
            if results and results[0].boxes:
                plot_masks = (
                    hasattr(results[0], "masks") and results[0].masks is not None
                )
                annotated_frame = results[0].plot(
                    masks=plot_masks, boxes=True, labels=False, conf=False
                )

            # 9. 불량 판별 결과에 따라 시각화에 추가 정보 그리기 (OpenCV 영문 텍스트 사용)
            annotated_frame = visualize_results(
                annotated_frame,
                detected_defects_list,
                opencv_font,
                opencv_font_scale,
                opencv_font_thickness,
            )

            # --- 처리된 프레임을 전역 변수에 업데이트 (스트리밍 서버에서 사용) ---
            with frame_lock:
                latest_frame = annotated_frame.copy()

            # --- 스냅샷 저장 및 S3 업로드 ---
            print("스냅샷 저장 및 업로드 처리 중...")
            # 스냅샷 저장 (로컬 파일로 임시 저장) - 불량 여부와 관계없이 항상 저장
            snapshot_filepath = save_snapshot_local(annotated_frame, snapshot_temp_dir)

            image_url = None  # 업로드된 이미지 URL 초기화
            defect_detected = (
                detected_defects_list is not None and len(detected_defects_list) > 0
            )  # 불량 감지 여부 판단

            # 스냅샷 파일이 성공적으로 저장되었고 S3 클라이언트가 유효한 경우 S3에 업로드 시도
            if snapshot_filepath and s3_client:
                try:
                    # S3에 파일 업로드 및 공개 URL 가져오기
                    # 감지 결과에 따라 S3 객체 경로를 분리하여 전달
                    image_url = upload_file_to_s3(
                        s3_client,
                        s3_bucket_name,
                        snapshot_filepath,
                        s3_object_base_path,
                        defect_detected,
                    )  # 인자 추가

                    if image_url:
                        print(f"S3 업로드 이미지 URL: {image_url}")
                        # 로컬에 임시 저장된 스냅샷 파일 삭제 (선택 사항)
                        try:
                            os.remove(snapshot_filepath)
                            print(f"로컬 임시 스냅샷 파일 삭제: {snapshot_filepath}")
                        except Exception as e:
                            print(f"로컬 임시 스냅샷 파일 삭제 오류: {e}")

                    else:
                        print("S3 이미지 URL 가져오기 실패.")

                except Exception as e:
                    print(f"스냅샷 S3 업로드 또는 URL 처리 중 오류 발생: {e}")
                    # 오류 발생 시 image_url은 None 상태로 유지

            # --- 감지 결과 데이터 (DetectionResultDto 구조) 준비 ---
            # analyze_defects에서 반환된 detected_defects_list 사용
            status = "Defect Detected" if defect_detected else "Normal"
            defect_count = len(detected_defects_list) if defect_detected else 0
            # 불량 유형 요약 생성
            defect_summary = "Normal"
            if defect_detected:
                # 상세 사유 (detailed_reason)를 사용하여 요약 생성
                detailed_reasons = [
                    d.get("detailed_reason", "Unknown") for d in detected_defects_list
                ]
                # 중복 제거 및 쉼표로 연결
                defect_summary = ", ".join(sorted(list(set(detailed_reasons))))

            # Spring Boot API로 전송할 데이터 구조 (DetectionResultDto와 일치)
            detection_result_data = {
                "detectionTime": datetime.now().isoformat(),  # 현재 시간 (ISO 8601 형식)
                "status": status,
                "defectCount": defect_count,
                "imageUrl": image_url,  # S3 업로드된 이미지 URL (없으면 None)
                "defectSummary": defect_summary,
                "defects": detected_defects_list,  # 불량 상세 정보 리스트 (없으면 빈 리스트)
            }

            # --- API 서버로 감지 결과 전송 ---
            # send_detection_result_to_api_async 함수는 이제 DetectionResultDto 구조의 딕셔너리를 받음
            # 불량 여부와 관계없이 항상 API로 전송하여 로그를 남김
            print(
                f"감지 결과 데이터 API 전송 시도: Status='{status}', Count={defect_count}, ImageURL='{image_url}'"
            )
            send_detection_result_to_api_async(
                api_detection_result_url, detection_result_data
            )

            # --- MQTT 메시지 발행 (선택 사항: API 전송으로 대체 가능) ---
            # 불량 감지 상태 신호 보내기 (간단한 메시지)
            status_message_mqtt = "Defect Detected" if defect_detected else "Normal"
            publish_mqtt_message(mqtt_client, mqtt_topic_status, status_message_mqtt)
            print(
                f"MQTT 상태 메시지 발행: '{status_message_mqtt}' to '{mqtt_topic_status}'"
            )

            # 불량 상세 정보 보내기 (JSON 형태) - 이제 API 전송 데이터와 동일한 구조
            # MQTT 상세 메시지에는 불량 목록만 포함하거나, 전체 결과를 포함할 수 있습니다.
            # 여기서는 API로 보내는 전체 결과 구조를 MQTT로도 보냅니다.
            details_message_mqtt = json.dumps(
                detection_result_data, indent=4
            )  # 전체 결과 데이터 사용
            publish_mqtt_message(mqtt_client, mqtt_topic_details, details_message_mqtt)
            print(
                f"MQTT 상세 메시지 발행: {len(detected_defects_list) if detected_defects_list else 0}건의 불량 정보 (API 구조) to '{mqtt_topic_details}'"
            )

        # 9. 결과 화면 표시 (MJPEG 스트리밍으로 대체)
        # cv.imshow는 더 이상 사용하지 않습니다.

        # 키 입력을 대기하고 'q' 키가 눌리면 루프 종료
        if cv.waitKey(1) & 0xFF == ord("q"):
            break

    # --- 자원 해제 ---
    cap.release()
    # cv.destroyAllWindows()는 더 이상 사용하지 않습니다.

    # MQTT 클라이언트 연결 종료
    if mqtt_client:
        mqtt_client.loop_stop()
        mqtt_client.disconnect()
        print("MQTT 클라이언트 연결 종료.")

    print("\n감지 및 영상 스트림 처리가 종료되었습니다.")


# --- 스크립트 실행 ---
if __name__ == "__main__":
    # AWS S3 버킷 이름과 리전을 여기에 설정하세요.
    # S3를 사용하지 않으려면 이 변수들을 None 또는 빈 문자열로 설정하세요.
    YOUR_S3_BUCKET_NAME = "ajwproject2bucket"  # <-- 실제 버킷 이름으로 변경!
    YOUR_AWS_REGION = (
        "ap-northeast-2"  # <-- 실제 AWS 리전으로 변경! (예: 서울 리전 ap-northeast-2)
    )
    YOUR_S3_OBJECT_BASE_PATH = "snapshots/"  # S3에 저장될 객체의 기본 경로 (폴더)

    # MJPEG 스트리밍 서버 설정
    STREAM_SERVER_HOST = "0.0.0.0"  # 모든 인터페이스에서 접근
    STREAM_SERVER_PORT = 8080  # 사용할 포트 (방화벽 설정 확인 필요)

    # Spring Boot API 기본 URL 설정
    # 이 URL은 Spring Boot 애플리케이션의 호스트와 포트, 감지 결과를 수신할 엔드포인트입니다.
    YOUR_API_DETECTION_RESULT_URL = "http://localhost:80/api/defect"  # Spring Boot 서버 주소 및 포트 + API 엔드포인트
    # # Spring Boot API 기본 URL 설정
    # # 이 URL은 Spring Boot 애플리케이션의 호스트와 포트입니다.
    # # 스냅샷 이미지를 제공하는 컨트롤러의 기본 경로(/api/snapshots)는
    # # 이 기본 URL 뒤에 붙습니다.
    # YOUR_SPRING_BOOT_API_BASE_URL = (
    #     "http://localhost:80"  # Spring Boot 서버 주소 및 포트
    # )

    # # 스냅샷 저장 기본 디렉토리 설정
    # # 이 디렉토리는 Spring Boot 애플리케이션에서 정적 리소스로 제공해야 합니다.
    # YOUR_SNAPSHOT_BASE_DIR = "snapshots"  # 프로젝트 루트의 snapshots 디렉토리

    main_loop(
        MODEL_PATH,
        VIDEO_STREAM_URL,
        CONF_THRESHOLD,
        IOU_THRESHOLD,
        OPENCV_FONT,
        OPENCV_FONT_SCALE,
        OPENCV_FONT_THICKNESS,
        AREA_THRESHOLD_SUBSTANDARD_PERCENT,
        AREA_THRESHOLD_DEFECTIVE_PERCENT,
        INFERENCE_INTERVAL_SECONDS,
        SNAPSHOT_TEMP_DIR,  # 임시 저장 디렉토리 전달
        YOUR_S3_BUCKET_NAME,  # S3 버킷 이름 전달 (None 가능)
        YOUR_AWS_REGION,  # AWS 리전 전달 (None 가능)
        YOUR_S3_OBJECT_BASE_PATH,  # S3 객체 기본 경로 전달
        MQTT_BROKER_HOST,
        MQTT_BROKER_PORT,
        MQTT_TOPIC_STATUS,
        MQTT_TOPIC_DETAILS,
        YOUR_API_DETECTION_RESULT_URL,  # API 엔드포인트 URL 전달
        STREAM_SERVER_HOST,
        STREAM_SERVER_PORT,
    )
