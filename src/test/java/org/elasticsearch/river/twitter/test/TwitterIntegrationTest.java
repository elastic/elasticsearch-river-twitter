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

import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.base.Predicate;
import org.elasticsearch.common.joda.time.DateTime;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.river.twitter.test.helper.HttpClient;
import org.elasticsearch.river.twitter.test.helper.HttpClientResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Assert;
import org.junit.Test;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Integration tests for Twitter river<br>
 * You must have an internet access.
 *
 * Launch it using:
 * mvn test -Dtests.twitter=true -Dtests.config=/path/to/elasticsearch.yml
 *
 * where your /path/to/elasticsearch.yml contains:

  river:
      twitter:
          oauth:
             consumer_key: ""
             consumer_secret: ""
             access_token: ""
             access_token_secret: ""

 */
@ElasticsearchIntegrationTest.ClusterScope(
        scope = ElasticsearchIntegrationTest.Scope.SUITE,
        numDataNodes = 1, numClientNodes = 0,
        transportClientRatio = 0.0)
@AbstractTwitterTest.TwitterTest
public class TwitterIntegrationTest extends ElasticsearchIntegrationTest {

    private final String track = "obama";

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        ImmutableSettings.Builder settings = ImmutableSettings.builder()
                .put(super.nodeSettings(nodeOrdinal));

        Environment environment = new Environment();

        // if explicit, just load it and don't load from env
        if (Strings.hasText(System.getProperty("tests.config"))) {
            settings.loadFromUrl(environment.resolveConfig(System.getProperty("tests.config")));
        }

        return settings.build();
    }

    private String getDbName() {
        return Strings.toUnderscoreCase(getTestName());
    }

    private void launchTest(XContentBuilder river, final Integer numDocs, boolean removeRiver)
            throws IOException, InterruptedException {
        logger.info("  -> Checking internet working");
        new HttpClient("www.elasticsearch.org", 80).request("/");
        HttpClientResponse response = new HttpClient("www.elasticsearch.org", 80).request("/");
        Assert.assertThat(response.errorCode(), is(200));

        logger.info("  -> Create river");
        try {
            createIndex(getDbName());
        } catch (IndexAlreadyExistsException e) {
            // No worries. We already created the index before
        }
        index("_river", getDbName(), "_meta", river);

        logger.info("  -> Wait for some docs");
        assertThat(awaitBusy(new Predicate<Object>() {
            public boolean apply(Object obj) {
                try {
                    refresh();
                    CountResponse response = client().prepareCount(getDbName()).get();
                    logger.info("  -> got {} docs in {} index", response.getCount(), getDbName());
                    return response.getCount() >= numDocs;
                } catch (IndexMissingException e) {
                    return false;
                }
            }
        }, 5, TimeUnit.MINUTES), equalTo(true));

        if (removeRiver) {
            logger.info("  -> Remove river");
            client().admin().indices().prepareDeleteMapping("_river").setType(getDbName()).get();
        }
    }

    @Test
    public void testLanguageFiltering() throws IOException, InterruptedException {
        launchTest(jsonBuilder()
            .startObject()
                .field("type", "twitter")
                .startObject("twitter")
                    .field("type", "filter")
                    .startObject("filter")
                        .field("tracks", "le")
                        .field("language", "fr")
                    .endObject()
                .endObject()
            .endObject(), randomIntBetween(5, 50), true);

        // We should have only FR data
        SearchResponse response = client().prepareSearch(getDbName())
                .addField("language")
                .addField("_source")
                .get();

        logger.info("  --> Search response: {}", response.toString());

        // All language fields should be fr
        for (SearchHit hit : response.getHits().getHits()) {
            assertThat(hit.field("language"), notNullValue());
            assertThat(hit.field("language").getValue().toString(), is("fr"));
        }
    }

    @Test
    public void testIgnoreRT() throws IOException, InterruptedException {
        launchTest(jsonBuilder()
            .startObject()
                .field("type", "twitter")
                .startObject("twitter")
                    .field("type", "sample")
                    .field("ignore_retweet", true)
               .endObject()
            .endObject(), randomIntBetween(5, 50), true);

        // We should have only FR data
        SearchResponse response = client().prepareSearch(getDbName())
                .addField("retweet.id")
                .get();

        logger.info("  --> Search response: {}", response.toString());

        // We should not have any RT
        for (SearchHit hit : response.getHits().getHits()) {
            assertThat(hit.field("retweet.id"), nullValue());
        }
    }

    @Test
    public void testRaw() throws IOException, InterruptedException {
        launchTest(jsonBuilder()
            .startObject()
                .field("type", "twitter")
                .startObject("twitter")
                    .field("raw", true)
                    .startObject("filter")
                          .field("tracks", track)
                    .endObject()
               .endObject()
            .endObject(), randomIntBetween(5, 50), true);

        // We should have data we don't have without raw set to true
        SearchResponse response = client().prepareSearch(getDbName())
                .addField("user.statuses_count")
                .addField("_source")
                .get();

        logger.info("  --> Search response: {}", response.toString());

        for (SearchHit hit : response.getHits().getHits()) {
            assertThat(hit.field("user.statuses_count"), notNullValue());
        }
    }

    /**
     * Tracking twitter account: 783214
     */
    @Test
    public void testFollow() throws IOException, InterruptedException {
        launchTest(jsonBuilder()
                .startObject()
                    .field("type", "twitter")
                    .startObject("twitter")
                        .startObject("filter")
                            .field("follow", "783214")
                        .endObject()
                    .endObject()
                    .startObject("index")
                        .field("bulk_size", 1)
                    .endObject()
                .endObject(), 1, true);
    }

    @Test
    public void testTracks() throws IOException, InterruptedException {
        launchTest(jsonBuilder()
            .startObject()
                .field("type", "twitter")
                .startObject("twitter")
                    .startObject("filter")
                        .field("tracks", track)
                    .endObject()
               .endObject()
            .endObject(), randomIntBetween(1, 10), true);

        // We should have only FR data
        SearchResponse response = client().prepareSearch(getDbName())
                .setQuery(QueryBuilders.queryString(track))
                .get();

        logger.info("  --> Search response: {}", response.toString());

        assertThat(response.getHits().getTotalHits(), greaterThan(0L));
    }

    @Test
    public void testSample() throws IOException, InterruptedException {
        launchTest(jsonBuilder()
            .startObject()
                .field("type", "twitter")
                .startObject("twitter")
                    .field("type", "sample")
               .endObject()
            .endObject(), randomIntBetween(10, 200), true);
    }

    @Test
    public void testUserStream() throws IOException, InterruptedException, TwitterException {
        launchTest(jsonBuilder()
            .startObject()
                .field("type", "twitter")
                .startObject("twitter")
                    .field("type", "user")
               .endObject()
            .endObject(), 0, false);

        // Wait for the river to start
        awaitBusy(new Predicate<Object>() {
            public boolean apply(Object obj) {
                try {
                    GetResponse response = get("_river", getDbName(), "_status");
                    return response.isExists();
                } catch (IndexMissingException e) {
                    return false;
                }
            }
        }, 10, TimeUnit.SECONDS);

        // The river could look started but it tooks actually some seconds
        // to get twitter stream up and running. So we wait 5 seconds more.
        awaitBusy(new Predicate<Object>() {
            public boolean apply(Object obj) {
                return false;
            }
        }, 5, TimeUnit.SECONDS);

        // Generate a tweet on your timeline
        // We need to read settings from elasticsearch.yml file
        Settings settings = cluster().getInstance(Settings.class);
        AccessToken accessToken = new AccessToken(
                settings.get("river.twitter.oauth.access_token"),
                settings.get("river.twitter.oauth.access_token_secret"));


        Twitter twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(
                settings.get("river.twitter.oauth.consumer_key"),
                settings.get("river.twitter.oauth.consumer_secret"));
        twitter.setOAuthAccessToken(accessToken);

        Status status = twitter.updateStatus("testing elasticsearch twitter river. Please ignore. " +
                        DateTime.now().toString());
        logger.info("  -> tweet [{}] sent: [{}]", status.getId(), status.getText());

        assertThat(awaitBusy(new Predicate<Object>() {
            public boolean apply(Object obj) {
                try {
                    refresh();
                    SearchResponse response = client().prepareSearch(getDbName()).get();
                    logger.info("  -> got {} docs in {} index", response.getHits().totalHits(), getDbName());
                    return response.getHits().totalHits() >= 1;
                } catch (IndexMissingException e) {
                    return false;
                }
            }
        }, 1, TimeUnit.MINUTES), is(true));

        logger.info("  -> Remove river");
        client().admin().indices().prepareDeleteMapping("_river").setType(getDbName()).get();
    }

    /**
     * Test for #51: https://github.com/elasticsearch/elasticsearch-river-twitter/issues/51
     */
    @Test
    public void testgeoAsArray() throws IOException, InterruptedException {
        launchTest(jsonBuilder()
            .startObject()
                .field("type", "twitter")
                .startObject("twitter")
                    .startObject("filter")
                        .field("tracks", track)
                    .endObject()
                    .field("geo_as_array", true)
               .endObject()
            .endObject(), randomIntBetween(1, 10), false);

        // We wait for geo located tweets (it could take a looooong time)
        assertThat(awaitBusy(new Predicate<Object>() {
            public boolean apply(Object obj) {
                try {
                    refresh();
                    SearchResponse response = client().prepareSearch(getDbName())
                            .setPostFilter(
                                    FilterBuilders.geoDistanceFilter("status.location")
                                    .point(0, 0)
                                    .distance(10000, DistanceUnit.KILOMETERS)
                            )
                            .addField("_source")
                            .addField("location")
                            .get();

                    logger.info("  --> Search response: {}", response.toString());

                    for (SearchHit hit : response.getHits().getHits()) {
                        if (hit.field("location") != null) {
                            // We have a location field so it must be an array containing 2 values
                            assertThat(hit.field("location").getValues().size(), is(2));
                            return true;
                        }
                    }
                    return false;
                } catch (IndexMissingException e) {
                    return false;
                }
            }
        }, 5, TimeUnit.MINUTES), is(true));

        logger.info("  -> Remove river");
        client().admin().indices().prepareDeleteMapping("_river").setType(getDbName()).get();
    }
}
