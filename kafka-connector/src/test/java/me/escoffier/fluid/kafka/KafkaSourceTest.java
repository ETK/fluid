package me.escoffier.fluid.kafka;

import io.debezium.kafka.KafkaCluster;
import io.debezium.util.Testing;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import me.escoffier.fluid.constructs.Sink;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class KafkaSourceTest {

    private Vertx vertx;

    private static KafkaCluster kafka;

    @BeforeClass
    public static void beforeClass() throws IOException {
        Properties props = new Properties();
        props.setProperty("zookeeper.connection.timeout.ms", "10000");
        File directory = Testing.Files.createTestingDirectory(System.getProperty("java.io.tmpdir"), true);
        kafka = new KafkaCluster().withPorts(2182, 9092).addBrokers(1)
            .usingDirectory(directory)
            .deleteDataUponShutdown(true)
            .withKafkaConfiguration(props)
            .deleteDataPriorToStartup(true)
            .startup();
    }

    @AfterClass
    public static void afterClass() {
        kafka.shutdown();
    }

    @Before
    public void setup() {
        vertx = Vertx.vertx();
    }

    @After
    public void tearDown() {
        vertx.close();
    }

    @Test
    public void testSource() throws InterruptedException {
        KafkaUsage usage = new KafkaUsage();
        String topic = UUID.randomUUID().toString();

        KafkaSource<Integer> source = new KafkaSource<>(vertx,
            getKafkaConfig()
                .put("topic", topic)
                .put("value.serializer", IntegerSerializer.class.getName())
                .put("value.deserializer", IntegerDeserializer.class.getName())
        );

        List<Integer> results = new ArrayList<>();
        source
            .transform(i -> i + 1)
            .to(Sink.forEach(results::add));

        AtomicInteger counter = new AtomicInteger();
        usage.produceIntegers(10, null,
            () -> new ProducerRecord<>(topic, counter.getAndIncrement()));

        await().until(() -> results.size() >= 10);
        assertThat(results).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    }


    private JsonObject getKafkaConfig() {
        String randomId = UUID.randomUUID().toString();
        return new JsonObject()
            .put("bootstrap.servers", "localhost:9092")
            .put("enable.auto.commit", false)
            .put("group.id", randomId)
            .put("auto.offset.reset", "earliest")
            .put("key.serializer", StringSerializer.class.getName())
            .put("key.deserializer", StringDeserializer.class.getName());
    }


}