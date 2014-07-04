/*
 * Copyright 2006-2014 the original author or authors.
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

package com.consol.citrus.admin.converter.legacy;

import com.consol.citrus.admin.model.EndpointData;
import com.consol.citrus.model.config.core.MessageChannelReceiver;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Christoph Deppisch
 * @since 1.4.1
 */
public class MessageChannelReceiverConverterTest {

    private MessageChannelReceiverConverter endpointConverter = new MessageChannelReceiverConverter();

    @Test
    public void testConvert() throws Exception {
        EndpointData endpointData = endpointConverter.convert(new MessageChannelReceiver());
        Assert.assertEquals(endpointData.getType(), "channel");
    }
}