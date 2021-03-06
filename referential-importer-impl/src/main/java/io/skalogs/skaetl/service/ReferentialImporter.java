package io.skalogs.skaetl.service;

/*-
 * #%L
 * referential-importer-impl
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

import com.fasterxml.jackson.databind.JsonNode;
import io.skalogs.skaetl.admin.KafkaAdminService;
import io.skalogs.skaetl.config.KafkaConfiguration;
import io.skalogs.skaetl.config.ProcessConfiguration;
import io.skalogs.skaetl.config.RegistryConfiguration;
import io.skalogs.skaetl.domain.*;
import io.skalogs.skaetl.serdes.GenericDeserializer;
import io.skalogs.skaetl.serdes.GenericSerdes;
import io.skalogs.skaetl.serdes.GenericSerializer;
import io.skalogs.skaetl.service.processor.*;
import io.skalogs.skaetl.utils.JSONUtils;
import io.skalogs.skaetl.utils.KafkaUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Consumed;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@Component
@Lazy(value = false)
@Slf4j
@AllArgsConstructor
public class ReferentialImporter {

    public static final String TOPIC_PARSED_PROCESS = "parsedprocess";
    public static final String TOPIC_MERGE_REFERENTIAL = "mergereferential";
    private final KafkaAdminService kafkaAdminService;
    private final KafkaConfiguration kafkaConfiguration;
    private final ProcessConfiguration processConfiguration;
    private final RegistryConfiguration registryConfiguration;
    private final ApplicationContext applicationContext;
    private final Map<ProcessReferential, List<KafkaStreams>> runningProcessReferential = new HashMap();
    private final Map<ProcessReferential, List<KafkaStreams>> runningMergeProcess = new HashMap();
    private final Map<ProcessReferential, List<ReferentialService>> runningService = new HashMap();

    @PostConstruct
    public void init() {
        sendToRegistry("addService");
    }

    public void activate(ProcessReferential processReferential) {
        if (StringUtils.isNotBlank(processReferential.getIdProcess())) {
            String topicMerge = TOPIC_MERGE_REFERENTIAL + "-" + processReferential.getIdProcess();
            kafkaAdminService.buildTopic(topicMerge);
            runningMergeProcess.put(processReferential, new ArrayList<>());
            runningProcessReferential.put(processReferential, new ArrayList<>());
            runningService.put(processReferential, new ArrayList<>());
            processReferential.getListIdProcessConsumer().stream().forEach(consumerId -> feedStream(consumerId, processReferential, topicMerge));
            // treat the merge topic
            log.info("creating {} Process Referential", processReferential.getName());
            buildStreamMerge(processReferential, topicMerge);
        } else {
            log.error("No Referential Id for processReferential {}", processReferential);
        }
    }

    private void feedStream(String consumerId, ProcessReferential processReferential, String topicMerge) {
        String topicSource = consumerId + TOPIC_PARSED_PROCESS;
        log.info("creating {} Process Merge for topicsource {}", consumerId, topicSource);
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, JsonNode> streamToMerge = builder.stream(topicSource, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde()));
        streamToMerge.to(topicMerge, Produced.with(Serdes.String(), GenericSerdes.jsonNodeSerde()));
        KafkaStreams streams = new KafkaStreams(builder.build(), KafkaUtils.createKStreamProperties(processReferential.getIdProcess() + "_" + consumerId + "-_merge-topic", kafkaConfiguration.getBootstrapServers()));
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        runningMergeProcess.get(processReferential).add(streams);
        streams.start();
    }

    public void deactivate(ProcessReferential processReferential) {
        if (runningMergeProcess.containsKey(processReferential)) {
            log.info("deactivating {} Process Merge", processReferential.getName());
            runningMergeProcess.get(processReferential).stream()
                    .forEach(stream -> stream.close());
        }
        if (runningProcessReferential.containsKey(processReferential)) {
            log.info("deactivating {} Process Referential", processReferential.getName());
            runningProcessReferential.get(processReferential).stream()
                    .forEach(stream -> stream.close());
        }
        runningProcessReferential.remove(processReferential);
        runningService.remove(processReferential);
    }

    private void buildStreamMerge(ProcessReferential processReferential, String topicMerge) {
        StreamsBuilder builder = new StreamsBuilder();

        StoreBuilder referentialStore = Stores
                .keyValueStoreBuilder(Stores.persistentKeyValueStore(ReferentialTransformer.REFERENTIAL),
                        Serdes.String(),
                        Serdes.serdeFrom(new GenericSerializer<Referential>(), new GenericDeserializer(Referential.class)))
                .withLoggingEnabled(new HashMap<>());
        builder.addStateStore(referentialStore);

        KStream<String, JsonNode> streamToRef = builder.stream(topicMerge, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde()));

        ReferentialTransformer referentialTransformer = new ReferentialTransformer(processReferential);
        KStream<String, Referential>[] referentialStreams = streamToRef.transformValues(() -> referentialTransformer, ReferentialTransformer.REFERENTIAL)
                .flatMapValues((value -> value))
                .branch((k, referential) -> referential.getTypeReferential() == null,
                        (k, referential) -> referential.getTypeReferential() == TypeReferential.TRACKING,
                        (k, referential) -> referential.getTypeReferential() == TypeReferential.VALIDATION);


        routeResult(referentialStreams[0], processReferential.getProcessOutputs(), ReferentialElasticsearchProcessor.class);
        routeResult(referentialStreams[1], processReferential.getTrackingOuputs(), ReferentialEventToElasticSearchProcessor.class);
        routeResult(referentialStreams[2], processReferential.getValidationOutputs(), ReferentialEventToElasticSearchProcessor.class);

        KafkaStreams stream = new KafkaStreams(builder.build(), KafkaUtils.createKStreamProperties(processReferential.getIdProcess() + "_" + TOPIC_MERGE_REFERENTIAL, kafkaConfiguration.getBootstrapServers()));
        Runtime.getRuntime().addShutdownHook(new Thread(stream::close));
        runningProcessReferential.get(processReferential).add(stream);
        stream.start();
    }

    public void routeResult(KStream<String, Referential> result, List<ProcessOutput> processOutputs, Class<? extends AbstractElasticsearchProcessor> toElasticsearchProcessorClass) {
        KStream<String, JsonNode> resultAsJsonNode = result.mapValues(value -> JSONUtils.getInstance().toJsonNode(value));
        for (ProcessOutput processOutput : processOutputs) {
            switch (processOutput.getTypeOutput()) {
                case KAFKA:
                    toKafkaTopic(resultAsJsonNode, processOutput.getParameterOutput());
                    break;
                case ELASTICSEARCH:
                    toElasticsearch(resultAsJsonNode, processOutput.getParameterOutput(), toElasticsearchProcessorClass);
                    break;
                case SYSTEM_OUT:
                    toSystemOut(resultAsJsonNode);
                    break;
                case EMAIL:
                    toEmail(resultAsJsonNode, processOutput.getParameterOutput());
                    break;
                case SLACK:
                    toSlack(resultAsJsonNode, processOutput.getParameterOutput());
                    break;
                case SNMP:
                    toSnmp(resultAsJsonNode, processOutput.getParameterOutput());
            }
        }

    }

    private void toKafkaTopic(KStream<String, JsonNode> result, ParameterOutput parameterOutput) {
        result.to(parameterOutput.getTopicOut(), Produced.with(Serdes.String(), GenericSerdes.jsonNodeSerde()));
    }

    private void toElasticsearch(KStream<String, JsonNode> result, ParameterOutput parameterOutput, Class<? extends AbstractElasticsearchProcessor> toElasticsearchProcessorClass) {
        result.process(() -> applicationContext.getBean(toElasticsearchProcessorClass, parameterOutput.getElasticsearchRetentionLevel()));
    }

    private void toSystemOut(KStream<String, JsonNode> result) {
        result.process(() -> new LoggingProcessor<>());
    }

    private void toEmail(KStream<String, JsonNode> result, ParameterOutput parameterOutput) {
        String email = parameterOutput.getEmail();
        String template = parameterOutput.getTemplate();


        result.process(() -> new JsonNodeEmailProcessor(email, template, applicationContext.getBean(EmailService.class)));
    }

    private void toSlack(KStream<String, JsonNode> result, ParameterOutput parameterOutput) {
        result.process(() -> new JsonNodeSlackProcessor(parameterOutput.getWebHookURL(), parameterOutput.getTemplate()));
    }

    private void toSnmp(KStream<String, JsonNode> result, ParameterOutput parameterOutput) {
        result.process(() -> new JsonNodeSnmpProcessor(applicationContext.getBean(SnmpService.class)));
    }

    private void sendToRegistry(String action) {
        if (registryConfiguration.getActive()) {
            RegistryWorker registry = null;
            try {
                registry = RegistryWorker.builder()
                        .workerType(WorkerType.REFERENTIAL_PROCESS)
                        .ip(InetAddress.getLocalHost().getHostName())
                        .name(InetAddress.getLocalHost().getHostName())
                        .port(processConfiguration.getPortClient())
                        .statusConsumerList(statusExecutor())
                        .build();
                RestTemplate restTemplate = new RestTemplate();
                HttpEntity<RegistryWorker> request = new HttpEntity<>(registry);
                String url = processConfiguration.getUrlRegistry();
                String res = restTemplate.postForObject(url + "/process/registry/" + action, request, String.class);
                log.debug("sendToRegistry result {}", res);
            } catch (Exception e) {
                log.error("Exception on sendToRegistry", e);
            }
        }

    }

    public List<StatusConsumer> statusExecutor() {
        return runningProcessReferential.keySet().stream()
                .map(e -> StatusConsumer.builder()
                        .statusProcess(StatusProcess.ENABLE)
                        .creation(e.getTimestamp())
                        .idProcessConsumer(e.getIdProcess())
                        .build())
                .collect(toList());
    }

    @Scheduled(initialDelay = 20 * 1000, fixedRate = 5 * 60 * 1000)
    public void refresh() {
        sendToRegistry("refresh");
    }

    public void flush() {
        runningService.values().stream().forEach(
                referentialServices -> referentialServices.stream()
                        .forEach(referentialService -> referentialService.flush()));
    }
}

