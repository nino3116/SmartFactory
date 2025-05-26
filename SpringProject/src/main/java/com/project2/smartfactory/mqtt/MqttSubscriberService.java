// src/main/java/com/project2/smartfactory/mqtt/MqttSubscriberService.java
package com.project2.smartfactory.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.smartfactory.defect.DefectInfo;
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
import java.util.concurrent.TimeUnit;

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
    private String defectdDetailsTopic;

    @Value("${mqtt.topic.script.status}") // Python 스크립트 상태 토픽
    private String scriptStatusTopic;

    @Value("${mqtt.topic.conveyor.status}")   // 컨베이어벨트 동작 시스템 상태 토픽
    private String conveyorStatusTopic;

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
            mqttClient.subscribe(defectdDetailsTopic, 1); // 불량 감지 상세 정보 토픽 구독
            logger.info("Connected to MQTT broker and subscribed to topics: [{}, {}, {}]", scriptStatusTopic, conveyorStatusTopic, defectdDetailsTopic);

            // MQTT 연결 성공 알림
            notificationService.saveNotification(Notification.NotificationType.MQTT_CLIENT, "MQTT 연결", "MQTT 브로커에 성공적으로 연결되었습니다.");

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
                logger.info("MQTT 브로커 연결 해제 (Subscriber)");
                // MQTT 연결 해제 알림
                notificationService.saveNotification(Notification.NotificationType.MQTT_CLIENT, "MQTT 연결 해제", "MQTT 브로커 연결이 해제되었습니다.");
            } catch (MqttException me) {
                logger.error("MQTT 연결 해제 오류: {}", me.getMessage(), me);
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
                notificationService.saveNotification(Notification.NotificationType.DEFECT_MODULE, "불량 감지 모듈", "불량 감지 모듈이 강제정지되었습니다.");
            } else {
                logger.warn("Unknown defect module status message: {}", payload);
                notificationService.saveNotification(Notification.NotificationType.WARNING, "불량 감지 모듈", "알 수 없는 불량 감지 모듈 상태 메시지: " + payload);
            }
        } else if (topic.equals(conveyorStatusTopic)) {
            // 컨베이어 벨트 상태 토픽 처리 (control_panel/system_status)
            if (payload.contains("running")) {
                notificationService.saveNotification(Notification.NotificationType.CONVEYOR_BELT, "컨베이어 벨트", "컨베이어 벨트 동작이 시작되었습니다.");
            } else if (payload.contains("stopped")) {
                notificationService.saveNotification(Notification.NotificationType.CONVEYOR_BELT, "컨베이어 벨트", "컨베이어 벨트 동작이 정지되었습니다.");
            } else {
                logger.warn("Unknown conveyor belt status message: {}", payload);
                notificationService.saveNotification(Notification.NotificationType.WARNING, "컨베이어 벨트", "알 수 없는 컨베이어 벨트 상태 메시지: " + payload);
            }
        } else if (topic.equals(defectdDetailsTopic)) {
            // 불량 감지 상세 정보 토픽 처리
            try {
                List<DefectInfo> defectDetails = Arrays.asList(objectMapper.readValue(payload, DefectInfo[].class));

                logger.info("불량 상세 정보 수신됨:");
                if (defectDetails.isEmpty()) {
                    // 불량 정보가 비어있다면 정상 감지로 간주하고 알림 저장
                    notificationService.saveNotification(Notification.NotificationType.SUCCESS, "정상 감지", "생산 라인에서 정상 제품이 감지되었습니다.");
                } else {
                    for (DefectInfo defect : defectDetails) {
                        logger.info(" - 클래스: {}, 원인: {}, 상세 원인: {}, 신뢰도: {}, 박스 위치: {}",
                                defect.getClazz(),
                                defect.getReason(),
                                defect.getDetailedReason(),
                                defect.getConfidence(),
                                defect.getBox()
                        );
                        // 불량 정보를 알림으로 저장
                        String notificationMessage = String.format(
                            "불량 유형: %s, 원인: %s, 상세 원인: %s, 신뢰도: %.2f%%",
                            defect.getClazz(),
                            defect.getReason(),
                            defect.getDetailedReason(),
                            defect.getConfidence() * 100 // 백분율로 표시
                        );
                        notificationService.saveNotification(Notification.NotificationType.DEFECT_DETECTED, "불량 감지", notificationMessage);
                    }
                }
            } catch (Exception e) {
                logger.error("불량 상세 정보 JSON 파싱 중 오류 발생: {}", e.getMessage(), e);
                notificationService.saveNotification(Notification.NotificationType.ERROR, "JSON 파싱 오류", "불량 상세 정보 JSON 파싱 중 오류 발생: " + e.getMessage());
            }
        } else {
            logger.warn("알 수 없는 토픽에서 메시지 수신: 토픽={}, 메시지={}", topic, payload);
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
