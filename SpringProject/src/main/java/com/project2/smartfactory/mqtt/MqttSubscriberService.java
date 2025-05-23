package com.project2.smartfactory.mqtt;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit; // TimeUnit 임포트 추가

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException; // MqttException 임포트 추가
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger; // SLF4J Logger 임포트
import org.slf4j.LoggerFactory; // SLF4J LoggerFactory 임포트
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service; // @Service 어노테이션 추가

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.smartfactory.defect.DefectInfo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service // Spring Bean으로 등록
public class MqttSubscriberService implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(MqttSubscriberService.class); // 로거 인스턴스 생성

    // application.properties 또는 application.yml 파일에서 설정 값을 주입받습니다.
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id.subscriber}")
    private String clientId;

    @Value("${mqtt.topic.status}")
    private String statusTopic;

    @Value("${mqtt.topic.details}")
    private String detailsTopic;

    private MqttClient mqttClient;
    private ObjectMapper objectMapper = new ObjectMapper(); // JSON 파싱을 위한 객체

    // 재연결 시도 관련 상수
    private static final int MAX_RECONNECT_ATTEMPTS = 5; // 최대 재연결 시도 횟수
    private static final long RECONNECT_DELAY_SECONDS = 50000; // 재연결 시도 간 지연 시간 (초)

    @PostConstruct // Spring 애플리케이션 시작 시 실행
    public void init() {
        connectAndSubscribe();
    }

    /**
     * MQTT 브로커에 연결하고 토픽을 구독하는 메서드.
     * 재연결 로직에서 호출될 수 있도록 별도 메서드로 분리.
     */
    private void connectAndSubscribe() {
        try {
            if (mqttClient == null) {
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

            // 토픽 구독
            // QoS 레벨은 발행 시 사용한 QoS와 같거나 높게 설정하는 것이 좋습니다.
            mqttClient.subscribe(statusTopic, 1);
            mqttClient.subscribe(detailsTopic, 1);
            logger.info("Subscribed to topics: {}, {}", statusTopic, detailsTopic);

        } catch (MqttException e) {
            logger.error("MQTT 연결 또는 구독 중 오류 발생: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("MQTT 초기화 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
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

    // --- MqttCallback 인터페이스 메소드 구현 ---

    @Override
    public void connectionLost(Throwable cause) {
        // MQTT 연결이 끊어졌을 때 호출됩니다. 재연결 로직을 여기에 구현합니다.
        logger.error("MQTT Connection lost: {}", cause.getMessage(), cause);

        int attempt = 0;
        while (!mqttClient.isConnected() && attempt < MAX_RECONNECT_ATTEMPTS) {
            attempt++;
            logger.info("Attempting to reconnect to MQTT Broker (Attempt {}/{})", attempt, MAX_RECONNECT_ATTEMPTS);
            try {
                // 연결이 끊어졌으므로, 클라이언트 객체를 다시 연결 시도
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

        if (!mqttClient.isConnected()) {
            logger.error("Failed to reconnect to MQTT Broker after {} attempts. Further manual intervention may be required.", MAX_RECONNECT_ATTEMPTS);
            // TODO: 재연결 실패 시 추가적인 알림 (예: 관리자에게 이메일, 시스템 경고) 로직 구현
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // 구독한 토픽으로 메시지가 도착했을 때 호출됩니다.
        String payload = new String(message.getPayload());
        logger.debug("Message Arrived - Topic: {}, Payload: {}", topic, payload);

        // 토픽에 따라 메시지 처리 로직 분기
        if (topic.equals(statusTopic)) {
            // 상태 메시지 처리 (예: UI 업데이트, 로그 기록 등)
            logger.info("Defect Status: {}", payload);

        } else if (topic.equals(detailsTopic)) {
            // 상세 정보 메시지 처리 (JSON 파싱)
            try {
                // JSON 문자열을 List<DefectInfo> 객체로 파싱
                List<DefectInfo> defectDetails = Arrays.asList(objectMapper.readValue(payload, DefectInfo[].class));

                logger.info("Defect Details Received:");
                for (DefectInfo defect : defectDetails) {
                    logger.info(" - Class: {}, Reason: {}, Detailed Reason: {}, Confidence: {}, Box Location: {}",
                                       defect.getClazz(),
                                       defect.getReason(),
                                       defect.getDetailedReason(),
                                       defect.getConfidence(),
                                       defect.getBox()
                    );
                    // TODO: 파싱된 불량 정보를 데이터베이스에 저장, 웹 페이지에 표시 등 후처리 로직 구현
                }

            } catch (Exception e) {
                logger.error("불량 상세 정보 JSON 파싱 중 오류 발생: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 발행한 메시지가 브로커에 전달 완료되었을 때 호출됩니다 (QoS > 0인 경우).
        // 여기서는 구독자 역할이므로 주로 발행자에서 사용됩니다.
        // logger.debug("Delivery complete for message: {}", token.getMessageId());
    }
}
