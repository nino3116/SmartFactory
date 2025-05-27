package com.project2.smartfactory.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class MqttPublisherService {

    // application.properties 또는 application.yml에서 MQTT 브로커 주소 주입
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    // application.properties 또는 application.yml에서 클라이언트 ID 주입
    @Value("${mqtt.client.id.publisher}")
    private String clientId;

    private MqttClient mqttClient;

    @Value("${mqtt.topic.system.command}")
    private String systemCommandTopic;
    @Value("${mqtt.topic.script.command}")
    private String scriptCommandTopic;


    // 서비스 초기화 시 MQTT 클라이언트 연결
    @PostConstruct
    public void init() {
        try {
            // 메모리 기반의 Persistence 사용 (메시지 저장 방식)
            MemoryPersistence persistence = new MemoryPersistence();

            // MQTT 클라이언트 생성
            mqttClient = new MqttClient(brokerUrl, clientId, persistence);

            // 연결 옵션 설정
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true); // 클린 세션 사용 (클라이언트 연결 종료 시 구독 정보 및 메시지 삭제)
            // 필요에 따라 사용자 이름, 비밀번호, Last Will and Testament(LWT) 등 추가 설정 가능
            // connOpts.setUserName("username");
            // connOpts.setPassword("password".toCharArray());
            // connOpts.setWill("lwt/topic", "disconnected".getBytes(), 1, true); // LWT 설정

            System.out.println("MQTT 브로커 연결 시도: " + brokerUrl);
            mqttClient.connect(connOpts);
            System.out.println("MQTT 브로커 연결 성공");

            this.publishMessage(systemCommandTopic, "status_request", 2, false);
            this.publishMessage(scriptCommandTopic, "status_request", 2, false);

        } catch (MqttException me) {
            System.err.println("MQTT 연결 오류: " + me.getMessage());
            System.err.println("reason " + me.getReasonCode());
            System.err.println("msg " + me.getMessage());
            System.err.println("loc " + me.getLocalizedMessage());
            System.err.println("cause " + me.getCause());
            System.err.println("excep " + me);
            // 오류 발생 시 클라이언트 객체를 null로 설정하거나 상태를 표시하여 발행 시 오류 처리
            mqttClient = null; // 연결 실패 시 클라이언트를 null로 설정
        } catch (Exception e) {
            System.err.println("MQTT 클라이언트 초기화 중 예상치 못한 오류 발생: " + e.getMessage());
            mqttClient = null;
        }
    }

    // 서비스 종료 시 MQTT 클라이언트 연결 해제
    @PreDestroy
    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                System.out.println("MQTT 브로커 연결 해제");
            } catch (MqttException me) {
                System.err.println("MQTT 연결 해제 오류: " + me.getMessage());
            }
        }
    }

    /**
     * 지정된 토픽으로 MQTT 메시지를 발행합니다.
     * @param topic 발행할 토픽
     * @param payload 발행할 메시지 내용 (문자열)
     * @param qos QoS 레벨 (0, 1, 2)
     * @param retained 메시지 유지 여부
     */
    public void publishMessage(String topic, String payload, int qos, boolean retained) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            System.err.println("MQTT 클라이언트가 연결되지 않았습니다. 메시지를 발행할 수 없습니다.");
            // 필요에 따라 예외를 던지거나 다른 방식으로 오류 처리
            this.init();
        }

        try {
            // 메시지 생성
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setRetained(retained);
            message.setQos(qos);

            // 메시지 발행
            mqttClient.publish(topic, message);
            System.out.println(String.format("MQTT 메시지 발행 성공: 토픽='%s', 메시지='%s'", topic, payload));

        } catch (MqttException me) {
            System.err.println("MQTT 메시지 발행 오류: " + me.getMessage());
            System.err.println("reason " + me.getReasonCode());
            System.err.println("msg " + me.getMessage());
            System.err.println("loc " + me.getLocalizedMessage());
            System.err.println("cause " + me.getCause());
            System.err.println("excep " + me);
        } catch (Exception e) {
        System.err.println("MQTT 메시지 발행 중 예상치 못한 오류 발생: " + e.getMessage());
        }
    }


}
