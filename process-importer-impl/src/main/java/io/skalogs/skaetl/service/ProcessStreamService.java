package io.skalogs.skaetl.service;

/*-
 * #%L
 * process-importer-impl
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.skalogs.skaetl.domain.*;
import io.skalogs.skaetl.rules.filters.GenericFilter;
import io.skalogs.skaetl.serdes.GenericSerdes;
import io.skalogs.skaetl.service.processor.*;
import io.skalogs.skaetl.utils.KafkaUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Consumed;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.context.ApplicationContext;

import java.util.List;

@Slf4j
public class ProcessStreamService extends AbstractStreamProcess {
    private final ESErrorRetryWriter esErrorRetryWriter;
    private final ApplicationContext applicationContext;
    private final List<GenericFilter> genericFilters;
    private final EmailService emailService;
    private final SnmpService snmpService;

    public ProcessStreamService(GenericValidator genericValidator, GenericTransformator transformValidator, GenericParser genericParser, GenericFilterService genericFilterService, ProcessConsumer processConsumer, List<GenericFilter> genericFilters, ESErrorRetryWriter esErrorRetryWriter, ApplicationContext applicationContext, EmailService emailService, SnmpService snmpService) {
        super(genericValidator, transformValidator, genericParser, genericFilterService, processConsumer);
        this.esErrorRetryWriter = esErrorRetryWriter;
        this.applicationContext = applicationContext;
        this.genericFilters = genericFilters;
        this.emailService = emailService;
        this.snmpService = snmpService;
    }

    public void createStreamProcess() {
        log.info("create Stream Process for treat INPUT");
        createStreamInput(getProcessConsumer().getProcessInput().getTopicInput(), getProcessConsumer().getIdProcess() + ProcessConstants.TOPIC_PARSED_PROCESS);
        log.info("create Stream Process for valid transform and filters");
        createStreamValidAndTransformAndFilter(getProcessConsumer().getIdProcess() + ProcessConstants.TOPIC_PARSED_PROCESS, getProcessConsumer().getIdProcess() + ProcessConstants.TOPIC_TREAT_PROCESS);
        getProcessConsumer().getProcessOutput().stream()
                .forEach(processOutput -> treatOutput(processOutput));
    }

    private void treatOutput(ProcessOutput processOutput) {
        log.info("create Stream Process for output {} / {}", processOutput.getTypeOutput(), processOutput);
        switch (processOutput.getTypeOutput()) {
            case ELASTICSEARCH:
                log.info("create Stream Process for treat ES");
                createStreamEs(getProcessConsumer().getIdProcess() + ProcessConstants.TOPIC_TREAT_PROCESS, processOutput.getParameterOutput());
                break;
            case KAFKA:
                createStreamKafka(getProcessConsumer().getIdProcess() + ProcessConstants.TOPIC_TREAT_PROCESS, processOutput.getParameterOutput());
                break;
            case SYSTEM_OUT:
                createStreamSystemOut(getProcessConsumer().getIdProcess() + ProcessConstants.TOPIC_TREAT_PROCESS);
                break;
            case EMAIL:
                createStreamEmail(getProcessConsumer().getIdProcess() + ProcessConstants.TOPIC_TREAT_PROCESS, processOutput.getParameterOutput());
                break;
            case SLACK:
                createStreamSlack(getProcessConsumer().getIdProcess() + ProcessConstants.TOPIC_TREAT_PROCESS, processOutput.getParameterOutput());
                break;
            case SNMP:
                createStreamSnmp(getProcessConsumer().getIdProcess() + ProcessConstants.TOPIC_TREAT_PROCESS, processOutput.getParameterOutput());
                break;
            default:
                log.error("TypeOut not managed {}", getProcessConsumer().getProcessOutput());
                break;
        }
    }

    private void createStreamInput(String inputTopic, String outputTopic) {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> streamInput = builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> streamParsed = streamInput.mapValues((value) -> {
            Metrics.counter("skaetl_nb_read_kafka_count", Lists.newArrayList(Tag.of("processConsumerName", getProcessConsumer().getName()))).increment();
            return getGenericParser().apply(value, getProcessConsumer());
        }).filter((key, value) -> StringUtils.isNotBlank(value));

        final Serde<String> stringSerdes = Serdes.String();

        streamParsed.to(outputTopic, Produced.with(stringSerdes, stringSerdes));

        KafkaStreams streams = new KafkaStreams(builder.build(), KafkaUtils.createKStreamProperties(getProcessConsumer().getIdProcess() + ProcessConstants.INPUT_PROCESS, getBootstrapServer()));
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        streams.start();
        addStreams(streams);
    }

    private void createStreamValidAndTransformAndFilter(String inputTopic, String outputTopic) {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, JsonNode> streamInput = builder.stream(inputTopic, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde()));
        String applicationId = getProcessConsumer().getIdProcess() + ProcessConstants.VALIDATE_PROCESS;
        Counter counter = Metrics.counter("skaetl_nb_transformation_validation_count", Lists.newArrayList(Tag.of("processConsumerName", getProcessConsumer().getName())));
        KStream<String, ValidateData> streamValidation = streamInput.mapValues((value) -> {
            ObjectNode resultTransformer = getGenericTransformator().apply(value, getProcessConsumer());
            ValidateData item = getGenericValidator().process(resultTransformer, getProcessConsumer());
            counter.increment();
            return item;
        }).filter((key, value) -> {
            //Validation
            if (!value.success) {
                //produce to errorTopic
                esErrorRetryWriter.sendToErrorTopic(applicationId, value);
                return false;
            }
            //FILTER
            return processFilter(value);
        });

        KStream<String, JsonNode> streamOfJsonNode = streamValidation.mapValues(value -> value.getJsonValue());
        streamOfJsonNode.to(outputTopic, Produced.with(Serdes.String(), GenericSerdes.jsonNodeSerde()));

        KafkaStreams streams = new KafkaStreams(builder.build(), KafkaUtils.createKStreamProperties(applicationId, getBootstrapServer()));
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        streams.start();
        addStreams(streams);
    }

    private Boolean processFilter(ValidateData item) {
        for (GenericFilter genericFilter : genericFilters) {
            FilterResult filterResult = genericFilter.filter(item.jsonValue);
            if (filterResult != null && !filterResult.getFilter()) {
                if (filterResult.getProcessFilter().getActiveFailForward()) {
                    getGenericFilterService().treatParseResult(filterResult.getProcessFilter(), item.jsonValue);
                }
                return false;
            }
        }
        return true;
    }

    public void createStreamEs(String inputTopic, ParameterOutput parameterOutput) {

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, JsonNode> streamToES = builder.stream(inputTopic, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde()));
        streamToES.process(() -> applicationContext.getBean(JsonNodeToElasticSearchProcessor.class, parameterOutput.getElasticsearchRetentionLevel(), parameterOutput.getIndexShape()));

        KafkaStreams streams = new KafkaStreams(builder.build(), KafkaUtils.createKStreamProperties(getProcessConsumer().getIdProcess() + ProcessConstants.ES_PROCESS, getBootstrapServer()));
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        streams.start();
        addStreams(streams);
    }

    public void createStreamSystemOut(String inputTopic) {

        StreamsBuilder builder = new StreamsBuilder();

        builder.stream(inputTopic, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde())).process(() -> new LoggingProcessor<>());

        KafkaStreams streams = new KafkaStreams(builder.build(), KafkaUtils.createKStreamProperties(getProcessConsumer().getIdProcess() + ProcessConstants.SYSOUT_PROCESS, getBootstrapServer()));
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        streams.start();
        addStreams(streams);
    }

    public void createStreamKafka(String inputTopic, ParameterOutput parameterOutput) {

        StreamsBuilder builder = new StreamsBuilder();

        builder.stream(inputTopic, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde()))
                //json as string powa
                .mapValues(value -> value.toString())
                .to(parameterOutput.getTopicOut(), Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams streams = new KafkaStreams(builder.build(), KafkaUtils.createKStreamProperties(getProcessConsumer().getIdProcess() + ProcessConstants.KAFKA_PROCESS, getBootstrapServer()));
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        streams.start();
        addStreams(streams);
    }

    public void createStreamEmail(String inputTopic, ParameterOutput parameterOutput) {

        String email = parameterOutput.getEmail();
        if (email != null) {
            String template = parameterOutput.getTemplate();
            StreamsBuilder builder = new StreamsBuilder();

            if (template != null)
                builder.stream(inputTopic, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde())).process(() -> new JsonNodeEmailProcessor(email, template, emailService));
            else
                builder.stream(inputTopic, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde())).process(() -> new JsonNodeEmailProcessor(email, emailService));

            KafkaStreams streams = new KafkaStreams(builder.build(), KafkaUtils.createKStreamProperties(getProcessConsumer().getIdProcess() + ProcessConstants.EMAIL_PROCESS, getBootstrapServer()));
            Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
            streams.start();
            addStreams(streams);
        } else {
            log.error("destinationEmail is null and it's not normal");
        }
    }

    public void createStreamSlack(String inputTopic, ParameterOutput parameterOutput) {

        String webHookURL = parameterOutput.getWebHookURL();
        if (webHookURL != null) {
            String template = parameterOutput.getTemplate();
            StreamsBuilder builder = new StreamsBuilder();

            if (template != null)
                builder.stream(inputTopic, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde())).process(() -> new JsonNodeSlackProcessor(webHookURL, template));
            else
                builder.stream(inputTopic, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde())).process(() -> new JsonNodeSlackProcessor(webHookURL));

            KafkaStreams streams = new KafkaStreams(builder.build(), KafkaUtils.createKStreamProperties(getProcessConsumer().getIdProcess() + ProcessConstants.SLACK_PROCESS, getBootstrapServer()));
            Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
            streams.start();
            addStreams(streams);
        } else {
            log.error("webHookURL is null and it's not normal");
        }
    }

    public void createStreamSnmp(String inputTopic, ParameterOutput parameterOutput) {

        StreamsBuilder builder = new StreamsBuilder();
        builder.stream(inputTopic, Consumed.with(Serdes.String(), GenericSerdes.jsonNodeSerde())).process(() -> new JsonNodeSnmpProcessor(snmpService));

        KafkaStreams streams = new KafkaStreams(builder.build(), KafkaUtils.createKStreamProperties(getProcessConsumer().getIdProcess() + ProcessConstants.SNMP_PROCESS, getBootstrapServer()));
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        streams.start();
        addStreams(streams);
    }
}
