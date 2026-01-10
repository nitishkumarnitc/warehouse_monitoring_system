package com.company.monitoring.consumer;

import com.company.common.event.SensorEvent;
import com.company.monitoring.rules.ThresholdEvaluator;

public class SensorEventConsumer {

    private final ThresholdEvaluator evaluator;

    public SensorEventConsumer(ThresholdEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void consume(SensorEvent event) {
        evaluator.evaluate(event);
    }
}
