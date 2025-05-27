package com.project2.smartfactory.task;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyTaskProgressDto {
    private LocalDate recordDate;
    private int dailyTotalTasks;
    private int completedTasks;

}
