package io.skalogs.skaetl.web;

/*-
 * #%L
 * retry-importer
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

import io.skalogs.skaetl.service.RetryImporter;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("/manage")
@AllArgsConstructor
public class ManageController {

    private final RetryImporter retryImporter;

    @ResponseStatus(OK)
    @GetMapping("/activate")
    public void activate() {
        retryImporter.activate();
    }

    @ResponseStatus(OK)
    @GetMapping("/deactivate")
    public void deactivate() {
        retryImporter.deactivate();
    }

}
