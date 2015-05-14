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
import org.elasticsearch.common.base.Predicate;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.elasticsearch.test.ElasticsearchIntegrationTest.ThirdParty;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

/**
 * Base class for tests that an internet connection and twitter credentials to run.
 * Twitter tests are disabled by default.
 * <p/>
 * To enable test add -Dtests.thirdparty=true -Dtests.config=/path/to/elasticsearch.yml
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
@ThirdParty
public abstract class AbstractTwitterTest extends ElasticsearchIntegrationTest {

    /**
     * Repeat a task until it returns true or after a given wait time.
     * We use here a 1 second delay between two runs
     * @param breakPredicate test you want to run
     * @param maxWaitTime maximum time you want to wait
     * @param unit time unit used for maxWaitTime and maxSleepTime
     */
    public static boolean awaitBusy1Second(Predicate<?> breakPredicate, long maxWaitTime, TimeUnit unit) throws InterruptedException {
        long maxTimeInMillis = TimeUnit.MILLISECONDS.convert(maxWaitTime, unit);
        long sleepTimeInMillis = 1000;
        long iterations = maxTimeInMillis / sleepTimeInMillis;
        for (int i = 0; i < iterations; i++) {
            if (breakPredicate.apply(null)) {
                return true;
            }
            Thread.sleep(sleepTimeInMillis);
        }
        return breakPredicate.apply(null);
    }
}
