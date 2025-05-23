package com.project2.smartfactory.mqtt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class SystemControlController {
  private final MqttPublisherService mqttPublisherService;
  private final MqttSubscriberService mqttSubscriberService;

  @Value("${mqtt.topic.system.command}")
  private String commandTopic;

  /**
     * 스크립트 시작 명령을 MQTT로 발행하는 엔드포인트.
     * defects.html의 SCRIPT_CONTROL_START_URL에 해당합니다.
     * @return 응답 상태
     */
    @GetMapping("/control/system/start")
    public ResponseEntity<String> startScript() {
        System.out.println("웹 요청: 시스템 시작 명령 수신");
        try {
            // MQTT Publisher 서비스를 사용하여 명령 토픽으로 "START" 메시지 발행
            mqttPublisherService.publishMessage(commandTopic, "START", 2, false); // QoS 1, Retained false
            mqttPublisherService.publishMessage(commandTopic, "status_request", 2, false);
            return ResponseEntity.ok("시스템 시작 명령 발행 성공");
        } catch (Exception e) {
            System.err.println("시스템 시작 명령 발행 중 오류: " + e.getMessage());
            return ResponseEntity.internalServerError().body("시스템 시작 명령 발행 실패: " + e.getMessage());
        }
    }

    /**
     * 스크립트 중지 명령을 MQTT로 발행하는 엔드포인트.
     * defects.html의 SCRIPT_CONTROL_STOP_URL에 해당합니다.
     * @return 응답 상태
     */
    @GetMapping("/control/system/stop")
    public ResponseEntity<String> stopScript() {
        System.out.println("웹 요청: 시스템 중지 명령 수신");
         try {
            // MQTT Publisher 서비스를 사용하여 명령 토픽으로 "STOP" 메시지 발행
            mqttPublisherService.publishMessage(commandTopic, "STOP", 2, false); // QoS 1, Retained false
            mqttPublisherService.publishMessage(commandTopic, "status_request", 2, false);
            return ResponseEntity.ok("시스템 중지 명령 발행 성공");
        } catch (Exception e) {
            System.err.println("시스템 중지 명령 발행 중 오류: " + e.getMessage());
            return ResponseEntity.internalServerError().body("시스템 중지 명령 발행 실패: " + e.getMessage());
        }
    }

    /**
     * 현재 스크립트 상태를 반환하는 엔드포인트.
     * defects.html의 SCRIPT_STATUS_API_URL에 해당합니다.
     * @return 현재 스크립트 상태 문자열
     */
    @GetMapping("/status/system")
    public ResponseEntity<String> getSystemStatus() {
        System.out.println("웹 요청: 시스템 상태 조회 수신");
        // MQTT Status Subscriber로부터 현재 상태 가져와서 반환
        String status = mqttSubscriberService.getCurrentSystemStatus();
        System.out.println("현재 시스템 상태: " + status);
        return ResponseEntity.ok(status);
    }
}
