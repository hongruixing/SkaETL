package io.skalogs.skaetl.generator.secuRules;

/*-
 * #%L
 * generator
 * %%
 * Copyright (C) 2017 - 2018 SkaLogs
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.skalogs.skaetl.config.KafkaConfiguration;
import io.skalogs.skaetl.domain.*;
import io.skalogs.skaetl.service.MetricServiceHTTP;
import io.skalogs.skaetl.service.ProcessServiceHTTP;
import io.skalogs.skaetl.utils.KafkaUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class UtilsSecu {

    private final ProcessServiceHTTP processServiceHTTP;
    private final MetricServiceHTTP metricServiceHTTP;
    private final String host;
    private final String port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Producer<String, String> producer;
    private final String[] tabCustomerFirstName = new String[]{
            "JAMES",
            "JOHN",
            "ROBERT",
            "MICHAEL",
            "WILLIAM",
            "DAVID",
            "RICHARD",
            "MARY",
            "PATRICIA",
            "LINDA",
            "BARBARA",
            "ELIZABETH",
            "JENNIFER",
            "MARIA",
            "SUSAN",
            "MARGARET",
            "DOROTHY",
            "LISA",
            "NANCY",
            "KAREN",
            "BETTY",
            "HELEN",
            "SANDRA",
            "DONNA",
            "CAROL",
            "RUTH",
            "SHARON"
    };
    private final String[] tabCustomerLastName = new String[]{
            "SMITH",
            "JOHNSON",
            "WILLIAMS",
            "BROWN",
            "JONES",
            "MILLER",
            "DAVI",
            "GARCIA",
            "RODRIGUEZ",
            "WILSON",
            "MARTINEZ",
            "ANDERSON",
            "TAYLOR",
            "THOMAS",
            "HERNANDEZ",
            "MOORE",
            "MARTIN",
            "JACKSON",
            "THOMPSON",
            "WHITE",
            "LOPEZ",
            "LEE",
            "GONZALEZ",
            "HARRIS",
            "CLARK",
            "LEWIS",
            "ROBINSON",
            "WALKER",
            "PEREZ",
            "HALL",
            "YOUNG",
            "ALLEN",
            "SANCHEZ",
            "WRIGHT",
            "KING",
            "SCOTT"
    };
    private Random RANDOM = new Random();
    private List<ClientData> listClient = new ArrayList<>();

    public UtilsSecu(MetricServiceHTTP metricServiceHTTP, ProcessServiceHTTP processServiceHTTP, KafkaConfiguration kafkaConfiguration, KafkaUtils kafkaUtils) {
        this.metricServiceHTTP = metricServiceHTTP;
        this.processServiceHTTP = processServiceHTTP;
        this.host = kafkaConfiguration.getBootstrapServers().split(":")[0];
        this.port = kafkaConfiguration.getBootstrapServers().split(":")[1];
        this.producer = kafkaUtils.kafkaProducer();
    }

    public ClientData getClient() {
        return listClient.get(RANDOM.nextInt(listClient.size()));
    }

    private void initClient(int nbClient) {
        for (int i = 0; i < nbClient; i++) {
            listClient.add(ClientData.builder()
                    .ipClient("10.243." + (RANDOM.nextInt(50) + 50) + "." + RANDOM.nextInt(220))
                    .hostname("svLinPRDDC2" + RANDOM.nextInt(2048))
                    .username(tabCustomerFirstName[RANDOM.nextInt(tabCustomerFirstName.length)].substring(1) + tabCustomerLastName[RANDOM.nextInt(tabCustomerLastName.length)])
                    .build());
        }
    }

    public Date addMinutesAndSecondsToTime(int minutesToAdd, int secondsToAdd, Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date.getTime());
        cal.add(Calendar.HOUR, -6);
        cal.add(Calendar.MINUTE, minutesToAdd);
        cal.add(Calendar.SECOND, secondsToAdd);
        return cal.getTime();
    }

    public void sendToKafka(String topic, Object object) {
        try {
            String value = mapper.writeValueAsString(object);
            log.info("Sending topic {} value {}", topic, value);
            producer.send(new ProducerRecord(topic, value));
        } catch (Exception e) {
            log.error("Error sending to Kafka during generation ", e);
        }
    }

    public void setup(int nbUser) {
        processConnexion();
        processFirewall();
        processDatabase();
        processProxy();
        sshMetricRules();
        firewallMetricRules();
        databaseMetricRules();
        proxyMetricRules();
        joinMetricRules();
        initClient(nbUser);
    }

    private void processProxy() {
        if (processServiceHTTP.findProcess("idProcessProxy") == null) {
            List<ProcessTransformation> listProcessTransformation = new ArrayList<>();
            listProcessTransformation.add(ProcessTransformation.builder()
                    .typeTransformation(TypeValidation.ADD_FIELD)
                    .parameterTransformation(ParameterTransformation.builder()
                            .composeField(ProcessKeyValue.builder().key("project").value("secu").build())
                            .build())
                    .build());
            listProcessTransformation.add(ProcessTransformation.builder()
                    .typeTransformation(TypeValidation.ADD_FIELD)
                    .parameterTransformation(ParameterTransformation.builder()
                            .composeField(ProcessKeyValue.builder().key("type").value("proxy").build())
                            .build())
                    .build());
            processServiceHTTP.saveOrUpdate(ProcessConsumer.builder()
                    .idProcess("idProcessProxy")
                    .name("demo proxy")
                    .processInput(ProcessInput.builder().topicInput("proxy").host(this.host).port(this.port).build())
                    .processTransformation(listProcessTransformation)
                    .processOutput(Lists.newArrayList(
                            ProcessOutput.builder().typeOutput(TypeOutput.ELASTICSEARCH).parameterOutput(ParameterOutput.builder().elasticsearchRetentionLevel(RetentionLevel.week).build()).build()))
                    .build());

            try {
                //HACK
                Thread.sleep(2000);
                processServiceHTTP.activateProcess(processServiceHTTP.findProcess("idProcessProxy"));
            } catch (Exception e) {
                log.error("Exception processProxy {}", "idProcessProxy");
            }
        }
    }

    private void processFirewall() {
        if (processServiceHTTP.findProcess("idProcessFirewall") == null) {
            List<ProcessTransformation> listProcessTransformation = new ArrayList<>();
            listProcessTransformation.add(ProcessTransformation.builder()
                    .typeTransformation(TypeValidation.ADD_FIELD)
                    .parameterTransformation(ParameterTransformation.builder()
                            .composeField(ProcessKeyValue.builder().key("project").value("secu").build())
                            .build())
                    .build());
            listProcessTransformation.add(ProcessTransformation.builder()
                    .typeTransformation(TypeValidation.ADD_FIELD)
                    .parameterTransformation(ParameterTransformation.builder()
                            .composeField(ProcessKeyValue.builder().key("type").value("firewall").build())
                            .build())
                    .build());
            processServiceHTTP.saveOrUpdate(ProcessConsumer.builder()
                    .idProcess("idProcessFirewall")
                    .name("demo firewall")
                    .processInput(ProcessInput.builder().topicInput("firewall").host(this.host).port(this.port).build())
                    .processTransformation(listProcessTransformation)
                    .processOutput(Lists.newArrayList(
                            ProcessOutput.builder().typeOutput(TypeOutput.ELASTICSEARCH).parameterOutput(ParameterOutput.builder().elasticsearchRetentionLevel(RetentionLevel.week).build()).build()))
                    .build());

            try {
                //HACK
                Thread.sleep(2000);
                processServiceHTTP.activateProcess(processServiceHTTP.findProcess("idProcessFirewall"));
            } catch (Exception e) {
                log.error("Exception processFirewall {}", "idProcessFirewall");
            }
        }
    }

    private void processConnexion() {
        if (processServiceHTTP.findProcess("idProcessConnexion") == null) {
            List<ProcessTransformation> listProcessTransformation = new ArrayList<>();
            listProcessTransformation.add(ProcessTransformation.builder()
                    .typeTransformation(TypeValidation.ADD_FIELD)
                    .parameterTransformation(ParameterTransformation.builder()
                            .composeField(ProcessKeyValue.builder().key("project").value("secu").build())
                            .build())
                    .build());
            listProcessTransformation.add(ProcessTransformation.builder()
                    .typeTransformation(TypeValidation.ADD_FIELD)
                    .parameterTransformation(ParameterTransformation.builder()
                            .composeField(ProcessKeyValue.builder().key("type").value("connexion").build())
                            .build())
                    .build());
            processServiceHTTP.saveOrUpdate(ProcessConsumer.builder()
                    .idProcess("idProcessConnexion")
                    .name("demo connexion")
                    .processInput(ProcessInput.builder().topicInput("connexion").host(this.host).port(this.port).build())
                    .processTransformation(listProcessTransformation)
                    .processOutput(Lists.newArrayList(
                            ProcessOutput.builder().typeOutput(TypeOutput.ELASTICSEARCH).parameterOutput(ParameterOutput.builder().elasticsearchRetentionLevel(RetentionLevel.week).build()).build()))
                    .build());

            try {
                //HACK
                Thread.sleep(2000);
                processServiceHTTP.activateProcess(processServiceHTTP.findProcess("idProcessConnexion"));
            } catch (Exception e) {
                log.error("Exception processConnexion {}", "idProcessConnexion");
            }
        }
    }

    private void processDatabase() {
        if (processServiceHTTP.findProcess("idProcessDatabase") == null) {
            List<ProcessTransformation> listProcessTransformation = new ArrayList<>();
            listProcessTransformation.add(ProcessTransformation.builder()
                    .typeTransformation(TypeValidation.ADD_FIELD)
                    .parameterTransformation(ParameterTransformation.builder()
                            .composeField(ProcessKeyValue.builder().key("project").value("secu").build())
                            .build())
                    .build());
            listProcessTransformation.add(ProcessTransformation.builder()
                    .typeTransformation(TypeValidation.ADD_FIELD)
                    .parameterTransformation(ParameterTransformation.builder()
                            .composeField(ProcessKeyValue.builder().key("type").value("database").build())
                            .build())
                    .build());
            processServiceHTTP.saveOrUpdate(ProcessConsumer.builder()
                    .idProcess("idProcessDatabase")
                    .name("demo database")
                    .processInput(ProcessInput.builder().topicInput("database").host(this.host).port(this.port).build())
                    .processTransformation(listProcessTransformation)
                    .processOutput(Lists.newArrayList(
                            ProcessOutput.builder().typeOutput(TypeOutput.ELASTICSEARCH).parameterOutput(ParameterOutput.builder().elasticsearchRetentionLevel(RetentionLevel.week).build()).build()))
                    .build());

            try {
                //HACK
                Thread.sleep(2000);
                processServiceHTTP.activateProcess(processServiceHTTP.findProcess("idProcessDatabase"));
            } catch (Exception e) {
                log.error("Exception processDatabase {}", "idProcessDatabase");
            }
        }
    }

    private void joinMetricRules() {
        List<ProcessMetric> processMetrics = new ArrayList<>();
        processMetrics.add(ProcessMetric.builder()
                .idProcess("DATABASE_CONNECTION_FROM_LOCALHOST")
                .name("Database connection from localhost joined with SSH connection")
                .sourceProcessConsumers(Lists.newArrayList("idProcessDatabase"))
                .aggFunction("COUNT(*)")
                .where("statusAccess = \"OK\" AND remoteIp = \"127.0.0.1\"")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .sourceProcessConsumersB(Lists.newArrayList("idProcessConnexion"))
                .joinType(JoinType.INNER)
                .joinKeyFromA("databaseIp")
                .joinKeyFromB("serverIp")
                .joinWindowSize(15)
                .joinWindowUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        createProcessMetrics(processMetrics);
    }

    private void proxyMetricRules() {
        List<ProcessMetric> processMetrics = new ArrayList<>();

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_REQUEST_NON_2XX")
                .name("Proxy nb request non 2XX")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .where("urlStatus < 200 AND urlStatus > 300")
                .groupBy("remoteIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_REQUEST_SAME_IP_DIFFERENT_SESSION_ID")
                .name("Proxy nb request with same ip and different session id")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .groupBy("remoteIp,cookieSession")
                .having("> 1")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_UPLOAD_REQUEST_PER_SRC_IP")
                .name("Proxy nb upload request per src ip")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .where("urlMethod IN (\"PUT\",\"POST\",\"PATCH\")")
                .groupBy("remoteIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_AVG_REQUEST_SIZE_PER_USER")
                .name("Proxy average request size per user")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("AVG(request_size_l)")
                .where("urlMethod IN (\"PUT\",\"POST\",\"PATCH\")")
                .groupBy("user")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_DELETE_REQUEST_PER_SRC_IP")
                .name("Proxy nb delete request per src ip")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .where("urlMethod = \"DELETE\"")
                .groupBy("remoteIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_DELETE_REQUEST_PER_URL")
                .name("Proxy nb delete request per url")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .where("urlMethod = \"DELETE\"")
                .groupBy("url")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_SLOW_REQUEST_PER_SRC_IP")
                .name("Proxy nb slow request per src ip")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .where("global_request_time_l > 10")
                .groupBy("remoteIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_SLOW_CONNECTION_PER_SRC_IP")
                .name("Proxy nb slow connection per src ip")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .where("global_cnx_time_l > 10")
                .groupBy("remoteIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_BIG_REQUEST_SIZE_PER_IP")
                .name("Proxy nb request with big request size per src ip")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .where("request_size_l > 10")
                .groupBy("remoteIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_BIG_RESPONSE_SIZE_PER_IP")
                .name("Proxy nb request with big response size per src ip")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .where("response_size_l > 10")
                .groupBy("remoteIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_REQUEST_URL_IN_BL_PER_IP")
                .name("Proxy nb request with url in black list per src ip")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .where("uri CONTAINS (\"/login\",\"/logout\",\"/audit\",\"/admin\")")
                .groupBy("remoteIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("PROXY_NB_REQUEST_URL_IN_BL_PER_URL")
                .name("Proxy nb request with url in black list per url")
                .sourceProcessConsumers(Lists.newArrayList("idProcessProxy"))
                .aggFunction("COUNT(*)")
                .where("uri CONTAINS (\"/login\",\"/logout\",\"/audit\",\"/admin\")")
                .groupBy("urlHost")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        createProcessMetrics(processMetrics);
    }

    private void databaseMetricRules() {
        List<ProcessMetric> processMetrics = new ArrayList<>();
        processMetrics.add(ProcessMetric.builder()
                .idProcess("DATABASE_NB_CONNECTION_FAIL")
                .name("Database nb connection fail")
                .sourceProcessConsumers(Lists.newArrayList("idProcessDatabase"))
                .aggFunction("COUNT(*)")
                .where("statusAccess = \"KO\"")
                .groupBy("remoteIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("DATABASE_NB_INSERT_PER_SRC_IP")
                .name("Database nb insert per src ip")
                .sourceProcessConsumers(Lists.newArrayList("idProcessDatabase"))
                .aggFunction("COUNT(*)")
                .where("typeRequest = \"INSERT\"")
                .groupBy("remoteIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("DATABASE_NB_CONNECTION_FAIL_PER_DB_NAME")
                .name("Database nb connection per database name")
                .sourceProcessConsumers(Lists.newArrayList("idProcessDatabase"))
                .aggFunction("COUNT(*)")
                .where("statusAccess = \"KO\"")
                .groupBy("databaseName")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        createProcessMetrics(processMetrics);
    }

    private void firewallMetricRules() {
        List<ProcessMetric> processMetrics = new ArrayList<>();

        processMetrics.add(ProcessMetric.builder()
                .idProcess("FIREWALL_BLOCK_PER_DEST")
                .name("Firewall block by destination")
                .sourceProcessConsumers(Lists.newArrayList("idProcessFirewall"))
                .aggFunction("COUNT(*)")
                .where("status = \"BLOCKED\"")
                .groupBy("destIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());
        processMetrics.add(ProcessMetric.builder()
                .idProcess("FIREWALL_BLOCK_PER_DEST_WITH_SPECIFIC_PORTS")
                .name("Firewall block by destination on specific ports")
                .sourceProcessConsumers(Lists.newArrayList("idProcessFirewall"))
                .aggFunction("COUNT(*)")
                .where("status = \"BLOCKED\" AND dest_port_l in (25,80)")
                .groupBy("destIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("FIREWALL_BLOCK_PER_DEST_IN_SENSIBLE_SUBNET")
                .name("Firewall block by destination in sensible subnet")
                .sourceProcessConsumers(Lists.newArrayList("idProcessFirewall"))
                .aggFunction("COUNT(*)")
                .where("status = \"BLOCKED\" AND destIp IN_SUBNET(\"10.15.8.1/16\")")
                .groupBy("destIp")
                .windowType(WindowType.TUMBLING)
                .size(5)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());
        createProcessMetrics(processMetrics);
    }

    private void sshMetricRules() {
        List<ProcessMetric> processMetrics = new ArrayList<>();
        processMetrics.add(ProcessMetric.builder()
                .idProcess("SSH_CONNECTION_PER_SRC_IP")
                .name("SSH connexion per source IP")
                .sourceProcessConsumers(Lists.newArrayList("idProcessConnexion"))
                .aggFunction("COUNT(*)")
                .groupBy("clientIp")
                .windowType(WindowType.TUMBLING)
                .size(10)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("SSH_CONNECTION_FAIL_PER_SRC_IP")
                .name("SSH connexion fail per source IP")
                .sourceProcessConsumers(Lists.newArrayList("idProcessConnexion"))
                .aggFunction("COUNT(*)")
                .where("status = \"KO\"")
                .groupBy("clientIp")
                .windowType(WindowType.TUMBLING)
                .size(10)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());

        processMetrics.add(ProcessMetric.builder()
                .idProcess("SSH_CONNECTION_FAIL_PER_DEST_IP")
                .name("SSH connexion fail per dest IP")
                .sourceProcessConsumers(Lists.newArrayList("idProcessConnexion"))
                .aggFunction("COUNT(*)")
                .where("status = \"KO\"")
                .groupBy("serverIp")
                .windowType(WindowType.TUMBLING)
                .size(10)
                .sizeUnit(TimeUnit.MINUTES)
                .processOutputs(Lists.newArrayList(toEsOutput()))
                .build());
        createProcessMetrics(processMetrics);
    }

    private void createProcessMetrics(List<ProcessMetric> processMetrics) {
        for (ProcessMetric processMetric : processMetrics) {
            if (metricServiceHTTP.findById(processMetric.getIdProcess()) == null) {
                metricServiceHTTP.updateProcess(processMetric);
                try {
                    //HACK
                    Thread.sleep(2000);
                    metricServiceHTTP.activateProcess((ProcessMetric) metricServiceHTTP.findById(processMetric.getIdProcess()));
                } catch (Exception e) {
                    log.error("Exception createProcessMetrics {}", processMetric.getIdProcess());
                }
            }

        }
    }

    private ProcessOutput toEsOutput() {
        return ProcessOutput.builder()
                .typeOutput(TypeOutput.ELASTICSEARCH)
                .parameterOutput(ParameterOutput.builder().elasticsearchRetentionLevel(RetentionLevel.week).build())
                .build();
    }

    public void usecase(int minute){
        int rand = RANDOM.nextInt(20);
        if (rand == 5){
            usecaseLocalhost(minute);
            usecaseFwdDatabase(minute);
        }
    }

    private void usecaseFwdDatabase(int minute) {
        String dbIp = "15.15.10.10";

        ClientData client = getClient();

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        Date newDateDb = addMinutesAndSecondsToTime(minute, RANDOM.nextInt(50), new Date());
        sendToKafka("database", Database.builder()
                .databaseIp(dbIp)
                .user(client.username)
                .databaseName("PRODUCT_REFERENTIAL")
                .message("log database")
                .portDatabase(7878)
                .request("SELECT * FROM product;")
                .statusAccess("OK")
                .remoteIp(client.ipClient)
                .typeDatabase("ORACLE")
                .versionDatabase("12")
                .timestamp(df.format(newDateDb))
                .build());

        sendToKafka("firewall", Firewall.builder()
                .srcIp(client.ipClient)
                .srcPort(RANDOM.nextInt(55000) + 2048)
                .destIp(dbIp)
                .destPort(7878)
                .typeConnexion("JDBC")
                .user(client.username)
                .status("OK")
                .equipment("STORMSHIELD")
                .equipmentIp("10.242.18.2")
                .equipmentVersion("SN-300")
                .timestamp(df.format(newDateDb))
                .build());
        Date newDateDb2 = addMinutesAndSecondsToTime(minute + 2, RANDOM.nextInt(50), new Date());
        sendToKafka("database", Database.builder()
                .databaseIp(dbIp)
                .user(client.username)
                .databaseName("PRODUCT_REFERENTIAL")
                .message("log database")
                .portDatabase(7878)
                .request("SELECT * FROM product;")
                .statusAccess("OK")
                .remoteIp(client.ipClient)
                .typeDatabase("ORACLE")
                .versionDatabase("12")
                .timestamp(df.format(newDateDb2))
                .build());

        sendToKafka("firewall", Firewall.builder()
                .srcIp(client.ipClient)
                .srcPort(RANDOM.nextInt(55000) + 2048)
                .destIp(dbIp)
                .destPort(7878)
                .typeConnexion("JDBC")
                .user(client.username)
                .status("OK")
                .equipment("STORMSHIELD")
                .equipmentIp("10.242.18.2")
                .equipmentVersion("SN-300")
                .timestamp(df.format(newDateDb2))
                .build());
        Date newDateDb3 = addMinutesAndSecondsToTime(minute + 10, RANDOM.nextInt(50), new Date());
        sendToKafka("database", Database.builder()
                .databaseIp(dbIp)
                .user("oracle")
                .databaseName("PRODUCT_REFERENTIAL")
                .message("log database")
                .portDatabase(7878)
                .request("SELECT * FROM product;")
                .statusAccess("OK")
                .remoteIp("15.15.10.8")
                .typeDatabase("ORACLE")
                .versionDatabase("12")
                .timestamp(df.format(newDateDb3))
                .build());

    }

    private void usecaseLocalhost(int minute){
        String dbIp = "10.10.8."+(RANDOM.nextInt(5)+10);

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        Date newDateDb = addMinutesAndSecondsToTime(minute, RANDOM.nextInt(50), new Date());
        sendToKafka("database", Database.builder()
                    .databaseIp(dbIp)
                    .user("oracle")
                    .databaseName("CLIENT_ACCOUNT")
                    .message("log database")
                    .portDatabase(7878)
                    .request("SELECT * FROM users;")
                    .statusAccess("OK")
                    .remoteIp("127.0.0.1")
                    .typeDatabase("ORACLE")
                    .versionDatabase("12")
                .timestamp(df.format(newDateDb))
                    .build());
        Date newDate = addMinutesAndSecondsToTime(minute - 5, RANDOM.nextInt(50), new Date());
        ClientData client = getClient();

        sendToKafka("connexion", ConnexionSSH.builder()
                .clientIp(client.ipClient)
                .portClient(new Integer(22))
                .serverIp(dbIp)
                .hostname(client.hostname)
                .messageRaw("log ssh")
                .timestamp(df.format(newDate))
                .userClient(client.username)
                .build());
        Date newDate2 = addMinutesAndSecondsToTime(minute - 8, RANDOM.nextInt(50), new Date());

        sendToKafka("connexion", ConnexionSSH.builder()
                .clientIp(client.ipClient)
                .portClient(new Integer(22))
                .serverIp(dbIp)
                .hostname(client.hostname)
                .messageRaw("log ssh")
                .timestamp(df.format(newDate2))
                .userClient(client.username)
                .build());

    }
}
