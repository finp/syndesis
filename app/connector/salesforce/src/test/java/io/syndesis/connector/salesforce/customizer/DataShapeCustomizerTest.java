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
package io.syndesis.connector.salesforce.customizer;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import io.syndesis.common.model.action.ConnectorAction;
import io.syndesis.common.model.connection.Connector;
import io.syndesis.common.model.integration.Step;
import io.syndesis.connector.salesforce.SalesforceIdentifier;
import io.syndesis.connector.salesforce.SalesforceTestSupport;
import io.syndesis.integration.component.proxy.ComponentProxyComponent;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.processor.Pipeline;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class DataShapeCustomizerTest extends SalesforceTestSupport {
    private static final Processor BEFORE_PROCESSOR = DataShapeCustomizerTest::nop;

    private static final Processor AFTER_PROCESSOR = DataShapeCustomizerTest::nop;

    // ********************
    //
    // ********************

    protected ComponentProxyComponent setUpComponent(String actionId) {
        Connector connector = mandatoryLookupConnector();
        ConnectorAction action = mandatoryLookupAction(connector, actionId);

        ComponentProxyComponent component = new ComponentProxyComponent("salesforce-1", "salesforce");
        component.setBeforeProducer(BEFORE_PROCESSOR);
        component.setAfterProducer(AFTER_PROCESSOR);

        DataShapeCustomizer customizer = new DataShapeCustomizer();
        customizer.setCamelContext(context());
        action.getDescriptor().getInputDataShape().ifPresent(customizer::setInputDataShape);
        action.getDescriptor().getOutputDataShape().ifPresent(customizer::setOutputDataShape);
        customizer.customize(component, Collections.emptyMap());

        return component;
    }

    @Override
    protected List<Step> createSteps() {
        return Collections.emptyList();
    }

    // ********************
    //
    // ********************

    @Test
    public void shouldNotRemoveExistingProcessors() {
        final ComponentProxyComponent component = setUpComponent("salesforce-create-sobject");

        final Processor createdBeforeProducer = component.getBeforeProducer();
        Assertions.assertThat(createdBeforeProducer).isInstanceOf(Pipeline.class);

        final Pipeline beforePipeline = (Pipeline) createdBeforeProducer;
        Assertions.assertThat(beforePipeline.next()).isInstanceOf(List.class).hasSize(2);
        //Using toString() since there's no direct access to a processor in Camel 3, which is wrapped in AsyncProcessorConverterHelper.ProcessorToAsyncProcessorBridge
        Assertions.assertThat(beforePipeline.next().get(0).toString()).contains(DataShapeCustomizer.UnmarshallProcessor.class.getName());
        Assertions.assertThat(beforePipeline.next().get(1).toString()).isEqualTo(BEFORE_PROCESSOR.toString());

        final Processor createdAfterProducer = component.getAfterProducer();
        Assertions.assertThat(createdAfterProducer).isInstanceOf(Pipeline.class);

        final Pipeline afterPipeline = (Pipeline) createdAfterProducer;
        Assertions.assertThat(afterPipeline.next()).isInstanceOf(List.class).hasSize(2);
        Assertions.assertThat(afterPipeline.next().get(0).toString()).contains(DataShapeCustomizer.UnmarshallProcessor.class.getName());
        Assertions.assertThat(afterPipeline.next().get(1).toString()).isEqualTo(AFTER_PROCESSOR.toString());
    }

    @Test
    public void shouldAllowNullInput() throws Exception {
        final ComponentProxyComponent component = setUpComponent("salesforce-create-sobject");
        final Exchange exchange = new DefaultExchange(context);
        final Message in = exchange.getIn();

        component.getBeforeProducer().process(exchange);

        Assertions.assertThat(in.getBody()).isNull();
    }

    @Test
    public void shouldAllowNullOutput() throws Exception {
        final ComponentProxyComponent component = setUpComponent("salesforce-create-sobject");
        final Exchange exchange = new DefaultExchange(context);
        final Message out = exchange.getOut();

        component.getAfterProducer().process(exchange);

        Assertions.assertThat(out.getBody()).isNull();
    }

    @Test
    public void shouldNotConvertFailedExchanges() throws Exception {
        final ComponentProxyComponent component = setUpComponent("salesforce-create-sobject");
        final Exchange exchange = new DefaultExchange(context);
        final Message out = exchange.getOut();

        exchange.setException(new Exception());
        out.setBody("wat");

        component.getAfterProducer().process(exchange);

        Assertions.assertThat(out.getBody()).isEqualTo("wat");
    }

    @Test
    public void shouldUnmarshallToSpecifiedInputType() throws Exception {
        final ComponentProxyComponent component = setUpComponent("salesforce-delete-sobject");
        final Exchange exchange = new DefaultExchange(context);
        final Message in = exchange.getIn();
        in.setBody("{}");

        component.getBeforeProducer().process(exchange);

        Assertions.assertThat(in.getBody()).isInstanceOf(SalesforceIdentifier.class);
    }

    @Test
    public void shouldUnmarshallToSpecifiedInputTypeWithoutConversion() throws Exception {
        final ComponentProxyComponent component = setUpComponent("salesforce-delete-sobject");
        final Exchange exchange = new DefaultExchange(context);
        final Message in = exchange.getIn();
        in.setBody(new SalesforceIdentifier("test"));

        component.getBeforeProducer().process(exchange);

        Assertions.assertThat(in.getBody()).isInstanceOf(SalesforceIdentifier.class);
        Assertions.assertThat(in.getBody()).hasFieldOrPropertyWithValue("id", "test");
    }

    @Test
    public void shouldFailToUnmarshallToSpecifiedInputTypeFromString() throws Exception {
        final ComponentProxyComponent component = setUpComponent("salesforce-delete-sobject");
        final Exchange exchange = new DefaultExchange(context);
        final Message in = exchange.getIn();
        in.setBody("invalid");

        component.getBeforeProducer().process(exchange);

        Assertions.assertThat(exchange.isFailed()).isTrue();
        Assertions.assertThat(exchange.getException()).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void shouldFailToUnmarshallToSpecifiedInputType() throws Exception {
        final ComponentProxyComponent component = setUpComponent("salesforce-delete-sobject");
        final Exchange exchange = new DefaultExchange(context);
        final Message in = exchange.getIn();
        in.setBody(new Object());

        component.getBeforeProducer().process(exchange);

        Assertions.assertThat(exchange.isFailed()).isTrue();
        Assertions.assertThat(exchange.getException()).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void shouldUnmarshallToSpecifiedOutputType() throws Exception {
        final ComponentProxyComponent component = setUpComponent("salesforce-create-sobject");
        final Exchange exchange = new DefaultExchange(context);
        final Message out = exchange.getIn();
        out.setBody("{}");

        component.getAfterProducer().process(exchange);

        Assertions.assertThat(out.getBody()).isInstanceOf(AbstractDTOBase.class);
    }

    private static void nop(final Exchange exchange) {
      // do nothing
    }
}
