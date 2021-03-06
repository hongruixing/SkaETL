package io.skalogs.skaetl.rules.codegeneration.metrics;

/*-
 * #%L
 * rule-executor
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

import io.skalogs.skaetl.rules.RuleMetricLexer;
import io.skalogs.skaetl.rules.RuleMetricParser;
import io.skalogs.skaetl.rules.codegeneration.RuleToJava;
import io.skalogs.skaetl.rules.codegeneration.SyntaxErrorListener;
import io.skalogs.skaetl.rules.codegeneration.domain.RuleCode;
import io.skalogs.skaetl.rules.codegeneration.exceptions.TemplatingException;
import io.skalogs.skaetl.rules.functions.FunctionRegistry;
import lombok.AllArgsConstructor;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.skalogs.skaetl.rules.codegeneration.RuleToJava.nullSafePredicate;

@Component
@AllArgsConstructor
public class RuleMetricToJava {

    private final FunctionRegistry functionRegistry;

    public RuleCode convert(String name, String dsl) {
        checkNotNull(name);
        checkNotNull(dsl);
        RuleMetricVisitorImpl ruleMetricVisitor = new RuleMetricVisitorImpl(functionRegistry);
        ruleMetricVisitor.visit(parser(dsl).parse());
        try {
            return templating(name, dsl, ruleMetricVisitor);
        } catch (Exception e) {
            throw new TemplatingException(e);
        }
    }

    private RuleCode templating(String name, String dsl, RuleMetricVisitorImpl ruleMetricVisitor) {
        String camelCaseName = RuleToJava.toCamelCase(name);
        String ruleClassName = StringUtils.replace(camelCaseName, "\"", "");
        String packageName = "io.skalogs.skaetl.metrics.generated";
        String javaCode = "package " + packageName + ";\n" +
                "\n" +
                "import com.fasterxml.jackson.databind.JsonNode;\n" +
                "import io.skalogs.skaetl.rules.metrics.GenericMetricProcessor;\n" +
                "import io.skalogs.skaetl.rules.metrics.udaf.AggregateFunction;\n" +
                "import io.skalogs.skaetl.domain.ProcessMetric;\n" +
                "import io.skalogs.skaetl.domain.JoinType;\n" +
                "import io.skalogs.skaetl.rules.metrics.domain.Keys;\n" +
                "import io.skalogs.skaetl.rules.metrics.domain.MetricResult;\n" +
                "import static java.util.concurrent.TimeUnit.*;\n" +
                "import io.skalogs.skaetl.utils.JSONUtils;\n" +
                "\n" +
                "import javax.annotation.Generated;\n" +
                "import static io.skalogs.skaetl.rules.UtilsValidator.*;\n" +
                "import static io.skalogs.skaetl.domain.JoinType.*;\n" +
                "import static io.skalogs.skaetl.domain.RetentionLevel.*;\n" +
                "import io.skalogs.skaetl.rules.functions.FunctionRegistry;\n" +
                "import io.skalogs.skaetl.rules.metrics.UDAFRegistry;\n" +
                "\n" +
                "import org.apache.kafka.streams.kstream.*;\n" +
                "\n" +
                "/*\n" +
                dsl + "\n" +
                "*/\n" +
                "@Generated(\"etlMetric\")\n" +
                "public class " + ruleClassName + " extends GenericMetricProcessor {\n" +
                "    private final JSONUtils jsonUtils = JSONUtils.getInstance();\n" +
                "    public " + ruleClassName + "(ProcessMetric processMetric, FunctionRegistry functionRegistry, UDAFRegistry udafRegistry) {\n";
        if (StringUtils.isBlank(ruleMetricVisitor.getJoinFrom())) {
            javaCode += "        super(processMetric, \"" + ruleMetricVisitor.getFrom() + "\", functionRegistry, udafRegistry);\n";
        } else {
            javaCode += "        super(processMetric, \"" + ruleMetricVisitor.getFrom() + "\", \"" + ruleMetricVisitor.getJoinFrom() + "\", functionRegistry, udafRegistry);\n";
        }

        javaCode += "    }\n" +
                "    \n";
        if (ruleMetricVisitor.getJoinType() != null) {
            javaCode += "    @Override\n" +
                    "    protected JoinType joinType() {\n" +
                    "        return " + ruleMetricVisitor.getJoinType() + ";\n" +
                    "    }\n" +
                    "    \n";
        }
        javaCode +=
                "    @Override\n" +
                        "    protected AggregateFunction aggInitializer() {\n" +
                        "        return aggFunction(\"" + ruleMetricVisitor.getAggFunction() + "\");\n" +
                        "    }\n" +
                        "    \n" +
                        "    @Override\n" +
                        "    protected KTable<Windowed<Keys>, Double> aggregate(KGroupedStream<Keys, JsonNode> kGroupedStream) {\n" +
                        "        return " + ruleMetricVisitor.getWindow() + ";\n" +
                        "    }\n";
        if (StringUtils.isNotBlank(ruleMetricVisitor.getAggFunctionField())) {
            javaCode += "    \n" +
                    "    @Override\n" +
                    "    protected JsonNode mapValues(JsonNode value) {\n" +
                    "        return jsonUtils.at(value, \"" + ruleMetricVisitor.getAggFunctionField() + "\");\n" +
                    "    }\n";
        }
        if (StringUtils.isNotBlank(ruleMetricVisitor.getWhere())) {
            javaCode += "    \n" +
                    "    @Override\n" +
                    "    protected boolean filter(String key, JsonNode jsonValue) {\n" +
                    "        return " + nullSafePredicate(ruleMetricVisitor.getWhere()) + ";\n" +
                    "    }\n";
        }

        if (!ruleMetricVisitor.getGroupBy().isEmpty()) {
            javaCode += "    \n" +
                    "    @Override\n" +
                    "    protected boolean filterKey(String key, JsonNode value) {\n";

            String filterKeyCode = ruleMetricVisitor.getGroupBy()
                    .stream()
                    .map(key -> "jsonUtils.has(value, \"" + key + "\")")
                    .collect(Collectors.joining(" && "));
            javaCode += "        return " + filterKeyCode + ";\n";
            javaCode += "    }\n" +
                    "    \n" +
                    "    @Override\n" +
                    "    protected Keys selectKey(String key, JsonNode value) {\n" +
                    "        Keys keys = super.selectKey(key,value);\n";
            for (String groupByField : ruleMetricVisitor.getGroupBy()) {
                javaCode += "        keys.addKey(\"" + groupByField + "\", jsonUtils.at(value, \"" + groupByField + "\").asText());\n";
            }
            javaCode += "        return keys;\n" +
                    "    }\n";
        }

        if (StringUtils.isNotBlank(ruleMetricVisitor.getHaving())) {
            javaCode += "    \n" +
                    "    @Override\n" +
                    "    protected boolean having(Windowed<Keys> keys, Double result) {\n" +
                    "        return " + nullSafePredicate(ruleMetricVisitor.getHaving()) + ";\n" +
                    "    }\n";
        }

        if (StringUtils.isNotBlank(ruleMetricVisitor.getJoinFrom())) {
            javaCode += "    \n" +
                    "    @Override\n" +
                    "    protected Keys selectKey(String key, JsonNode value) {\n" +
                    "        Keys keys = super.selectKey(key,value);\n" +
                    "        keys.addKey(\"" + ruleMetricVisitor.getJoinKeyFromA() + " = " + ruleMetricVisitor.getJoinKeyFromB() + "\", jsonUtils.at(value, \"" + ruleMetricVisitor.getJoinKeyFromA() + "\").asText());\n" +
                    "        return keys;\n" +
                    "    }\n" +
                    "    \n" +
                    "    @Override\n" +
                    "    protected Keys selectKeyJoin(String key, JsonNode value) {\n" +
                    "        Keys keys = super.selectKey(key,value);\n" +
                    "        keys.addKey(\"" + ruleMetricVisitor.getJoinKeyFromA() + " = " + ruleMetricVisitor.getJoinKeyFromB() + "\", jsonUtils.at(value, \"" + ruleMetricVisitor.getJoinKeyFromB() + "\").asText());\n" +
                    "        return keys;\n" +
                    "    }\n" +
                    "    \n" +
                    "    @Override\n" +
                    "    protected JoinWindows joinWindow() {\n" +
                    "        return " + ruleMetricVisitor.getJoinWindow() + ";\n" +
                    "    }\n";
        }

        if (StringUtils.isNotBlank(ruleMetricVisitor.getJoinWhere())) {
            javaCode += "    \n" +
                    "    @Override\n" +
                    "    protected boolean filterJoin(String key, JsonNode jsonNode) {\n" +
                    "        return " + nullSafePredicate(ruleMetricVisitor.getWhere()) + ";\n" +
                    "    }\n";
        }

        javaCode += "}";

        return new RuleCode(ruleClassName, dsl, packageName + "." + ruleClassName, javaCode);
    }

    public static RuleMetricParser parser(String dsl) {
        SyntaxErrorListener syntaxErrorListener = new SyntaxErrorListener(dsl);

        RuleMetricLexer lexer = new RuleMetricLexer(new ANTLRInputStream(dsl));
        lexer.removeErrorListeners();
        lexer.addErrorListener(syntaxErrorListener);

        RuleMetricParser parser = new RuleMetricParser(new CommonTokenStream(lexer));
        parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
        parser.removeErrorListeners();
        parser.addErrorListener(syntaxErrorListener);

        return parser;
    }

}
