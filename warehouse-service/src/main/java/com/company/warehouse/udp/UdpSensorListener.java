package com.company.warehouse.udp;

import com.company.common.bus.EventBus;
import com.company.common.event.SensorEvent;
import com.company.common.model.MeasurementUnit;
import com.company.common.model.SensorReading;
import com.company.common.model.SensorType;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class UdpSensorListener implements Runnable {

    private final int port;
    private final SensorType sensorType;
    private final EventBus eventBus;

    public UdpSensorListener(int port, SensorType sensorType, EventBus eventBus) {
        this.port = port;
        this.sensorType = sensorType;
        this.eventBus = eventBus;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Listening on UDP port " + port +
                    " for " + sensorType + " data");

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(
                        packet.getData(),
                        0,
                        packet.getLength(),
                        StandardCharsets.UTF_8
                );

                // Example: sensor_id=t1; value=40
                String[] parts = message.split(";");
                String sensorId = parts[0].split("=")[1].trim();
                double value = Double.parseDouble(parts[1].split("=")[1].trim());

                MeasurementUnit unit =
                        sensorType == SensorType.TEMPERATURE
                                ? MeasurementUnit.CELSIUS
                                : MeasurementUnit.PERCENT;

                SensorReading reading = new SensorReading(
                        sensorId,
                        value,
                        sensorType,
                        unit,
                        Instant.now()
                );

                SensorEvent event = new SensorEvent(reading);
                eventBus.publish(event);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
