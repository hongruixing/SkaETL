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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.skalogs.skaetl.RawDataGen;
import io.skalogs.skaetl.domain.ParameterTransformation;
import io.skalogs.skaetl.domain.ProcessHashData;
import io.skalogs.skaetl.domain.TypeHash;
import io.skalogs.skaetl.utils.JSONUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class HashFieldTransformatorTest {
    @Test
    public void should_Process_Ok() throws Exception {
        HashFieldTransformator hashFieldTransformator = new HashFieldTransformator();

        RawDataGen rd = RawDataGen.builder().messageSend("message gni de test").project("project").type("type").build();
        ObjectMapper obj = new ObjectMapper();
        String value = obj.writeValueAsString(rd);
        ObjectNode jsonValue = JSONUtils.getInstance().parseObj(value);

        hashFieldTransformator.apply(null, ParameterTransformation.builder()
                .processHashData(ProcessHashData.builder()
                        .field("messageSend")
                        .typeHash(TypeHash.MURMUR3)
                        .build()
                ).build(), jsonValue);
        assertThat(jsonValue.path("messageSend").asText()).isEqualTo("7dd5a4ac398698f085e216e25a330f58");
    }

    @Test
    public void should_Process_Ko() throws Exception {
        HashFieldTransformator hashFieldTransformator = new HashFieldTransformator();

        RawDataGen rd = RawDataGen.builder().messageSend("").project("project").type("type").build();
        ObjectMapper obj = new ObjectMapper();
        String value = obj.writeValueAsString(rd);
        ObjectNode jsonValue = JSONUtils.getInstance().parseObj(value);

        hashFieldTransformator.apply(null, ParameterTransformation.builder()
                .processHashData(ProcessHashData.builder()
                        .field("messageSend")
                        .typeHash(TypeHash.MURMUR3)
                        .build()
                ).build(), jsonValue);
        assertThat(jsonValue.path("messageSend").asText()).isEqualTo("");
    }

}
