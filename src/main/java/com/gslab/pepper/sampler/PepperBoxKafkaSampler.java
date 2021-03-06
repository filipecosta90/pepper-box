
package com.gslab.pepper.sampler;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.gslab.pepper.util.ProducerKeys;
import com.gslab.pepper.util.PropsKeys;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.protocol.SecurityProtocol;
import org.apache.log.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import java.util.concurrent.CountDownLatch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * The PepperBoxKafkaSampler class custom java sampler for jmeter.
 *
 * @Author Satish Bhor<satish.bhor@gslab.com> 
 * @Author Nachiket Kate <nachiket.kate@gslab.com>
 * @Author Filipe Oliveira <filipe.oliveira@farfetch.com>
 * 
 * @Version 1.0
 * @since 01/03/2017
 */
public class PepperBoxKafkaSampler extends AbstractJavaSamplerClient {

    //kafka producer
    private KafkaProducer<String, Object> producer;

    // topic on which messages will be sent
    private String topic;

    //ack type {-1,0,1}
    private int ack;

    //Message placeholder keys
    private String msg_key_placeHolder;
    private String msg_val_placeHolder;

    private boolean key_message_flag = false;
    private static final Logger log = LoggingManager.getLoggerForClass();

    /**
     * Set default parameters and their values
     *
     * @return
     */
    @Override
    public Arguments getDefaultParameters() {

        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, ProducerKeys.BOOTSTRAP_SERVERS_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerKeys.ZOOKEEPER_SERVERS, ProducerKeys.ZOOKEEPER_SERVERS_DEFAULT);
        defaultParameters.addArgument(ProducerKeys.KAFKA_TOPIC_CONFIG, ProducerKeys.KAFKA_TOPIC_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ProducerKeys.KEY_SERIALIZER_CLASS_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProducerKeys.VALUE_SERIALIZER_CLASS_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerConfig.COMPRESSION_TYPE_CONFIG, ProducerKeys.COMPRESSION_TYPE_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerConfig.BATCH_SIZE_CONFIG, ProducerKeys.BATCH_SIZE_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerConfig.LINGER_MS_CONFIG, ProducerKeys.LINGER_MS_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerConfig.BUFFER_MEMORY_CONFIG, ProducerKeys.BUFFER_MEMORY_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerConfig.ACKS_CONFIG, ProducerKeys.ACKS_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerConfig.SEND_BUFFER_CONFIG, ProducerKeys.SEND_BUFFER_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerConfig.RECEIVE_BUFFER_CONFIG, ProducerKeys.RECEIVE_BUFFER_CONFIG_DEFAULT);
        defaultParameters.addArgument(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.PLAINTEXT.name);
        defaultParameters.addArgument(PropsKeys.KEYED_MESSAGE_KEY, PropsKeys.KEYED_MESSAGE_DEFAULT);
        defaultParameters.addArgument(PropsKeys.MESSAGE_KEY_PLACEHOLDER_KEY, PropsKeys.MSG_KEY_PLACEHOLDER);
        defaultParameters.addArgument(PropsKeys.MESSAGE_VAL_PLACEHOLDER_KEY, PropsKeys.MSG_PLACEHOLDER);
        defaultParameters.addArgument(ProducerKeys.KERBEROS_ENABLED, ProducerKeys.FLAG_NO);
        defaultParameters.addArgument(ProducerKeys.JAVA_SEC_AUTH_LOGIN_CONFIG, ProducerKeys.JAVA_SEC_AUTH_LOGIN_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerKeys.JAVA_SEC_KRB5_CONFIG, ProducerKeys.JAVA_SEC_KRB5_CONFIG_DEFAULT);
        defaultParameters.addArgument(ProducerKeys.SASL_KERBEROS_SERVICE_NAME, ProducerKeys.SASL_KERBEROS_SERVICE_NAME_DEFAULT);
        defaultParameters.addArgument(ProducerKeys.SASL_MECHANISM, ProducerKeys.SASL_MECHANISM_DEFAULT);

        defaultParameters.addArgument(ProducerKeys.SSL_ENABLED, ProducerKeys.FLAG_NO);
        defaultParameters.addArgument(SslConfigs.SSL_KEY_PASSWORD_CONFIG, "<Key Password>");
        defaultParameters.addArgument(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, "<Keystore Location>");
        defaultParameters.addArgument(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, "<Keystore Password>");
        defaultParameters.addArgument(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, "<Truststore Location>");
        defaultParameters.addArgument(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "<Truststore Password>");



        return defaultParameters;
    }

    /**
     * Gets invoked exactly once  before thread starts
     *
     * @param context
     */
    @Override
    public void setupTest(JavaSamplerContext context) {

        // set the ack type
        this.ack =  Integer.parseInt( context.getParameter(ProducerConfig.ACKS_CONFIG) );

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getBrokerServers(context));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, context.getParameter(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, context.getParameter(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        props.put(ProducerConfig.ACKS_CONFIG, context.getParameter(ProducerConfig.ACKS_CONFIG));
        props.put(ProducerConfig.SEND_BUFFER_CONFIG, context.getParameter(ProducerConfig.SEND_BUFFER_CONFIG));
        props.put(ProducerConfig.RECEIVE_BUFFER_CONFIG, context.getParameter(ProducerConfig.RECEIVE_BUFFER_CONFIG));
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, context.getParameter(ProducerConfig.BATCH_SIZE_CONFIG));
        props.put(ProducerConfig.LINGER_MS_CONFIG, context.getParameter(ProducerConfig.LINGER_MS_CONFIG));
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, context.getParameter(ProducerConfig.BUFFER_MEMORY_CONFIG));
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, context.getParameter(ProducerConfig.COMPRESSION_TYPE_CONFIG));
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, context.getParameter(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        props.put(ProducerKeys.SASL_MECHANISM, context.getParameter(ProducerKeys.SASL_MECHANISM));

        Iterator<String> parameters = context.getParameterNamesIterator();
        parameters.forEachRemaining(parameter -> {
            if (parameter.startsWith("_")) {
                props.put(parameter.substring(1), context.getParameter(parameter));
            }
        });


        String sslEnabled = context.getParameter(ProducerKeys.SSL_ENABLED);

        if (sslEnabled != null && sslEnabled.equals(ProducerKeys.FLAG_YES)) {

            props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, context.getParameter(SslConfigs.SSL_KEY_PASSWORD_CONFIG));
            props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, context.getParameter(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
            props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, context.getParameter(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG));
            props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, context.getParameter(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
            props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, context.getParameter(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG));
        }

        String kerberosEnabled = context.getParameter(ProducerKeys.KERBEROS_ENABLED);
        if (kerberosEnabled != null && kerberosEnabled.equals(ProducerKeys.FLAG_YES)) {
            System.setProperty(ProducerKeys.JAVA_SEC_AUTH_LOGIN_CONFIG, context.getParameter(ProducerKeys.JAVA_SEC_AUTH_LOGIN_CONFIG));
            System.setProperty(ProducerKeys.JAVA_SEC_KRB5_CONFIG, context.getParameter(ProducerKeys.JAVA_SEC_KRB5_CONFIG));
            props.put(ProducerKeys.SASL_KERBEROS_SERVICE_NAME, context.getParameter(ProducerKeys.SASL_KERBEROS_SERVICE_NAME));
        }

        if (context.getParameter(PropsKeys.KEYED_MESSAGE_KEY).equals("YES")) {
            key_message_flag= true;
            msg_key_placeHolder = context.getParameter(PropsKeys.MESSAGE_KEY_PLACEHOLDER_KEY);
        }
        msg_val_placeHolder = context.getParameter(PropsKeys.MESSAGE_VAL_PLACEHOLDER_KEY);
        topic = context.getParameter(ProducerKeys.KAFKA_TOPIC_CONFIG);
        producer = new KafkaProducer<String, Object>(props);

    }


    /**
     * For each sample request this method is invoked and will return success/failure result
     *
     * @param context
     * @return
     */
    @Override
    public SampleResult runTest(JavaSamplerContext context) {

        SampleResult sampleResult = new SampleResult();
        sampleResult.sampleStart();
        Object message_val = JMeterContextService.getContext().getVariables().getObject(msg_val_placeHolder);
        long message_val_size = 0;
        if( message_val != null ){
            message_val_size = message_val.toString().getBytes().length;
            sampleResult.setBodySize( message_val_size );
            ProducerRecord<String, Object> producerRecord;
            try {
                if (key_message_flag) {
                    Object message_key = JMeterContextService.getContext().getVariables().getObject(msg_key_placeHolder);
                    producerRecord = new ProducerRecord<String, Object>(topic, message_key.toString(), message_val);
                } else {
                    producerRecord = new ProducerRecord<String, Object>(topic, message_val);
                }
                // Record the start time of the sample
                sampleResult.sampleStart();
                //  If set to zero then the producer will not wait for any acknowledgment from the server at all. The record will be immediately added to the socket buffer and considered sent. No guarantee can be made that the server has received the record in this case, and the retries configuration will not take effect (as the client won't generally know of any failures). The offset given back for each record will always be set to -1.
                if ( this.ack == 0 ){
                    producer.send(producerRecord);
                }
                 // ack == 1 
                 // This will mean the leader will write the record to its local log but will respond without awaiting full acknowledgement from all followers. In this case should the leader fail immediately after acknowledging the record but before the followers have replicated it then the record will be lost.
                 // ack == -1 | acks=all
                 // This means the leader will wait for the full set of in-sync replicas to acknowledge the record. This guarantees that the record will not be lost as long as at least one in-sync replica remains alive. This is the strongest available guarantee. This is equivalent to the acks=-1 setting.  
                else{
                    
                    java.util.Date utilDate = new java.util.Date();
                    long unixTimestamp = utilDate.getTime();
                    log.info("Creating CountDownLatch at timestamp " + unixTimestamp );
                    CountDownLatch latch = new CountDownLatch(1);
                    producer.send(producerRecord, new KafkaCallBack(sampleResult, latch, log ) );
                    producer.flush();
                    try {
                        
                        latch.await();
                        unixTimestamp = utilDate.getTime();
                        log.info("Passed latch at timestamp" + unixTimestamp );
                      } catch (InterruptedException ex) {
                        log.error(ex.getMessage());
                        Thread.currentThread().interrupt();
                      }
                }
                sampleResult.sampleEnd();  
                
                sampleResult.setResponseData(message_val.toString(), StandardCharsets.UTF_8.name());
                // Sets the successful attribute of the SampleResult object.
                sampleResult.setSuccessful(true);
                // Set result statuses OK - shorthand method to set: ResponseCode ResponseMessage Successful status
                sampleResult.setSentBytes( message_val_size );
                sampleResult.setResponseOK();
            } catch (Exception e) {
                sampleResult.sampleEnd();
                log.error("Failed to send message", e);
                sampleResult.setResponseData(e.getMessage(), StandardCharsets.UTF_8.name());
                // Sets the successful attribute of the SampleResult object.
                sampleResult.setSuccessful(false);
            }
        }
        else{
            log.error("Error while getting message from JMeterContextService");
            sampleResult.setSuccessful(false);
        }    

        return sampleResult;
    }

    @Override
    public void teardownTest(JavaSamplerContext context) {
        producer.close();
    }

    private String getBrokerServers(JavaSamplerContext context) {

        StringBuilder kafkaBrokers = new StringBuilder();

        String zookeeperServers = context.getParameter(ProducerKeys.ZOOKEEPER_SERVERS);

        if (zookeeperServers != null && !zookeeperServers.equalsIgnoreCase(ProducerKeys.ZOOKEEPER_SERVERS_DEFAULT)) {

            try {

                ZooKeeper zk = new ZooKeeper(zookeeperServers, 10000, null);
                List<String> ids = zk.getChildren(PropsKeys.BROKER_IDS_ZK_PATH, false);

                for (String id : ids) {

                    String brokerInfo = new String(zk.getData(PropsKeys.BROKER_IDS_ZK_PATH + "/" + id, false, null));
                    JsonObject jsonObject = Json.parse(brokerInfo).asObject();

                    String brokerHost = jsonObject.getString(PropsKeys.HOST, "");
                    int brokerPort = jsonObject.getInt(PropsKeys.PORT, -1);

                    if (!brokerHost.isEmpty() && brokerPort != -1) {

                        kafkaBrokers.append(brokerHost);
                        kafkaBrokers.append(":");
                        kafkaBrokers.append(brokerPort);
                        kafkaBrokers.append(",");

                    }

                }
            } catch (IOException | KeeperException | InterruptedException e) {

                log.error("Failed to get broker information", e);

            }

        }

        if (kafkaBrokers.length() > 0) {

            kafkaBrokers.setLength(kafkaBrokers.length() - 1);

            return kafkaBrokers.toString();

        } else {

            return  context.getParameter(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);

        }
    }
}

/**
 * The KafkaCallBack class is a custom java kafka Callback.
 * @author Filipe Oliveira <filipe.oliveira@farfetch.com>
 * @version 1.1
 * @since 03/04/2018
 */
class KafkaCallBack implements Callback {

    private final SampleResult kafka_sample;
    private final CountDownLatch latch;
    private final Logger log;

    public KafkaCallBack(SampleResult sample, CountDownLatch latch, Logger log ) {
        this.kafka_sample = sample;
        this.latch = latch;
        this.log = log;
       }

    public void onCompletion(RecordMetadata metadata, Exception exception) {
        if (metadata != null) {
            latch.countDown();
            log.info("onCompletion received");
        } else {
            log.error(exception.toString());
            exception.printStackTrace();
        }
    }
}