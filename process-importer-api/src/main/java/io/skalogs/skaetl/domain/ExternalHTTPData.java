package io.skalogs.skaetl.domain;

/*-
 * #%L
 * process-importer-api
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

import lombok.*;
import org.springframework.http.HttpMethod;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class ExternalHTTPData {
    private String url;
    private Integer refresh;
    private HttpMethod httpMethod;
    private String body;
}
