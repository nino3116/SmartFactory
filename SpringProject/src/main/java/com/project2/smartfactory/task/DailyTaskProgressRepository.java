package com.project2.smartfactory.task;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyTaskProgressRepository extends JpaRepository<DailyTaskProgress, Long> {
    // userId와 recordDate로 DailyTaskProgress 엔티티를 찾는 메서드
    Optional<DailyTaskProgress> findByUserIdAndRecordDate(String userId, LocalDate recordDate);

    // userId와 날짜 범위로 DailyTaskProgress 엔티티 리스트를 찾는 메서드
    List<DailyTaskProgress> findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(String userId, LocalDate startDate, LocalDate endDate);
}
