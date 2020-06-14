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
package io.syndesis.server.inspector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.syndesis.common.util.json.JsonUtils;
import io.syndesis.server.inspector.DataMapperBaseInspector.Context;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

public class SpecificationClassInspectorTest {

    @Test
    public void shouldFindNestedClassesWithinFullJson() throws IOException {
        final SpecificationClassInspector inspector = new SpecificationClassInspector();

        final String specification = read("/twitter4j.Status.full.json");

        final Context<JsonNode> context = new Context<>(JsonUtils.reader().readTree(specification));

        final String json = inspector.fetchJsonFor("twitter4j.GeoLocation", context);

        assertThatJson(json).isEqualTo(read("/twitter4j.GeoLocation.json"));
    }

    private static String read(final String path) throws IOException {
        return IOUtils.toString(SpecificationClassInspectorTest.class.getResourceAsStream(path), StandardCharsets.UTF_8);
    }
}
