package io.skalogs.skaetl.service.processor;

/*-
 * #%L
 * core
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
import io.skalogs.skaetl.domain.TypeOutput;
import io.skalogs.skaetl.utils.TemplateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

@Slf4j
public abstract class AbstractSlackProcessor<K, V> extends AbstractOutputProcessor<K, V> {
    private final String uri;
    private final String template;

    public AbstractSlackProcessor(String uri) {
        this.uri = uri;
        this.template = null;
    }

    public AbstractSlackProcessor(String uri, String template) {
        this.uri = uri;
        this.template = template;
    }

    protected abstract String buildMsg(V value);

    protected abstract JsonNode getMsg(V value);

    @Override
    public void process(K key, V value) {
        try {

            String v;

            if (!StringUtils.isBlank(template)) {
                v = TemplateUtils.getInstance().process(template, getMsg(value));
                v = "{\"text\":\"" + StringEscapeUtils.escapeJson(v) + "\"}";
            } else
                v = "{\"text\":\"" + StringEscapeUtils.escapeJson(buildMsg(value)) + "\"}";

            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(uri);

            StringEntity entity = new StringEntity(v);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            CloseableHttpResponse response = client.execute(httpPost);
            client.close();

            int code = response.getStatusLine().getStatusCode();
            String reason = response.getStatusLine().getReasonPhrase();

            if (code == 200)
                log.debug("Message sended to Slack key {} value {}", key, value);
            else
                log.error("Error during Slack calls: code {} reason {}", code, reason);

        } catch (Exception ex) {
            log.error("Exception during Slack calls {}", ex.getMessage());
        }
    }

    @Override
    public boolean support(TypeOutput typeOutput) {
        return typeOutput == TypeOutput.SLACK;
    }
}
