package com.company.monitoring.alarm;

import com.company.common.event.SensorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlarmService {

    private static final Logger log =
            LoggerFactory.getLogger(AlarmService.class);

    public void raiseAlarm(String message, SensorEvent event) {
        log.error("🚨 ALARM: {} | Event={}", message, event);
    }
}
