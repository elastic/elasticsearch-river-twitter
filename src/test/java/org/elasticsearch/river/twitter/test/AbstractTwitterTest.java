/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import com.carrotsearch.randomizedtesting.annotations.TestGroup;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public abstract class AbstractTwitterTest {

    /**
     * Annotation for tests that an internet connection and twitter credentials to run.
     * Twitter tests are disabled by default.
     * <p/>
     * To enable test add -Dtests.twitter=true -Dtests.config=/path/to/elasticsearch.yml
     * <p/>
     * The elasticsearch.yml file should contain the following keys
     * <pre>
      river:
          twitter:
              oauth:
                 consumer_key: ""
                 consumer_secret: ""
                 access_token: ""
                 access_token_secret: ""
     * </pre>
     *
     * You need to get an OAuth token in order to use Twitter river.
     * Please follow [Twitter documentation](https://dev.twitter.com/docs/auth/tokens-devtwittercom), basically:
     *
     * <ul>
     * <li>Login to: https://dev.twitter.com/apps/
     * <li>Create a new Twitter application (let's say elasticsearch): https://dev.twitter.com/apps/new
     You don't need a callback URL.
     * <li>When done, click on `Create my access token`.
     * <li>Open `OAuth tool` tab and note `Consumer key`, `Consumer secret`, `Access token` and `Access token secret`.
     * </ul>
     */
    @Documented
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @TestGroup(enabled = false, sysProperty = SYSPROP_TWITTER)
    public @interface TwitterTest {
    }

    /**
     */
    public static final String SYSPROP_TWITTER = "tests.twitter";

}
