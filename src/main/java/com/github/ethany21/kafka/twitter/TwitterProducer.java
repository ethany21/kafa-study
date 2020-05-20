package com.github.ethany21.kafka.twitter;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.protocol.types.Field;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class TwitterProducer {
    private Logger logger = LoggerFactory.getLogger(TwitterProducer.class.getName());

    private String consumerKey = "RKADjHxWAMIqWXtddJ2qFkt8E";
    private String consumerSecret = "sYIoTp2XaCSg81k4gqzcRNiiQwXVOppcS0ykhhP5UgcyLfiYe3";
    private String token = "2975163290-9805evZsfyPIyvrdoBvBU0p5zYxSTa3HJ1kjfwm";
    private String secret = "90J5kexpOg2yU1Yu8oz7oAZ5gLDr4Al3nRUEAbYkqilM0";

    public TwitterProducer(){}

    public static void main(String[] args) {
        new TwitterProducer().run();
    }
    public void run() {
        BlockingDeque<String> messageQueue = new LinkedBlockingDeque<String>(1000);
        // create a twitter client
        Client client = createTwitterClient(messageQueue);

        // Attempts to establish a connection.
        client.connect();

        // create a kafka produce
        KafkaProducer<String, String> producer = createKafkaProducer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("stopping application");
            logger.info("shutting down client from twitter");
            client.stop();
            logger.info("closing producer");
            producer.close();
            logger.info("done");
        }));

        // loop to send tweet to kafka
        while (!client.isDone()) {
            String msg = null;
            try {
                msg = messageQueue.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e){
                e.printStackTrace();
                client.stop();
            }
            if (msg != null){
                logger.info(msg);
                producer.send(new ProducerRecord<>("tweets", null, msg), new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                        if(e != null){
                            logger.error("Error occured", e);
                        }
                    }
                });
            }
        }
        logger.info("Applictaion ended");
    }

    public Client createTwitterClient(BlockingDeque<String> messageQueue){

        Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();
        // Optional: set up some followings and track terms
        List<Long> followings = Lists.newArrayList(1234L, 566788L);
        //트위터에서 실시간 트렌드에 올라와 있는 것을 기준으로 리스트의 원소로 넣음
        //새벽이나 아침 시간은 미국쪽 트렌드, 오후 ~ 저녁 시간에는 한국쪽 트렌드
        List<String> terms = Lists.newArrayList("BoycottFedEx", "Snyder");
        hosebirdEndpoint.followings(followings);
        hosebirdEndpoint.trackTerms(terms);

        // These secrets should be read from a config file
        Authentication hosebirdAuth = new OAuth1(consumerKey, consumerSecret, token, secret);

        ClientBuilder builder = new ClientBuilder()
                .name("Hosebird-Client-01")                              // optional: mainly for the logs
                .hosts(hosebirdHosts)
                .authentication(hosebirdAuth)
                .endpoint(hosebirdEndpoint)
                .processor(new StringDelimitedProcessor(messageQueue));
        // optional: use this if you want to process client events
        //.eventMessageQueue(eventQueue);를 추가해서, 클라이언트 이벤트를 생성, 처리한다
        //BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<Event>(1000);도 추가한다.

        Client hosebirdClient = builder.build();
// Attempts to establish a connection.
        return hosebirdClient;
    }

    public KafkaProducer<String, String> createKafkaProducer(){
        String bootstrapServers = "127.0.0.1:9092";

        // create producer properties
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        //어떤 타입의 데이터를 키/값으로 카프카에 전송할 지를 정한다
        // 그리고 어떤 방법을 사용해서 byte 단위로 serialize 될지를 결정
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // create the producer
        // kafka producer의 key와 value를 String으로 지정한다
        KafkaProducer<String, String> producer = new KafkaProducer<String, String>(properties);
        return producer;
    }
}
