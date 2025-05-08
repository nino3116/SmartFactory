package com.project2.smartfactory.defect;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DefectInfo {

    // Json 키 이름과 Java 필드 이름이 다를경우 @JsonProperty로 맵핑
    // class는 Java키워드이므로 'clazz'로 변경후 맵핑
    @JsonProperty("class")
    private String clazz;

    private double confidence;  // 감지 신뢰도

    private String reason;  // 불량 판정 사유

    private List<Double> box;   // box 좌표 리스트

    // 'unriped' 불량의 경우 사과 면적대비 비율
    @JsonProperty("area_percent_on_apple")
    private Double areaPercentOnApple;  // Double 사용으로 Null 가능하게

    // Jackson 라이브러리가 JSON 객체로 변환할 떄 사용할 기본 생성자는 필수
    public DefectInfo() {}

    // toString() 메서드 오버라이드 (디버깅용)
    @Override
    public String toString() {
        return "DefectInfo [clazz=" + clazz + ", confidence=" + confidence + ", reason=" + reason + ", box=" + box
                + ", areaPercentOnApple=" + areaPercentOnApple + "]";
    }
    

}
