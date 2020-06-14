/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.server.credential;

import io.syndesis.common.util.json.JsonUtils;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

public class AcquisitionMethodTest {

    @Test
    public void shouldSerializeAsExpected() throws JsonProcessingException {
        final AcquisitionMethod acquisitionMethod = new AcquisitionMethod.Builder()
            .description("description")
            .icon("icon")
            .label("label")
            .type(Type.OAUTH2)
            .build();

        final String json = JsonUtils.writer().writeValueAsString(acquisitionMethod);
        final String expected = "{\"description\":\"description\",\"icon\":\"icon\",\"label\":\"label\",\"type\":\"OAUTH2\",\"configured\":false}";
        assertThatJson(json).isEqualTo(expected);
    }
}
