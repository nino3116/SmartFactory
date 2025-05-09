package com.project2.smartfactory.defect;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionLogRepository extends JpaRepository<DetectionLog, Long> {

    // 예: 모든 로그를 최신 순으로 정렬하여 조회
    List<DetectionLog> findAllByOrderByDetectionTimeDesc();

}
