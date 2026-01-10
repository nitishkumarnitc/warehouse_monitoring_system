package com.company.warehouse.kafka;

import com.company.common.event.SensorEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class SensorEventProducer {

    private final Producer<String, String> producer;
    private final ObjectMapper mapper = new ObjectMapper();

    public SensorEventProducer() {
        this.producer = KafkaProducerFactory.create();
    }

    public void send(SensorEvent event) {
        try {
            String payload = mapper.writeValueAsString(event);
            producer.send(new ProducerRecord<>("sensor-events", payload));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
