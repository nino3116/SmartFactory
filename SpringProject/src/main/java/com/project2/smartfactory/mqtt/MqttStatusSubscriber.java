package com.project2.smartfactory.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class MqttStatusSubscriber {

    // application.properties 또는 application.yml에서 MQTT 브로커 주소 주입
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    // application.properties 또는 application.yml에서 클라이언트 ID 주입
    @Value("${mqtt.client.id.subscriber}")
    private String clientId;

    // 구독할 토픽 (controller.py에서 상태를 발행하는 토픽과 일치해야 함)
    @Value("${mqtt.topic.script.status}")
    private String statusTopic;

    private MqttClient mqttClient;

    // 스크립트의 현재 상태를 저장할 변수 (기본값 설정)
    private String currentScriptStatus = "Unknown"; // 초기 상태

    // 컴포넌트 초기화 시 MQTT 클라이언트 연결 및 토픽 구독
    @PostConstruct
    public void init() {
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            mqttClient = new MqttClient(brokerUrl, clientId, persistence);

            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            // 필요에 따라 사용자 이름, 비밀번호 등 설정

            System.out.println("MQTT 브로커 연결 시도 (Subscriber): " + brokerUrl);
            mqttClient.connect(connOpts);
            System.out.println("MQTT 브로커 연결 성공 (Subscriber)");

            // 토픽 구독 및 메시지 리스너 설정
            // IMqttMessageListener는 메시지 수신 시 실행될 콜백 함수를 정의합니다.
            mqttClient.subscribe(statusTopic, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String statusMessage = new String(message.getPayload());
                    System.out.println(String.format("MQTT 상태 메시지 수신: 토픽='%s', 메시지='%s'", topic, statusMessage));
                    // 수신된 상태 메시지를 전역 변수에 저장
                    currentScriptStatus = statusMessage;
                }
            });

            System.out.println(String.format("토픽 구독 (Subscriber): %s", statusTopic));

        } catch (MqttException me) {
            System.err.println("MQTT Subscriber 연결/구독 오류: " + me.getMessage());
            // 오류 발생 시 클라이언트 객체를 null로 설정하거나 상태를 표시하여 구독 실패 알림
            mqttClient = null;
            currentScriptStatus = "Subscription Failed"; // 구독 실패 상태 표시
        } catch (Exception e) {
             System.err.println("MQTT Subscriber 초기화 중 예상치 못한 오류 발생: " + e.getMessage());
             mqttClient = null;
             currentScriptStatus = "Initialization Error"; // 초기화 오류 상태 표시
        }
    }

    // 컴포넌트 종료 시 MQTT 클라이언트 연결 해제
    @PreDestroy
    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                System.out.println("MQTT 브로커 연결 해제 (Subscriber)");
            } catch (MqttException me) {
                System.err.println("MQTT Subscriber 연결 해제 오류: " + me.getMessage());
            }
        }
    }

    /**
     * 현재 스크립트 상태를 반환합니다.
     * @return 현재 스크립트 상태 문자열
     */
    public String getCurrentScriptStatus() {
        return currentScriptStatus;
    }
}
