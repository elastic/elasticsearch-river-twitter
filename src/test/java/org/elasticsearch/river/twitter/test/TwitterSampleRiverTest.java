/*
 * Licensed to Elasticsearch (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.river.twitter.test;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * To run this test you have to provide your twitter login and twitter password.
 * You can also define test duration in seconds (default to 10)
 */
public class TwitterSampleRiverTest extends TwitterRiverAbstractTest {

    public static void main(String[] args) throws InterruptedException, IOException {
        TwitterSampleRiverTest instance = new TwitterSampleRiverTest();
        instance.launcher(args);
    }

    @Override
    protected XContentBuilder riverSettings() throws IOException {
        XContentBuilder xb = XContentFactory.jsonBuilder()
            .startObject()
                .field("type", "twitter")
                .startObject("twitter")
                    .field("user", username)
                    .field("password", password)
                    .field("type", "sample")
                .endObject()
            .endObject();
        return xb;
    }
}
