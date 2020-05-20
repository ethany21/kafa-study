package com.github.ethany21.kafka.test;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ConsumerDemo {
    public static void main(String[] args) {
        new ConsumerDemo().run();
    }
    private ConsumerDemo(){

    }
    private void run() {

        Logger logger = LoggerFactory.getLogger(ConsumerDemo.class.getName());

        String bootstrapServers = "127.0.0.1:9092";
        String topic = "first_topic";
        String groupId = "my-first-kafka";
        // 멀티 스레딩을 위한 latch 만들기
        CountDownLatch latch = new CountDownLatch(1);
        //runnable한 consumer 만들기
        logger.info("Creating the consumer thread");
        Runnable myConsumerThread = new ConsumerRunnable(latch, bootstrapServers, groupId, topic);
        //스레드 실행
        Thread thread = new Thread(myConsumerThread);
        thread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() ->  {
            logger.info("Caught shutdown ");
            ((ConsumerRunnable) myConsumerThread).shutdown();
        }
        ));
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error("Application got interrupted",e);
        } finally {
            logger.info("Application is closing");
        }

    }

    public class ConsumerRunnable implements Runnable {

        private CountDownLatch latch;
        private KafkaConsumer<String, String> consumer;
        private Logger logger = LoggerFactory.getLogger(ConsumerRunnable.class);

        public ConsumerRunnable(CountDownLatch latch,
                              String bootstrapServers,
                              String groupId,
                              String topic){
            this.latch = latch;
            //consumer의 property로 필요한 것들: producer에서 보낸 키/값 메시지를 deserialize하고,
            //bootstrap 정보가 필요하며, group id가 필요하다
            Properties properties = new Properties();
            properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            //카프카 consumer가 어떤 방식으로 offset을 reseting 하는지 결정
            //earliest로 하면 가장 맨 처음 만들어진 메세지부터, latest로 하면 새로이 형성된 메세지만을 읽는다
            properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            //create consumer
            consumer = new KafkaConsumer<String, String>(properties);

            //subscribe consumer to out topics
            //지금은 하나의 토픽만을 subscribe하고 있기에, Collections.singleton(topic)을 사용하지만,
            //Arrays.asList(토픽 목록들....)을 사용하면 여러 토픽을 구독하게 할 수 있다.
            consumer.subscribe(Arrays.asList(topic));
        }

        @Override
        public void run(){
            try {
                //poll new data
                while (true) {
                    ConsumerRecords<String, String> records =
                            consumer.poll(Duration.ofMillis(100));

                    for (ConsumerRecord<String, String> record : records) {
                        logger.info("Key: " + record.key() + " Value: " + record.value());
                        logger.info("Partitions: " + record.partition());
                        logger.info("Offsets: " + record.offset());
                        logger.info("Timestamps: " + record.timestamp());
                    }
                }
            } catch (WakeupException e) {
                logger.info("Recieved shutdown signal");
            } finally {
                consumer.close();
                //main의 코드로부터 하여금 consumer에서 exit을 할 수 있음을 알려준다
                latch.countDown();
            }

        }
        public void shutdown(){
            //wakeup():
            consumer.wakeup();
        }

    }
}
