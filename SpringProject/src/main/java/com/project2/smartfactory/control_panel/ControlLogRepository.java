package com.project2.smartfactory.control_panel;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlLogRepository extends JpaRepository<ControlLog, Long> {

    // 예: 모든 로그를 최신 순으로 정렬하여 조회
    List<ControlLog> findAllByOrderByControlTimeDesc();

}
