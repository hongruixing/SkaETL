package io.skalogs.skaetl.web;

/*-
 * #%L
 * skaetl-backend
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

import io.skalogs.skaetl.domain.GrokData;
import io.skalogs.skaetl.domain.GrokDomain;
import io.skalogs.skaetl.domain.GrokResult;
import io.skalogs.skaetl.domain.GrokResultSimulate;
import io.skalogs.skaetl.repository.GrokRepository;
import io.skalogs.skaetl.service.GrokService;
import io.skalogs.skaetl.web.domain.GrokSimulateWeb;
import io.skalogs.skaetl.web.domain.GrokTerm;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.websocket.server.PathParam;
import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@AllArgsConstructor
@RestController
@RequestMapping("/admin/grok")
public class GrokController {

    private final GrokService grokService;
    private final GrokRepository grokRepository;

    @ResponseStatus(OK)
    @GetMapping("/find")
    public List<GrokDomain> findGrok(@PathParam("filters") String filter) {
        return grokService.findGrokPatten(filter);
    }

    @ResponseStatus(OK)
    @GetMapping("/forceReload")
    public void forceReload() {
        grokService.setup();
    }

    @ResponseStatus(OK)
    @PostMapping("/create")
    public void create(@RequestBody GrokData grokData) {
        grokService.createUserGrok(grokData.getKey(), grokData.getValue());
    }

    @ResponseStatus(OK)
    @PostMapping("/delete")
    public void delete(@RequestBody GrokData grokData) {
        grokService.deleteGrok(grokData.getKey());
    }

    @ResponseStatus(OK)
    @DeleteMapping("/deleteAll")
    public void deleteAll() {
        grokRepository.deleteAll();
    }

    @ResponseStatus(OK)
    @PostMapping("/simulate")
    public List<GrokResultSimulate> simulate(@RequestBody GrokSimulateWeb grokSimluateWeb) {
        return grokService.simulate(grokSimluateWeb.getGrokPattern(), grokSimluateWeb.getValueList());
    }

    @ResponseStatus(OK)
    @PostMapping("/simulateAllPattern")
    public List<GrokResult> simulateAllPattern(@RequestBody GrokTerm grokTerm) {
        return grokService.simulateAllPattern(grokTerm.value);
    }

}
