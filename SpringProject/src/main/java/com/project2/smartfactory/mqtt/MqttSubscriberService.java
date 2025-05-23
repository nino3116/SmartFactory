package com.project2.smartfactory.mqtt;

import java.util.Arrays;
import java.util.List;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.smartfactory.defect.DefectInfo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class MqttSubscriberService implements MqttCallback {


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




    @PostConstruct // Spring 애플리케이션 시작 시 실행
    public void connectAndSubscribe() {
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            mqttClient = new MqttClient(brokerUrl, clientId, persistence);

            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true); // 클라이언트 연결 해제 시 구독 정보 삭제

            System.out.println("Connecting to MQTT Broker: " + brokerUrl);
            mqttClient.connect(connectOptions);
            System.out.println("Connected to MQTT Broker");

            mqttClient.setCallback(this); // 메시지 수신 시 호출될 콜백 설정

            // 토픽 구독
            // QoS 레벨은 발행 시 사용한 QoS와 같거나 높게 설정하는 것이 좋습니다.
            mqttClient.subscribe(statusTopic, 1);
            mqttClient.subscribe(detailsTopic, 1);
            System.out.println("Subscribed to topics: " + statusTopic + ", " + detailsTopic);

        } catch (Exception e) {
            System.err.println("Error connecting to MQTT Broker or subscribing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @PreDestroy // Spring 애플리케이션 종료 시 실행
    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                System.out.println("Disconnected from MQTT Broker");
            }
        } catch (Exception e) {
            System.err.println("Error disconnecting from MQTT Broker: " + e.getMessage());
        }
    }

    // --- MqttCallback 인터페이스 메소드 구현 ---

    @Override
    public void connectionLost(Throwable cause) {
        // MQTT 연결이 끊어졌을 때 호출됩니다. 재연결 로직 등을 여기에 구현할 수 있습니다.
        System.err.println("MQTT Connection lost: " + cause.getMessage());
        cause.printStackTrace();
        // TODO: 재연결 로직 구현
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // 구독한 토픽으로 메시지가 도착했을 때 호출됩니다.
        String payload = new String(message.getPayload());
        System.out.println("Message Arrived - Topic: " + topic + ", Payload: " + payload);

        // 토픽에 따라 메시지 처리 로직 분기
        if (topic.equals(statusTopic)) {
            // 상태 메시지 처리 (예: UI 업데이트, 로그 기록 등)
            System.out.println("Defect Status: " + payload);

        } else if (topic.equals(detailsTopic)) {
            // 상세 정보 메시지 처리 (JSON 파싱)
            try {
                // JSON 문자열을 List<DefectInfo> 객체로 파싱
                List<DefectInfo> defectDetails = Arrays.asList(objectMapper.readValue(payload, DefectInfo[].class));

                System.out.println("Defect Details Received:");
                for (DefectInfo defect : defectDetails) {
                    System.out.println(" - Class: " + defect.getClazz() +
                                    ", Reason: " + defect.getReason() +
                                    ", Detailed Reason: " + defect.getDetailedReason() +
                                    ", Confidence: " + defect.getConfidence() +
                                    ", Box Location: " + defect.getBox()

                    );
                    // TODO: 파싱된 불량 정보를 데이터베이스에 저장, 웹 페이지에 표시 등 후처리 로직 구현
                }

            } catch (Exception e) {
                System.err.println("Error parsing defect details JSON: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 발행한 메시지가 브로커에 전달 완료되었을 때 호출됩니다 (QoS > 0인 경우).
        // 여기서는 구독자 역할이므로 주로 발행자에서 사용됩니다.
        // System.out.println("Delivery complete for message: " + token.getMessageId());
    }

    
    

}
