package com.company.warehouse.kafka;

import com.company.common.config.ApplicationConfig;
import com.company.common.config.ServiceConfig;
import com.company.common.event.SensorEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class SensorEventProducer {

    private final Producer<String, String> producer;
    private final ObjectMapper mapper = new ObjectMapper();

    public SensorEventProducer() {
        this(new ServiceConfig(new ApplicationConfig()));
    }

    public SensorEventProducer(ServiceConfig config) {
        this.producer = KafkaProducerFactory.create(config);
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
