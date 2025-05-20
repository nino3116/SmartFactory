package com.project2.smartfactory.control_panel;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // 올바른 import
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import lombok.NonNull;

public class WebSocketHandler extends TextWebSocketHandler{

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    private static final String RASPBERRY_PI_IP = "192.168.0.125"; // 라즈베리 파이 IP 주소
    private static final String RASPBERRY_PI_USER = "pi"; // 라즈베리 파이 사용자 이름
    private static final String PYTHON_SCRIPT_PATH = "/home/nino/control_panel.py"; // Python 스크립트 경로

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) throws Exception {
        if (session == null) {
            throw new IllegalArgumentException("WebSocketSession은 null일 수 없습니다.");
        }
        if (message == null) {
            throw new IllegalArgumentException("TextMessage는 null일 수 없습니다.");
        }
        String payload = message.getPayload();
        logger.info("받은 메시지: {}", payload);
        // Raspberry Pi 제어 로직 호출
        controlRaspberryPi(payload);
        session.sendMessage(new TextMessage("OK"));
    }

    private void controlRaspberryPi(String command) {
        // Raspberry Pi 제어 로직 (예: SSH를 통해 원격으로 명령 실행)
        try {
            String[] cmdArray = {"ssh", RASPBERRY_PI_USER + "@" + RASPBERRY_PI_IP, "python", PYTHON_SCRIPT_PATH, command};
            ProcessBuilder processBuilder = new ProcessBuilder(cmdArray);
            Process process = processBuilder.start();
            process.waitFor();
            logger.info("명령이 성공적으로 실행되었습니다.");
        } catch (IOException | InterruptedException e) {
            logger.error("명령 실행 오류: {}", e.getMessage());
        }
    }
}
