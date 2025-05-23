package com.project2.smartfactory.mqtt;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.smartfactory.defect.DefectInfo;
import com.project2.smartfactory.defect.DetectionLogService; // DetectionLogService 임포트

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service // Spring Bean으로 등록
public class MqttSubscriberService implements MqttCallback { // 클래스 이름을 MqttSubscriberService로 변경

    private static final Logger logger = LoggerFactory.getLogger(MqttSubscriberService.class); // 로거 인스턴스 생성

    // application.properties 또는 application.yml 파일에서 설정 값을 주입받습니다.
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id.subscriber") // 통합된 클라이언트 ID (고유해야 함)
    private String clientId;

    @Value("${mqtt.topic.status}") // 불량 감지 상태 토픽
    private String defectStatusTopic;

    @Value("${mqtt.topic.details}") // 불량 감지 상세 정보 토픽
    private String defectDetailsTopic;

    @Value("${mqtt.topic.script.status}") // Python 스크립트 상태 토픽
    private String scriptStatusTopic;

    @Value("${mqtt.topic.system.status}") // 시스템 상태 토픽
    private String systemStatusTopic;

    private MqttClient mqttClient;
    private ObjectMapper objectMapper = new ObjectMapper(); // JSON 파싱을 위한 객체

    // 스크립트의 현재 상태를 저장할 변수 (기본값 설정)
    private String currentScriptStatus = "Unknown";
    private String currentSystemStatus = "Unknown";

    // 재연결 시도 관련 상수
    private static final int MAX_RECONNECT_ATTEMPTS = 5; // 최대 재연결 시도 횟수
    private static final long RECONNECT_DELAY_SECONDS = 5; // 재연결 시도 간 지연 시간 (초)

    // DetectionLogService 주입 (불량 상세 정보를 DB에 저장하기 위함)
    private final DetectionLogService detectionLogService;

    // 생성자 주입을 통해 DetectionLogService를 받습니다.
    public MqttSubscriberService(DetectionLogService detectionLogService) {
        this.detectionLogService = detectionLogService;
    }

    @PostConstruct // Spring 애플리케이션 시작 시 실행
    public void init() {
        connectAndSubscribe();
    }

    /**
     * MQTT 브로커에 연결하고 모든 필요한 토픽을 구독하는 메서드.
     * 재연결 로직에서 호출될 수 있도록 별도 메서드로 분리.
     */
    private void connectAndSubscribe() {
        try {
            // mqttClient가 null이거나 연결되어 있지 않은 경우에만 새로 연결 시도
            if (mqttClient == null || !mqttClient.isConnected()) {
                MemoryPersistence persistence = new MemoryPersistence();
                mqttClient = new MqttClient(brokerUrl, clientId, persistence);
                mqttClient.setCallback(this); // 메시지 수신 시 호출될 콜백 설정
            }

            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true); // 클라이언트 연결 해제 시 구독 정보 삭제
            connectOptions.setAutomaticReconnect(false); // 수동 재연결 로직을 위해 자동 재연결 비활성화

            logger.info("Connecting to MQTT Broker: {}", brokerUrl);
            mqttClient.connect(connectOptions);
            logger.info("Connected to MQTT Broker");

            // 모든 토픽을 한 번에 구독
            String[] topics = {defectStatusTopic, defectDetailsTopic, scriptStatusTopic, systemStatusTopic};
            int[] qos = {1, 1, 1, 1}; // 각 토픽에 대한 QoS 레벨 (예: 1)
            mqttClient.subscribe(topics, qos);

            logger.info("Subscribed to topics: {}, {}, {}", defectStatusTopic, defectDetailsTopic, scriptStatusTopic);

        } catch (MqttException e) {
            logger.error("MQTT 연결 또는 구독 중 오류 발생: {}", e.getMessage(), e);
            // 오류 발생 시 클라이언트 객체를 null로 설정하여 재연결 시도 시 새로운 클라이언트 생성 유도
            mqttClient = null;
        } catch (Exception e) {
            logger.error("MQTT 초기화 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            mqttClient = null;
        }
    }

    @PreDestroy // Spring 애플리케이션 종료 시 실행
    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                logger.info("Disconnected from MQTT Broker");
            }
        } catch (MqttException e) {
            logger.error("MQTT 브로커 연결 해제 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 현재 스크립트 상태를 반환합니다.
     * @return 현재 스크립트 상태 문자열
     */
    public String getCurrentScriptStatus() {
        // MQTT 클라이언트의 연결 상태에 따라 메시지를 보강
        if (mqttClient != null && mqttClient.isConnected()) {
            return currentScriptStatus;
        } else {
            return "MQTT Disconnected / " + currentScriptStatus; // 연결 끊김 상태도 함께 표시
        }
    }

    public String getCurrentSystemStatus() {
        // MQTT 클라이언트의 연결 상태에 따라 메시지를 보강
        if (mqttClient != null && mqttClient.isConnected()) {
            return currentSystemStatus;
        } else {
            return "MQTT Disconnected / " + currentSystemStatus; // 연결 끊김 상태도 함께 표시
        }
    }

    // --- MqttCallback 인터페이스 메소드 구현 ---

    @Override
    public void connectionLost(Throwable cause) {
        // MQTT 연결이 끊어졌을 때 호출됩니다. 재연결 로직을 여기에 구현합니다.
        logger.error("MQTT Connection lost: {}", cause.getMessage(), cause);

        int attempt = 0;
        // 클라이언트가 연결되어 있지 않고, 최대 재연결 시도 횟수 미만일 때 재연결 시도
        while (mqttClient != null && !mqttClient.isConnected() && attempt < MAX_RECONNECT_ATTEMPTS) {
            attempt++;
            logger.info("Attempting to reconnect to MQTT Broker (Attempt {}/{})", attempt, MAX_RECONNECT_ATTEMPTS);
            try {
                // 연결 재시도
                connectAndSubscribe();
                if (mqttClient.isConnected()) {
                    logger.info("Successfully reconnected to MQTT Broker on attempt {}", attempt);
                    return; // 재연결 성공 시 루프 종료
                }
            } catch (Exception e) {
                logger.error("Reconnection attempt {} failed: {}", attempt, e.getMessage());
            }

            try {
                // 재연결 시도 간 지연
                TimeUnit.SECONDS.sleep(RECONNECT_DELAY_SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                logger.warn("Reconnection delay interrupted.", ie);
                break; // 인터럽트 발생 시 재연결 시도 중단
            }
        }

        if (mqttClient != null && !mqttClient.isConnected()) {
            logger.error("Failed to reconnect to MQTT Broker after {} attempts. Further manual intervention may be required.", MAX_RECONNECT_ATTEMPTS);
            currentScriptStatus = "Connection Lost: Reconnect Failed"; // 연결 실패 상태 업데이트
            // TODO: 재연결 실패 시 추가적인 알림 (예: 관리자에게 이메일, 시스템 경고) 로직 구현
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // 구독한 토픽으로 메시지가 도착했을 때 호출됩니다.
        String payload = new String(message.getPayload());
        logger.debug("Message Arrived - Topic: {}, Payload: {}", topic, payload);

        // 토픽에 따라 메시지 처리 로직 분기
        if (topic.equals(scriptStatusTopic)) {
            // Python 스크립트 상태 메시지 처리
            currentScriptStatus = payload;
            logger.info("Script Status: {}", currentScriptStatus);
        } else if (topic.equals(systemStatusTopic)) {
            // Python 스크립트 상태 메시지 처리
            currentSystemStatus = payload;
            logger.info("System Status: {}", currentSystemStatus);
        } else if (topic.equals(defectStatusTopic)) {
            // 불량 감지 상태 메시지 처리 (예: UI 업데이트, 로그 기록 등)
            logger.info("Defect Status: {}", payload);
        } else if (topic.equals(defectDetailsTopic)) {
            // 불량 상세 정보 메시지 처리 (JSON 파싱)
            try {
                // JSON 문자열을 List<DefectInfo> 객체로 파싱
                List<DefectInfo> defectDetails = Arrays.asList(objectMapper.readValue(payload, DefectInfo[].class));

                logger.info("Defect Details Received. Saving to DB...");
                // DetectionLogService를 사용하여 DB에 저장
                // detectionLogService.saveDetectionLogs(defectDetails); // TODO: 이 메서드는 DetectionLogService에 구현되어야 합니다.
                // 현재 DetectionLogService에는 saveDetectionLogs 메서드가 없습니다.
                // 필요하다면 DetectionLogService에 List<DefectInfo>를 받아 처리하는 메서드를 추가해야 합니다.
                // 예: detectionLogService.saveDetectionLogs(defectDetails);
                logger.info("Successfully processed {} defect details.", defectDetails.size());

            } catch (Exception e) {
                logger.error("불량 상세 정보 JSON 파싱 중 오류 발생: {}", e.getMessage(), e);
            }
        } else {
            logger.warn("Unknown topic received: {}", topic);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 발행한 메시지가 브로커에 전달 완료되었을 때 호출됩니다 (QoS > 0인 경우).
        // 이 서비스는 주로 구독자 역할을 하므로, 이 메서드는 일반적으로 비어있습니다.
        // logger.debug("Delivery complete for message: {}", token.getMessageId());
    }
}
