package com.project2.smartfactory.defect;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

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
     * MQTT로부터 수신된 불량 정보를 처리하고 데이터베이스에 저장합니다.
     * 불량 감지 이벤트 로그도 함께 기록합니다.
     * @param defects MQTT 메시지로부터 파싱된 불량 정보 리스트
     */
    @Transactional
    public void processAndSaveDefects(List<DefectInfo> defects) {
        List<DefectInfo> safeDefects = Optional.ofNullable(defects).orElse(Collections.emptyList());
        
        boolean defectDetected = !safeDefects.isEmpty();
        String status = defectDetected ? "Defect Detected" : "Normal";
        Integer defectCount = safeDefects.size();
        String imageUrl = defectDetected && safeDefects.get(0).getImageUrl() != null ? 
            safeDefects.get(0).getImageUrl() : null;

        String defectSummary = defectDetected
            ? safeDefects.stream()
                         .map(DefectInfo::getDetailedReason) // 상세 사유 (영문) 사용
                         .distinct() // 중복 제거
                         .collect(Collectors.joining(", ")) // 쉼표로 연결
            : "Normal"; // 정상이면 "Normal"

        System.out.println("불량 정보 수신 및 데이터베이스 저장 처리 중...");

        try {
            // 1. 불량 감지 이벤트 로그 저장
            DetectionLog logEntry = new DetectionLog(status, defectCount, imageUrl, defectSummary);
            detectionLogRepository.save(logEntry);
            System.out.println("감지 로그 데이터베이스 저장 완료: " + status);

            // 2. 불량 상세 정보 저장 (불량이 감지된 경우에만)
            if (defectDetected) {
                List<DefectInfo> savedDefects = defectRepository.saveAll(safeDefects);
                System.out.println(savedDefects.size() + " 건의 불량 정보 데이터베이스 저장 완료.");

                // API 엔드포인트에서 최신 정보를 제공하기 위해 메모리 내 리스트 업데이트
                latestDefects.clear();
                latestDefects.addAll(savedDefects); // 저장된 객체를 리스트에 추가

                // 저장된 불량 정보 확인 (디버깅용)
                // latestDefects.forEach(defect -> System.out.println(defect.toString()));
            } else {
                 // 불량 감지되지 않았을 경우 최신 불량 정보 리스트 비우기 (선택 사항)
                 // latestDefects.clear();
                 System.out.println("불량 감지되지 않음. 불량 상세 정보 저장 스킵.");
            }


        } catch (Exception e) {
            System.err.println("데이터베이스 저장 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            // 데이터베이스 저장 실패 시 예외를 다시 던져 트랜잭션 롤백을 유도할 수 있습니다.
            // throw new RuntimeException("Failed to save data to database", e);
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
    //     // DefectRepository에 해당 조회 메소드를 추가하고 여기서 호출합니다.
    //     // return defectRepository.findByDetectionTimeBetween(startDate, endDate); // 예시
    // }
}