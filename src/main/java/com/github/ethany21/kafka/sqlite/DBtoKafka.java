package com.github.ethany21.kafka.sqlite;

import org.apache.kafka.clients.producer.*;
import java.sql.*;
import java.util.Properties;

public class DBtoKafka {
    private static final String topic = "urls";

    public static void main(String[] args) throws Exception{

        Properties props = new Properties();
        props.put("bootstrap.servers","127.0.0.1:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        Producer<String, String> producer = new KafkaProducer<>(props);

        String msg;
        String myDriver = "com.mysql.cj.jdbc.Driver";
        String myUrl = "jdbc:sqlite:C:/Users/imwoo/AppData/Local/Google/Chrome/User Data/Default/History";
        Class.forName(myDriver);
        Connection conn = DriverManager.getConnection(myUrl);


        String query = "SELECT * FROM urls";

        Statement st = conn.createStatement();

        ResultSet rs = st.executeQuery(query);

        while (rs.next()) {
            int id = rs.getInt("id");
            String url = rs.getString("url");
            String title = rs.getString("title");
            int visit_count = rs.getInt("visit_count");
            int typed_count = rs.getInt("typed_count");
            long last_visit_time = rs.getLong("last_visit_time");
            int hidden = rs.getInt("hidden");
            msg = id + "," + url + "," + title + "," + visit_count + "," + typed_count + "," + last_visit_time + "," + hidden;

            producer.send(new ProducerRecord<>(topic, msg));
        }
        conn.close();
        producer.close();
    }
}
