package com.company.warehouse.udp;

import com.company.common.event.SensorEvent;
import com.company.common.model.SensorReading;
import com.company.common.model.SensorType;
import com.company.common.model.MeasurementUnit;
import com.company.warehouse.kafka.KafkaProducerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class UdpSensorListener implements Runnable {

    private final int port;
    private final SensorType sensorType;
    private final Producer<String, String> producer;
    private final ObjectMapper mapper = new ObjectMapper();

    public UdpSensorListener(int port, SensorType sensorType) {
        this.port = port;
        this.sensorType = sensorType;
        this.producer = KafkaProducerFactory.create();
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[1024];

            System.out.println("📡 Listening on UDP port " + port);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String payload = new String(
                        packet.getData(),
                        0,
                        packet.getLength(),
                        StandardCharsets.UTF_8
                );

                double value = Double.parseDouble(payload);

                SensorReading reading = new SensorReading(
                        "sensor-" + port,
                        value,
                        sensorType,
                        MeasurementUnit.CELSIUS,
                        Instant.now()
                );

                SensorEvent event = new SensorEvent(reading);

                String json = mapper.writeValueAsString(event);

                producer.send(new ProducerRecord<>("sensor-events", json));

                System.out.println("➡️ Sent to Kafka: " + json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
