package com.company.warehouse.udp;

import com.company.common.metrics.MetricsRegistry;
import com.company.common.model.SensorType;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UdpSensorListenerTest {

    private MockProducer<String, String> mockProducer;
    private UdpSensorListener listener;

    @BeforeEach
    void setUp() {
        MetricsRegistry.resetForTesting();
        mockProducer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
    }

    @AfterEach
    void tearDown() {
        MetricsRegistry.resetForTesting();
    }

    @Test
    void shouldProcessValidTemperatureReading() {
        listener = new UdpSensorListener(3344, SensorType.TEMPERATURE, mockProducer);

        byte[] data = "25.5".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length);

        listener.processPacket(packet);

        assertThat(mockProducer.history()).hasSize(1);
        ProducerRecord<String, String> record = mockProducer.history().get(0);
        assertThat(record.topic()).isEqualTo("sensor-events");
        assertThat(record.value()).contains("\"value\":25.5");
        assertThat(record.value()).contains("\"sensorId\":\"sensor-3344\"");
        assertThat(record.value()).contains("\"sensorType\":\"TEMPERATURE\"");
    }

    @Test
    void shouldProcessValidHumidityReading() {
        listener = new UdpSensorListener(3355, SensorType.HUMIDITY, mockProducer);

        byte[] data = "65.0".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length);

        listener.processPacket(packet);

        assertThat(mockProducer.history()).hasSize(1);
        ProducerRecord<String, String> record = mockProducer.history().get(0);
        assertThat(record.value()).contains("\"value\":65.0");
        assertThat(record.value()).contains("\"sensorId\":\"sensor-3355\"");
        assertThat(record.value()).contains("\"sensorType\":\"HUMIDITY\"");
    }

    @Test
    void shouldHandleNegativeValues() {
        listener = new UdpSensorListener(3344, SensorType.TEMPERATURE, mockProducer);

        byte[] data = "-10.5".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length);

        listener.processPacket(packet);

        assertThat(mockProducer.history()).hasSize(1);
        ProducerRecord<String, String> record = mockProducer.history().get(0);
        assertThat(record.value()).contains("\"value\":-10.5");
    }

    @Test
    void shouldHandleZeroValue() {
        listener = new UdpSensorListener(3344, SensorType.TEMPERATURE, mockProducer);

        byte[] data = "0.0".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length);

        listener.processPacket(packet);

        assertThat(mockProducer.history()).hasSize(1);
        ProducerRecord<String, String> record = mockProducer.history().get(0);
        assertThat(record.value()).contains("\"value\":0.0");
    }

    @Test
    void shouldHandleWhitespace() {
        listener = new UdpSensorListener(3344, SensorType.TEMPERATURE, mockProducer);

        byte[] data = "  25.5  \n".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length);

        listener.processPacket(packet);

        assertThat(mockProducer.history()).hasSize(1);
        ProducerRecord<String, String> record = mockProducer.history().get(0);
        assertThat(record.value()).contains("\"value\":25.5");
    }

    @Test
    void shouldFailOnInvalidNumericValue() {
        listener = new UdpSensorListener(3344, SensorType.TEMPERATURE, mockProducer);

        byte[] data = "invalid".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length);

        assertThatThrownBy(() -> listener.processPacket(packet))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid sensor");

        assertThat(mockProducer.history()).isEmpty();
    }

    @Test
    void shouldFailOnEmptyPayload() {
        listener = new UdpSensorListener(3344, SensorType.TEMPERATURE, mockProducer);

        byte[] data = "".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length);

        assertThatThrownBy(() -> listener.processPacket(packet))
                .isInstanceOf(RuntimeException.class);

        assertThat(mockProducer.history()).isEmpty();
    }

    @Test
    void shouldGenerateUniqueSensorIds() {
        UdpSensorListener listener1 = new UdpSensorListener(3344, SensorType.TEMPERATURE, mockProducer);
        UdpSensorListener listener2 = new UdpSensorListener(3355, SensorType.HUMIDITY, mockProducer);

        byte[] data = "25.0".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length);

        listener1.processPacket(packet);
        listener2.processPacket(packet);

        assertThat(mockProducer.history()).hasSize(2);
        assertThat(mockProducer.history().get(0).value()).contains("\"sensorId\":\"sensor-3344\"");
        assertThat(mockProducer.history().get(1).value()).contains("\"sensorId\":\"sensor-3355\"");
    }

    @Test
    void shouldProduceValidJson() {
        listener = new UdpSensorListener(3344, SensorType.TEMPERATURE, mockProducer);

        byte[] data = "25.5".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length);

        listener.processPacket(packet);

        String json = mockProducer.history().get(0).value();

        // Verify JSON structure
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
        assertThat(json).contains("\"reading\"");
        assertThat(json).contains("\"sensorId\"");
        assertThat(json).contains("\"value\"");
        assertThat(json).contains("\"sensorType\"");
        assertThat(json).contains("\"unit\"");
        assertThat(json).contains("\"timestamp\"");
    }

    @Test
    void shouldHandleVeryLargeValues() {
        listener = new UdpSensorListener(3344, SensorType.TEMPERATURE, mockProducer);

        // Updated: 999999.99 is out of valid range (-40 to 60°C), should be rejected
        byte[] data = "999999.99".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length);

        assertThatThrownBy(() -> listener.processPacket(packet))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid sensor data")
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .cause()
                .hasMessageContaining("exceeds maximum");

        assertThat(mockProducer.history()).isEmpty();
    }

    @Test
    void shouldProcessMultiplePacketsSequentially() {
        listener = new UdpSensorListener(3344, SensorType.TEMPERATURE, mockProducer);

        for (int i = 0; i < 5; i++) {
            byte[] data = String.valueOf(20.0 + i).getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length);
            listener.processPacket(packet);
        }

        assertThat(mockProducer.history()).hasSize(5);
        assertThat(mockProducer.history().get(0).value()).contains("\"value\":20.0");
        assertThat(mockProducer.history().get(4).value()).contains("\"value\":24.0");
    }
}
