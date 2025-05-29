package com.project2.smartfactory.control_panel;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "control_logs") // 매핑될 데이터베이스 테이블 이름
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControlLog {
  @Id // 기본 키 필드 지정
  @GeneratedValue(strategy = GenerationType.IDENTITY) // 데이터베이스의 자동 증가 기능 사용
  private Long id; // 기본 키

  @Column(name = "control_time", nullable = false) // 컨트롤 시간
  private LocalDateTime controlTime;

  @Column(name = "control_type", nullable = false) // 컨트롤 유형
  private String controlType;

  @Column(name = "control_data", nullable = false) // 조작 내용
  private String controlData;

  @Column(name = "control_result_status", nullable = false) // 조작 결과
  private String controlResultStatus;

  @Column(name = "control_memo", nullable = false) // 비고
  private String controlMemo;

  public ControlLog(String controlType, String controlData,
      String controlResultStatus, String controlMemo) {
    this.controlTime = LocalDateTime.now();
    this.controlType = controlType;
    this.controlData = controlData;
    this.controlResultStatus = controlResultStatus;
    this.controlMemo = controlMemo;
  }

}
