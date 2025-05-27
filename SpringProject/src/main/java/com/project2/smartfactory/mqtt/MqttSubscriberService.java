// src/main/java/com/project2/smartfactory/mqtt/MqttSubscriberService.java
package com.project2.smartfactory.mqtt;

import com.fasterxml.jackson.databind.JsonNode; // JsonNode 임포트 추가
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project2.smartfactory.defect.DefectDetectionDetailsDto; // 새로 추가된 DTO 임포트
import com.project2.smartfactory.notification.NotificationService;
import com.project2.smartfactory.notification.Notification; // NotificationType Enum을 사용하기 위해 다시 임포트

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit; // TimeUnit 임포트 추가

@Service // Spring Bean으로 등록
@RequiredArgsConstructor
public class MqttSubscriberService implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(MqttSubscriberService.class);

    // application.properties 또는 application.yml 파일에서 설정 값을 주입받습니다.
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id.subscriber}") // 통합된 클라이언트 ID (고유해야 함)
    private String clientId;

    @Value("${mqtt.topic.status}") // 불량 감지 상태 토픽 (현재 사용되지 않음)
    private String defectStatusTopic;

    @Value("${mqtt.topic.details}") // 불량 감지 상세 정보 토픽
    private String defectDetailsTopic;

    @Value("${mqtt.topic.script.status}") // Python 스크립트 상태 토픽
    private String scriptStatusTopic;

    @Value("${mqtt.topic.conveyor.status}")   // 컨베이어벨트 동작 시스템 상태 토픽
    private String conveyorStatusTopic;

    @Value("${mqtt.topic.detect.result}") // factory/detect_result 토픽
    private String detectResultTopic;

    private MqttClient mqttClient;
    private ObjectMapper objectMapper = new ObjectMapper(); // JSON 파싱을 위한 객체

    // 스크립트의 현재 상태를 저장할 변수 (기본값 설정)
    private String currentScriptStatus = "Unknown";

    private final NotificationService notificationService; // NotificationService 주입


    /**
     * 빈 초기화 시 호출되어 MQTT 클라이언트를 설정하고 브로커에 연결합니다.
     * 토픽을 구독하고 초기 연결 성공/실패 알림을 생성합니다.
     */
    @PostConstruct
    public void init() {
        // ObjectMapper에 JavaTimeModule을 등록하여 LocalDateTime 파싱을 지원합니다.
        objectMapper.registerModule(new JavaTimeModule());
        // 만약 DefectInfo 객체도 LocalDateTime을 사용한다면, DefectInfo를 파싱하는 ObjectMapper에도 동일하게 적용해야 합니다.
        // 현재는 DefectInfo에 LocalDateTime 필드가 없는 것으로 보이지만, 추후 추가될 경우를 대비하여 명시합니다.
        try {
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            mqttClient.setCallback(this); // 콜백 설정

            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true); // 세션 클린 (false로 하면 끊겼다 다시 연결 시 이전 메시지 수신 가능)
            connectOptions.setAutomaticReconnect(true); // 자동 재연결 활성화
            connectOptions.setMaxReconnectDelay(5000); // 최대 재연결 지연 시간 5초

            logger.info("Trying to Connect MQTT Broker: {}", brokerUrl);
            mqttClient.connect(connectOptions);
            logger.info("MQTT Broker Connected. Client ID: {}", clientId);

            // 새로운 토픽들 구독
            mqttClient.subscribe(scriptStatusTopic, 1); // 감지 모듈 상태 토픽 구독
            mqttClient.subscribe(conveyorStatusTopic, 1); // 컨베이어 벨트 상태 토픽 구독
            mqttClient.subscribe(defectDetailsTopic, 1); // 불량 감지 상세 정보 토픽 구독
            mqttClient.subscribe(detectResultTopic, 1); // factory/detect_result 토픽 구독 추가
            logger.info("Connected to MQTT broker and subscribed to topics: [{}, {}, {}, {}]", scriptStatusTopic, conveyorStatusTopic, defectDetailsTopic, detectResultTopic);

            // MQTT 연결 성공 알림 (필요하다면 주석 해제)
            // notificationService.saveNotification(Notification.NotificationType.MQTT_CLIENT, "MQTT 연결", "MQTT 브로커에 성공적으로 연결되었습니다.");

        } catch (MqttException me) {
            logger.error("MQTT Connection Error: {}", me.getMessage(), me);
            // MQTT 연결 실패 알림
            notificationService.saveNotification(Notification.NotificationType.ERROR, "MQTT 연결 실패", "MQTT 브로커 연결에 실패했습니다: " + me.getMessage());
        } catch (Exception e) {
            logger.error("MQTT Subscriber Initialization Error: {}", e.getMessage(), e);
            // MQTT 초기화 오류 알림
            notificationService.saveNotification(Notification.NotificationType.ERROR, "MQTT 초기화 오류", "MQTT Subscriber 초기화 중 오류 발생: " + e.getMessage());
        }
    }

    /**
     * 빈 소멸 시 호출되어 MQTT 클라이언트 연결을 해제합니다.
     * 연결 해제 알림을 생성합니다.
     */
    @PreDestroy
    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect(5000); // 5초 대기 후 연결 해제
                logger.info("MQTT Disconnected (Subscriber)");
                // MQTT 연결 해제 알림 (필요하다면 주석 해제)
                notificationService.saveNotification(Notification.NotificationType.MQTT_CLIENT, "MQTT 연결 해제", "MQTT 브로커 연결이 해제되었습니다.");
            } catch (MqttException me) {
                logger.error("MQTT Error while disconnecting: {}", me.getMessage(), me);
                // MQTT 연결 해제 오류 알림
                notificationService.saveNotification(Notification.NotificationType.ERROR, "MQTT 연결 해제 오류", "MQTT 브로커 연결 해제 중 오류 발생: " + me.getMessage());
            }
        }
    }

    /**
     * MQTT 연결이 끊겼을 때 호출되는 콜백 메서드.
     * 연결 끊김 알림을 생성합니다.
     * @param cause 연결이 끊긴 원인
     */
    @Override
    public void connectionLost(Throwable cause) {
        logger.error("MQTT Connection lost: {}. Paho will attempt to reconnect automatically.", cause.getMessage(), cause);
        // MQTT 연결 끊김 알림
        notificationService.saveNotification(Notification.NotificationType.WARNING, "MQTT 연결 끊김", "MQTT 브로커 연결이 끊어졌습니다: " + cause.getMessage());
    }

    /**
     * 구독한 토픽에서 메시지가 도착했을 때 호출되는 콜백 메서드.
     * 메시지 내용을 파싱하고, 내용에 따라 적절한 알림을 생성합니다.
     * @param topic 메시지가 발행된 토픽
     * @param message 수신된 MQTT 메시지 객체
     * @throws Exception 메시지 처리 중 발생할 수 있는 예외
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        logger.info("Message Arrived. Topic: {}, Message: {}", topic, payload);

        if (topic.equals(scriptStatusTopic)) {
            // 감지 모듈 상태 토픽 처리 (apple_defect/controller_status)
            currentScriptStatus = payload; // 스크립트 상태 업데이트
            if (payload.contains("Already") && payload.contains("Script")) {
                notificationService.saveNotification(Notification.NotificationType.DEFECT_MODULE, "불량 감지 모듈", "불량 감지 모듈이 이미 실행중입니다.");
            } else if (payload.contains("Stop") && payload.contains("Force")) {
                notificationService.saveNotification(Notification.NotificationType.DEFECT_MODULE, "불량 감지 모듈", "불량 감지 모듈이 강제중지되었습니다.");
            } else if (!payload.contains("Script Started") && !payload.contains("Script Stopped")){
                logger.warn("Unknown defect module status message: {}", payload);
                notificationService.saveNotification(Notification.NotificationType.WARNING, "불량 감지 모듈", "알 수 없는 불량 감지 모듈 상태 메시지: " + payload);
            }
        } else if (topic.equals(conveyorStatusTopic)) {
            // 컨베이어 벨트 상태 토픽 처리 (control_panel/system_status)
            if (payload.contains("running") && payload.contains("already")) {
                notificationService.saveNotification(Notification.NotificationType.CONVEYOR_BELT, "컨베이어 벨트", "컨베이어 벨트가 이미 가동 중입니다.");
            } else if (payload.contains("error")) {
                notificationService.saveNotification(Notification.NotificationType.CONVEYOR_BELT, "컨베이어 벨트", "컨베이어 작동중 오류가 발생했습니다. \n " + payload);
            } else if (!payload.contains("running") && !payload.contains("stopped")){
                logger.warn("Unknown conveyor belt status message: {}", payload);
                notificationService.saveNotification(Notification.NotificationType.WARNING, "컨베이어 벨트", "알 수 없는 컨베이어 벨트 상태 메시지: " + payload);
            }
        } else if (topic.equals(detectResultTopic)) { // factory/detect_result 토픽 처리
            try {
                JsonNode jsonNode = objectMapper.readTree(payload);
                String status = jsonNode.has("status") ? jsonNode.get("status").asText() : "UNKNOWN";
                String timestamp = jsonNode.has("timestamp") ? jsonNode.get("timestamp").asText() : "UNKNOWN";
                int defectCount = jsonNode.has("defectCount") ? jsonNode.get("defectCount").asInt() : 0;

                logger.info("Defect Detection Message Arrived: Status={}, DefectCount={}, Timestamp={}", status, defectCount, timestamp);

                if ("Defective".equalsIgnoreCase(status)) {
                    String notificationMessage = String.format(
                        "불량 제품이 감지되었습니다. 불량 개수: %d개, 감지 시간: %s",
                        defectCount,
                        timestamp
                    );
                    notificationService.saveNotification(Notification.NotificationType.DEFECT_DETECTED, "불량 감지 결과", notificationMessage);
                } else if ("Substandard".equalsIgnoreCase(status)) {
                    String notificationMessage = String.format(
                        "비상품 제품이 감지되었습니다. 감지 시간: %s",
                        defectCount,
                        timestamp
                    );
                    notificationService.saveNotification(Notification.NotificationType.SUCCESS, "정상 감지 결과", notificationMessage);
                } else if (!"Normal".equalsIgnoreCase(status)) {
                    logger.warn("Unknown defect detection status: {}", status);
                    notificationService.saveNotification(Notification.NotificationType.WARNING, "알 수 없는 감지 결과", "알 수 없는 감지 결과 상태: " + status);
                }

            } catch (Exception e) {
                logger.error("Error while parsing defect detecion JSON data: {}", e.getMessage(), e);
                notificationService.saveNotification(Notification.NotificationType.ERROR, "JSON 파싱 오류", "불량 감지 결과 JSON 파싱 중 오류 발생: " + e.getMessage());
            }
        } else if (topic.equals(defectDetailsTopic)) { // defect_detection/details 토픽 처리
            try {
                // 전체 페이로드를 DefectDetectionDetailsDto 객체로 파싱
                DefectDetectionDetailsDto detailsDto = objectMapper.readValue(payload, DefectDetectionDetailsDto.class);

                logger.info("Defect detail message arrived (DTO parsing): Status={}, DefectCount={}, DefectSummary={}",
                            detailsDto.getStatus(), detailsDto.getDefectCount(), detailsDto.getDefectSummary());

                // defect_detection/details 토픽의 페이로드에 defects 리스트가 없는 경우를 고려하여
                // defectSummary와 defectCount를 활용한 알림 생성
                String notificationTitle = "불량 상세 정보";
                String notificationMessage = String.format(
                    "상태: %s, 감지 불량 개수: %d개, 요약: %s",
                    detailsDto.getStatus(),
                    detailsDto.getDefectCount(),
                    detailsDto.getDefectSummary() != null && !detailsDto.getDefectSummary().isEmpty() ? detailsDto.getDefectSummary() : "상세 불량 정보 없음"
                );

                // 알림 메시지가 너무 길어지지 않도록 길이 제한 (예: 500자)
                if (notificationMessage.length() > 500) {
                    notificationMessage = notificationMessage.substring(0, 497) + "...";
                }

                notificationService.saveNotification(Notification.NotificationType.DEFECT_DETECTED, notificationTitle, notificationMessage);

                // 만약 향후 defects 리스트가 다시 추가될 경우를 대비한 로깅 (현재 페이로드에는 없음)
                // if (detailsDto.getDefects() != null && !detailsDto.getDefects().isEmpty()) {
                //     logger.debug("Defects 리스트에 상세 불량 정보가 포함되어 있습니다. (이 알림은 현재 페이로드 구조와 다름)");
                //     for (DefectInfo defect : detailsDto.getDefects()) {
                //         logger.debug(" - 상세 불량: {} (원인: {})", defect.getClazz(), defect.getDetailedReason());
                //     }
                // }

            } catch (Exception e) {
                logger.error("Error while parsing defect detail JSON data: {}", e.getMessage(), e);
                notificationService.saveNotification(Notification.NotificationType.ERROR, "JSON 파싱 오류", "불량 상세 정보 JSON 파싱 중 오류 발생: " + e.getMessage());
            }
        } else {
            logger.warn("Message arrived from unknown topic : Topic={}, Message={}", topic, payload);
            notificationService.saveNotification(Notification.NotificationType.WARNING, "알 수 없는 토픽", "알 수 없는 토픽에서 메시지 수신: " + topic);
        }
    }

    /**
     * 발행한 메시지가 브로커에 전달 완료되었을 때 호출되는 콜백 메서드 (QoS > 0인 경우).
     * 구독자 역할에서는 주로 발행자에서 사용됩니다.
     * @param token 메시지 전달 토큰
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        logger.debug("메시지 전달 완료: {}", token.getMessageId());
    }

    public String getCurrentScriptStatus(){
        return currentScriptStatus;
    }
}
