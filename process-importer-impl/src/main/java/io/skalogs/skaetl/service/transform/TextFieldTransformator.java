package io.skalogs.skaetl.service.transform;

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
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.skalogs.skaetl.domain.ParameterTransformation;
import io.skalogs.skaetl.domain.TypeValidation;
import io.skalogs.skaetl.service.TransformatorProcess;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class TextFieldTransformator extends TransformatorProcess {

    public TextFieldTransformator() {
        super(TypeValidation.FORMAT_TEXT, "Format a field as text");
    }

    public void apply(String idProcess, ParameterTransformation parameterTransformation, ObjectNode jsonValue) {
        JsonNode at = at(parameterTransformation.getKeyField(), jsonValue);
        String valueToFormat = at.getNodeType() == JsonNodeType.OBJECT ? at.toString() : at.asText();
        if (StringUtils.isNotBlank(valueToFormat)) {
            put(jsonValue, parameterTransformation.getKeyField() + "_text", valueToFormat);
            remove(jsonValue,parameterTransformation.getKeyField());
        }
    }
}
