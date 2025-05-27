package com.project2.smartfactory.defect;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList; // 스레드 안전한 리스트 사용
import java.util.stream.Collectors;

@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor
public class DefectService {

    // MQTT로부터 수신된 최신 불량 정보를 저장할 리스트 (API에서 최신 정보 제공 용도)
    private final List<DefectInfo> latestDefects = new CopyOnWriteArrayList<>();

    private final DefectRepository defectRepository; // DefectRepository 주입
    private final DetectionLogRepository detectionLogRepository; // DetectionLogRepository 주입


    /**
     * 파이썬 스크립트로부터 수신된 감지 결과를 처리하고 데이터베이스에 저장합니다.
     * 감지 이벤트 로그 및 불량 상세 정보를 기록합니다.
     * @param detectionResultDto 수신된 감지 결과 데이터 (로그 정보 및 불량 목록 포함)
     */
    @Transactional // 트랜잭션 관리
    public void processAndSaveDetectionResult(DetectionResultDto detectionResultDto) { // 인자 타입 변경
        System.out.println("감지 결과 수신 및 데이터베이스 저장 처리 중...");

        if (detectionResultDto == null) {
            System.err.println("처리할 감지 결과 데이터가 null입니다.");
            return; // null 데이터는 처리하지 않음
        }

        try {
            // 1. 감지 이벤트 로그 저장 (DetectionResultDto의 정보 사용)
            // DetectionLog 엔티티 생성
            DetectionLog logEntry = new DetectionLog(
                detectionResultDto.getStatus(),
                detectionResultDto.getDefectCount(),
                detectionResultDto.getImageUrl(), // DTO의 전체 이미지 URL 사용
                detectionResultDto.getDefectSummary()
            );
            // 감지 시간은 DTO에서 받아오거나, 여기서 새로 설정할 수 있습니다.
            // 파이썬에서 시간을 보내준다면 DTO에서 받아오는 것이 좋습니다.
            if (detectionResultDto.getDetectionTime() != null) {
                logEntry.setDetectionTime(detectionResultDto.getDetectionTime());
            } else {
                 // DTO에 시간이 없다면 현재 시간 설정
                logEntry.setDetectionTime(LocalDateTime.now());
            }

            detectionLogRepository.save(logEntry);
            System.out.println("감지 로그 데이터베이스 저장 완료: " + logEntry.getStatus());

            // 2. 불량 상세 정보 저장 (DetectionResultDto의 불량 목록 사용)
            List<DefectInfo> defects = detectionResultDto.getDefects();
            boolean defectDetected = defects != null && !defects.isEmpty();

            if (defectDetected) {
                // 불량 정보에 로그 엔티티 연결 (필요시) 또는 DefectInfo 자체에 로그 ID 추가
                // 현재 DefectInfo 엔티티는 DetectionLog와의 직접적인 연관 관계가 없습니다.
                // 만약 불량 상세 정보와 로그를 연결하고 싶다면 DefectInfo 엔티티에
                // DetectionLog에 대한 참조 필드를 추가하고 매핑해야 합니다.
                // 여기서는 간단하게 불량 정보만 별도로 저장합니다.

                // DefectInfo 엔티티 저장 전에 imageUrl과 detectionTime 설정
                LocalDateTime currentDetectionTime = logEntry.getDetectionTime(); // 저장된 로그의 시간 사용
                String overallImageUrl = detectionResultDto.getImageUrl(); // DTO의 전체 이미지 URL

                for (DefectInfo defect : defects) {
                    // 각 DefectInfo 객체에 전체 감지 이미지 URL과 감지 시간 설정
                    // 만약 개별 불량 이미지 URL이 필요하다면, Python에서 해당 데이터를
                    // MQTT 페이로드의 각 불량 객체에 추가하고 DefectInfo 엔티티도 수정해야 합니다.
                    defect.setImageUrl(overallImageUrl);
                    defect.setDetectionTime(currentDetectionTime);
                }


                // DefectInfo 엔티티 저장
                List<DefectInfo> savedDefects = defectRepository.saveAll(defects);
                System.out.println(savedDefects.size() + " 건의 불량 정보 데이터베이스 저장 완료.");

                // API 엔드포인트에서 최신 정보를 제공하기 위해 메모리 내 리스트 업데이트
                latestDefects.clear();
                latestDefects.addAll(savedDefects); // 저장된 객체를 리스트에 추가

                // 저장된 불량 정보 확인 (디버깅용)
                // latestDefects.forEach(defect -> System.out.println(defect.toString()));
            } else {
                 // 불량 감지되지 않았을 경우 (Normal 상태)
                 // 불량 상세 정보는 저장하지 않습니다.
                 // 최신 불량 정보 리스트는 이전 불량 정보를 유지하거나 비울 수 있습니다.
                 // 여기서는 Normal 상태가 감지되면 최신 불량 정보 리스트를 비우도록 합니다.
                latestDefects.clear();
                System.out.println("불량 감지되지 않음. 불량 상세 정보 저장 스킵.");
            }


        } catch (Exception e) {
            System.err.println("데이터베이스 저장 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            // 데이터베이스 저장 실패 시 예외를 다시 던져 트랜잭션 롤백을 유도할 수 있습니다.
            throw new RuntimeException("Failed to save detection result to database", e);
        }
    }

    /**
     * 저장된 최신 불량 정보를 가져옵니다.
     * 이 정보는 API 엔드포인트에서 웹 페이지에 표시하기 위해 사용됩니다.
     * @return 최신 불량 정보 리스트 (수정 불가능한 리스트 반환)
     */
    public List<DefectInfo> getLatestDefects() {
        // 외부에서 리스트를 직접 수정하지 못하도록 수정 불가능한 리스트의 복사본을 반환합니다.
        return Collections.unmodifiableList(new ArrayList<>(latestDefects));
    }

    /**
     * 데이터베이스에 저장된 모든 감지 로그를 최신 순으로 가져옵니다.
     * @return 감지 로그 리스트
     */
    public List<DetectionLog> getAllDetectionLogs() {
        System.out.println("데이터베이스에서 모든 감지 로그 조회 중...");
        // DetectionLogRepository를 사용하여 모든 로그를 최신 순으로 조회
        List<DetectionLog> logs = detectionLogRepository.findAllByOrderByDetectionTimeDesc();
        System.out.println(logs.size() + " 건의 감지 로그 조회 완료.");
        return logs; // 조회된 로그 리스트 반환
    }


    // 필요에 따라 데이터베이스 조회, 삭제 등 추가 서비스 메소드를 구현할 수 있습니다.
    // 예: 특정 기간의 불량 정보 조회 (차트 등에 사용)
    // public List<DefectInfo> getDefectsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
    //      // DefectRepository에 해당 조회 메소드를 추가하고 여기서 호출합니다.
    //      // return defectRepository.findByDetectionTimeBetween(startDate, endDate); // 예시
    // }
}
