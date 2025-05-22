package com.project2.smartfactory.task;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_task_progress", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"userId", "recordDate"}) // userId와 recordDate 조합은 고유해야 함
})
@Data // Lombok: Getter, Setter, toString, equals, hashCode 자동 생성
@AllArgsConstructor // Lombok: 모든 필드를 인자로 받는 생성자 자동 생성
@NoArgsConstructor
public class DailyTaskProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 ID
    private Long id;

    @Column(nullable = false)
    private String userId; // 사용자를 식별하는 ID (항상 'admin'이 될 것임)

    @Column(nullable = false)
    private LocalDate recordDate; // 작업량을 기록한 날짜

    @Column(nullable = false)
    private int dailyTotalTasks; // 해당 날짜의 총 작업량 (추가됨)

    @Column(nullable = false)
    private int completedTasks; // 해당 날짜에 완료된 작업량 (이름 변경)

    public DailyTaskProgress(String userId, LocalDate recordDate, int dailyTotalTasks, int completedTasks) {
        this.userId = userId;
        this.recordDate = recordDate;
        this.dailyTotalTasks = dailyTotalTasks;
        this.completedTasks = completedTasks;
    }
}
