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
package io.syndesis.server.endpoint.v1.dto;

import java.util.Collections;
import java.util.Map;

import io.syndesis.common.model.connection.ConfigurationProperty;

import io.syndesis.common.util.json.JsonUtils;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

public class MixedTest {

    @Test
    public void shouldSerializeAsEmptyMap() {
        assertThatJson(serialize(create())).isEqualTo("{}");
    }

    @Test
    public void shouldSerializeMultipleValue() {
        final ConfigurationProperty.PropertyValue val1 = new ConfigurationProperty.PropertyValue.Builder().label("label").value("value")
            .build();

        final Map<String, String> val2 = Collections.singletonMap("key", "value");

        assertThatJson(serialize(create(val1, val2))).isEqualTo("{\"label\": \"label\", \"value\": \"value\", \"key\": \"value\"}");
    }

    @Test
    public void shouldSerializeSimpleValue() {
        final ConfigurationProperty.PropertyValue val = new ConfigurationProperty.PropertyValue.Builder().label("label").value("value")
            .build();

        assertThatJson(serialize(create(val))).isEqualTo("{\"label\": \"label\", \"value\": \"value\"}");
    }

    private static Mixed create(final Object... parts) {
        return new Mixed(parts) {
            // test object
        };
    }

    private static String serialize(final Mixed value) {
        try {
            return JsonUtils.writer().writeValueAsString(value);
        } catch (final JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }
}
