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

package org.elasticsearch.river.twitter;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.elasticsearch.threadpool.ThreadPool;

import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.json.DataObjectFactory;

import java.util.List;
import java.util.Map;

/**
 *
 */
public class TwitterRiver extends AbstractRiverComponent implements River {

    private final ThreadPool threadPool;

    private final Client client;

    private String oauthConsumerKey = null;
    private String oauthConsumerSecret = null;
    private String oauthAccessToken = null;
    private String oauthAccessTokenSecret = null;

    private String proxyHost;
    private String proxyPort;
    private String proxyUser;
    private String proxyPassword;

    private boolean raw = false;
    private boolean ignoreRetweet = false;

    private final String indexName;

    private final String typeName;

    private final int bulkSize;
    private final int maxConcurrentBulk;
    private final TimeValue bulkFlushInterval;

    private FilterQuery filterQuery;

    private String streamType;


    private volatile TwitterStream stream;

    private volatile BulkProcessor bulkProcessor;

    private volatile boolean closed = false;

    @SuppressWarnings({"unchecked"})
    @Inject
    public TwitterRiver(RiverName riverName, RiverSettings settings, Client client, ThreadPool threadPool) {
        super(riverName, settings);
        this.client = client;
        this.threadPool = threadPool;

        if (settings.settings().containsKey("twitter")) {
            Map<String, Object> twitterSettings = (Map<String, Object>) settings.settings().get("twitter");

            // Check removed properties
            if (twitterSettings.get("user") != null || twitterSettings.get("password") != null) {
                logger.warn("user and password are not supported anymore. See https://github.com/elasticsearch/elasticsearch-river-twitter/issues/28");
            }

            raw = XContentMapValues.nodeBooleanValue(twitterSettings.get("raw"), false);
            ignoreRetweet = XContentMapValues.nodeBooleanValue(twitterSettings.get("ignore_retweet"), false);

            if (twitterSettings.containsKey("oauth")) {
                Map<String, Object> oauth = (Map<String, Object>) twitterSettings.get("oauth");
                if (oauth.containsKey("consumerKey")) {
                    oauthConsumerKey = XContentMapValues.nodeStringValue(oauth.get("consumerKey"), null);
                }
                if (oauth.containsKey("consumer_key")) {
                    oauthConsumerKey = XContentMapValues.nodeStringValue(oauth.get("consumer_key"), null);
                }
                if (oauth.containsKey("consumerSecret")) {
                    oauthConsumerSecret = XContentMapValues.nodeStringValue(oauth.get("consumerSecret"), null);
                }
                if (oauth.containsKey("consumer_secret")) {
                    oauthConsumerSecret = XContentMapValues.nodeStringValue(oauth.get("consumer_secret"), null);
                }
                if (oauth.containsKey("accessToken")) {
                    oauthAccessToken = XContentMapValues.nodeStringValue(oauth.get("accessToken"), null);
                }
                if (oauth.containsKey("access_token")) {
                    oauthAccessToken = XContentMapValues.nodeStringValue(oauth.get("access_token"), null);
                }
                if (oauth.containsKey("accessTokenSecret")) {
                    oauthAccessTokenSecret = XContentMapValues.nodeStringValue(oauth.get("accessTokenSecret"), null);
                }
                if (oauth.containsKey("access_token_secret")) {
                    oauthAccessTokenSecret = XContentMapValues.nodeStringValue(oauth.get("access_token_secret"), null);
                }
            }
            if (twitterSettings.containsKey("proxy")) {
                Map<String, Object> proxy = (Map<String, Object>) twitterSettings.get("proxy");
                if (proxy.containsKey("host")) {
                    proxyHost = XContentMapValues.nodeStringValue(proxy.get("host"), null);
                }
                if (proxy.containsKey("port")) {
                    proxyPort = XContentMapValues.nodeStringValue(proxy.get("port"), null);
                }
                if (proxy.containsKey("user")) {
                    proxyUser = XContentMapValues.nodeStringValue(proxy.get("user"), null);
                }
                if (proxy.containsKey("password")) {
                    proxyPassword = XContentMapValues.nodeStringValue(proxy.get("password"), null);
                }
            }
            streamType = XContentMapValues.nodeStringValue(twitterSettings.get("type"), "sample");
            Map<String, Object> filterSettings = (Map<String, Object>) twitterSettings.get("filter");

            if (streamType.equals("filter") && filterSettings == null) {
                stream = null;
                indexName = null;
                typeName = "status";
                bulkSize = 100;
                this.maxConcurrentBulk = 1;
                this.bulkFlushInterval = TimeValue.timeValueSeconds(5);
                logger.warn("no filter defined for type filter. Disabling river...");
                return;
            }

            if (filterSettings != null) {
                streamType = "filter";
                filterQuery = new FilterQuery();
                filterQuery.count(XContentMapValues.nodeIntegerValue(filterSettings.get("count"), 0));
                Object tracks = filterSettings.get("tracks");
                boolean filterSet = false;
                if (tracks != null) {
                    if (tracks instanceof List) {
                        List<String> lTracks = (List<String>) tracks;
                        filterQuery.track(lTracks.toArray(new String[lTracks.size()]));
                    } else {
                        filterQuery.track(Strings.commaDelimitedListToStringArray(tracks.toString()));
                    }
                    filterSet = true;
                }
                Object follow = filterSettings.get("follow");
                if (follow != null) {
                    if (follow instanceof List) {
                        List lFollow = (List) follow;
                        long[] followIds = new long[lFollow.size()];
                        for (int i = 0; i < lFollow.size(); i++) {
                            Object o = lFollow.get(i);
                            if (o instanceof Number) {
                                followIds[i] = ((Number) o).intValue();
                            } else {
                                followIds[i] = Long.parseLong(o.toString());
                            }
                        }
                        filterQuery.follow(followIds);
                    } else {
                        String[] ids = Strings.commaDelimitedListToStringArray(follow.toString());
                        long[] followIds = new long[ids.length];
                        for (int i = 0; i < ids.length; i++) {
                            followIds[i] = Long.parseLong(ids[i]);
                        }
                        filterQuery.follow(followIds);
                    }
                    filterSet = true;
                }
                Object locations = filterSettings.get("locations");
                if (locations != null) {
                    if (locations instanceof List) {
                        List lLocations = (List) locations;
                        double[][] dLocations = new double[lLocations.size()][];
                        for (int i = 0; i < lLocations.size(); i++) {
                            Object loc = lLocations.get(i);
                            double lat;
                            double lon;
                            if (loc instanceof List) {
                                List lLoc = (List) loc;
                                if (lLoc.get(0) instanceof Number) {
                                    lon = ((Number) lLoc.get(0)).doubleValue();
                                } else {
                                    lon = Double.parseDouble(lLoc.get(0).toString());
                                }
                                if (lLoc.get(1) instanceof Number) {
                                    lat = ((Number) lLoc.get(1)).doubleValue();
                                } else {
                                    lat = Double.parseDouble(lLoc.get(1).toString());
                                }
                            } else {
                                String[] sLoc = Strings.commaDelimitedListToStringArray(loc.toString());
                                lon = Double.parseDouble(sLoc[0]);
                                lat = Double.parseDouble(sLoc[1]);
                            }
                            dLocations[i] = new double[]{lon, lat};
                        }
                        filterQuery.locations(dLocations);
                    } else {
                        String[] sLocations = Strings.commaDelimitedListToStringArray(locations.toString());
                        double[][] dLocations = new double[sLocations.length / 2][];
                        int dCounter = 0;
                        for (int i = 0; i < sLocations.length; i++) {
                            double lon = Double.parseDouble(sLocations[i]);
                            double lat = Double.parseDouble(sLocations[++i]);
                            dLocations[dCounter++] = new double[]{lon, lat};
                        }
                        filterQuery.locations(dLocations);
                    }
                    filterSet = true;
                }
                Object language = filterSettings.get("language");
                if (language != null) {
                    if (filterSet) {
                        if (language instanceof List) {
                            List<String> lLanguage = (List<String>) language;
                            filterQuery.language(lLanguage.toArray(new String[lLanguage.size()]));
                        } else {
                            filterQuery.language(Strings.commaDelimitedListToStringArray(language.toString()));
                        }
                    } else {
                        indexName = null;
                        typeName = "status";
                        bulkSize = 100;
                        this.maxConcurrentBulk = 1;
                        this.bulkFlushInterval = TimeValue.timeValueSeconds(5);
                        logger.warn("can not set language filter without tracks, follow or locations. Disabling river.");
                        return;
                    }
                }
            }
        }

        logger.info("creating twitter stream river");
        if (raw && logger.isDebugEnabled()) {
            logger.debug("will index twitter raw content...");
        }

        if (oauthAccessToken == null && oauthConsumerKey == null && oauthConsumerSecret == null && oauthAccessTokenSecret == null) {
            stream = null;
            indexName = null;
            typeName = "status";
            bulkSize = 100;
            this.maxConcurrentBulk = 1;
            this.bulkFlushInterval = TimeValue.timeValueSeconds(5);
            logger.warn("no oauth specified, disabling river...");
            return;
        }

        if (settings.settings().containsKey("index")) {
            Map<String, Object> indexSettings = (Map<String, Object>) settings.settings().get("index");
            indexName = XContentMapValues.nodeStringValue(indexSettings.get("index"), riverName.name());
            typeName = XContentMapValues.nodeStringValue(indexSettings.get("type"), "status");
            this.bulkSize = XContentMapValues.nodeIntegerValue(indexSettings.get("bulk_size"), 100);
            this.bulkFlushInterval = TimeValue.parseTimeValue(XContentMapValues.nodeStringValue(
                    indexSettings.get("flush_interval"), "5s"), TimeValue.timeValueSeconds(5));
            this.maxConcurrentBulk = XContentMapValues.nodeIntegerValue(indexSettings.get("max_concurrent_bulk"), 1);
        } else {
            indexName = riverName.name();
            typeName = "status";
            bulkSize = 100;
            this.maxConcurrentBulk = 1;
            this.bulkFlushInterval = TimeValue.timeValueSeconds(5);
        }

        stream = buildTwitterStream();
    }

    /**
     * Twitter Stream Builder
     * @return
     */
    private TwitterStream buildTwitterStream() {
        logger.debug("creating TwitterStreamFactory");
        ConfigurationBuilder cb = new ConfigurationBuilder();

        cb.setOAuthConsumerKey(oauthConsumerKey)
            .setOAuthConsumerSecret(oauthConsumerSecret)
            .setOAuthAccessToken(oauthAccessToken)
            .setOAuthAccessTokenSecret(oauthAccessTokenSecret);

        if (proxyHost != null) cb.setHttpProxyHost(proxyHost);
        if (proxyPort != null) cb.setHttpProxyPort(Integer.parseInt(proxyPort));
        if (proxyUser != null) cb.setHttpProxyUser(proxyUser);
        if (proxyPassword != null) cb.setHttpProxyPassword(proxyPassword);
        if (raw) cb.setJSONStoreEnabled(true);

        // We force SSL usage
        cb.setUseSSL(true);

        TwitterStream stream = new TwitterStreamFactory(cb.build()).getInstance();
        if (streamType.equals("user")) 
        	stream.addListener(new UserStreamHandler());
        else 
        	stream.addListener(new StatusHandler());

        return stream;
    }

    /**
     * Start twitter stream
     */
    private void startTwitterStream() {
        logger.info("starting {} twitter stream", streamType);
        if (streamType.equals("filter") || filterQuery != null) {
            stream.filter(filterQuery);
        } else if (streamType.equals("firehose")) {
            stream.firehose(0);
        } else if (streamType.equals("user")) {
        	stream.user();
        } else {
            stream.sample();
        }
    }

    @Override
    public void start() {
        if (stream == null) {
            return;
        }

        try {
            // We push ES mapping only if raw is false
            if (!raw) {
                String mapping = XContentFactory.jsonBuilder().startObject().startObject(typeName).startObject("properties")
                        .startObject("location").field("type", "geo_point").endObject()
                        .startObject("language").field("type", "string").field("index", "not_analyzed").endObject()
                        .startObject("user").startObject("properties").startObject("screen_name").field("type", "string").field("index", "not_analyzed").endObject().endObject().endObject()
                        .startObject("mention").startObject("properties").startObject("screen_name").field("type", "string").field("index", "not_analyzed").endObject().endObject().endObject()
                        .startObject("in_reply").startObject("properties").startObject("user_screen_name").field("type", "string").field("index", "not_analyzed").endObject().endObject().endObject()
                        .endObject().endObject().endObject().string();
                client.admin().indices().prepareCreate(indexName).addMapping(typeName, mapping).execute().actionGet();
            }
        } catch (Exception e) {
            if (ExceptionsHelper.unwrapCause(e) instanceof IndexAlreadyExistsException) {
                // that's fine
            } else if (ExceptionsHelper.unwrapCause(e) instanceof ClusterBlockException) {
                // ok, not recovered yet..., lets start indexing and hope we recover by the first bulk
                // TODO: a smarter logic can be to register for cluster event listener here, and only start sampling when the block is removed...
            } else {
                logger.warn("failed to create index [{}], disabling river...", e, indexName);
                return;
            }
        }

        // Creating bulk processor
        this.bulkProcessor = BulkProcessor.builder(client, new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId, BulkRequest request) {
                logger.debug("Going to execute new bulk composed of {} actions", request.numberOfActions());
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
                logger.debug("Executed bulk composed of {} actions", request.numberOfActions());
                if (response.hasFailures()) {
                    logger.warn("There was failures while executing bulk", response.buildFailureMessage());
                    if (logger.isDebugEnabled()) {
                        for (BulkItemResponse item : response.getItems()) {
                            if (item.isFailed()) {
                                logger.debug("Error for {}/{}/{} for {} operation: {}", item.getIndex(),
                                        item.getType(), item.getId(), item.getOpType(), item.getFailureMessage());
                            }
                        }
                    }
                }
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                logger.warn("Error executing bulk", failure);
            }
        })
                .setBulkActions(bulkSize)
                .setConcurrentRequests(maxConcurrentBulk)
                .setFlushInterval(bulkFlushInterval)
                .build();

        startTwitterStream();
    }

    private void reconnect() {
        if (closed) {
            logger.debug("can not reconnect twitter on a closed river");
            return;
        }
        try {
            stream.cleanUp();
        } catch (Exception e) {
            logger.debug("failed to cleanup after failure", e);
        }
        try {
            stream.shutdown();
        } catch (Exception e) {
            logger.debug("failed to shutdown after failure", e);
        }
        if (closed) {
            return;
        }

        try {
            stream = buildTwitterStream();
            startTwitterStream();
        } catch (Exception e) {
            if (closed) {
                close();
                return;
            }
            // TODO, we can update the status of the river to RECONNECT
            logger.warn("failed to connect after failure, throttling", e);
            threadPool.schedule(TimeValue.timeValueSeconds(10), ThreadPool.Names.GENERIC, new Runnable() {
                @Override
                public void run() {
                    reconnect();
                }
            });
        }
    }

    @Override
    public void close() {
        this.closed = true;
        logger.info("closing twitter stream river");

        bulkProcessor.close();

        if (stream != null) {
            stream.cleanUp();
            stream.shutdown();
        }
    }

    private class StatusHandler extends StatusAdapter {

        @Override
        public void onStatus(Status status) {
            try {
                // #24: We want to ignore retweets (default to false) https://github.com/elasticsearch/elasticsearch-river-twitter/issues/24
                if (!ignoreRetweet || status.isRetweet()) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("status {} : {}", status.getUser().getName(), status.getText());
                    }

                    // If we want to index tweets as is, we don't need to convert it to JSon doc
                    if (raw) {
                        String rawJSON = DataObjectFactory.getRawJSON(status);
                        bulkProcessor.add(Requests.indexRequest(indexName).type(typeName).id(Long.toString(status.getId())).create(true).source(rawJSON));
                    } else {
                        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
                        builder.field("text", status.getText());
                        builder.field("created_at", status.getCreatedAt());
                        builder.field("source", status.getSource());
                        builder.field("truncated", status.isTruncated());
                        builder.field("language", status.getIsoLanguageCode());

                        if (status.getUserMentionEntities() != null) {
                            builder.startArray("mention");
                            for (UserMentionEntity user : status.getUserMentionEntities()) {
                                builder.startObject();
                                builder.field("id", user.getId());
                                builder.field("name", user.getName());
                                builder.field("screen_name", user.getScreenName());
                                builder.field("start", user.getStart());
                                builder.field("end", user.getEnd());
                                builder.endObject();
                            }
                            builder.endArray();
                        }

                        if (status.getRetweetCount() != -1) {
                            builder.field("retweet_count", status.getRetweetCount());
                        }

                        if (status.isRetweet() && status.getRetweetedStatus() != null) {
                            builder.startObject("retweet");
                            builder.field("id", status.getRetweetedStatus().getId());
                            if (status.getRetweetedStatus().getUser() != null) {
                                builder.field("user_id", status.getRetweetedStatus().getUser().getId());
                                builder.field("user_screen_name", status.getRetweetedStatus().getUser().getScreenName());
                                if (status.getRetweetedStatus().getRetweetCount() != -1) {
                                    builder.field("retweet_count", status.getRetweetedStatus().getRetweetCount());
                                }
                            }
                            builder.endObject();
                        }

                        if (status.getInReplyToStatusId() != -1) {
                            builder.startObject("in_reply");
                            builder.field("status", status.getInReplyToStatusId());
                            if (status.getInReplyToUserId() != -1) {
                                builder.field("user_id", status.getInReplyToUserId());
                                builder.field("user_screen_name", status.getInReplyToScreenName());
                            }
                            builder.endObject();
                        }

                        if (status.getHashtagEntities() != null) {
                            builder.startArray("hashtag");
                            for (HashtagEntity hashtag : status.getHashtagEntities()) {
                                builder.startObject();
                                builder.field("text", hashtag.getText());
                                builder.field("start", hashtag.getStart());
                                builder.field("end", hashtag.getEnd());
                                builder.endObject();
                            }
                            builder.endArray();
                        }
                        if (status.getContributors() != null && status.getContributors().length > 0) {
                            builder.array("contributor", status.getContributors());
                        }
                        if (status.getGeoLocation() != null) {
                            builder.startObject("location");
                            builder.field("lat", status.getGeoLocation().getLatitude());
                            builder.field("lon", status.getGeoLocation().getLongitude());
                            builder.endObject();
                        }
                        if (status.getPlace() != null) {
                            builder.startObject("place");
                            builder.field("id", status.getPlace().getId());
                            builder.field("name", status.getPlace().getName());
                            builder.field("type", status.getPlace().getPlaceType());
                            builder.field("full_name", status.getPlace().getFullName());
                            builder.field("street_address", status.getPlace().getStreetAddress());
                            builder.field("country", status.getPlace().getCountry());
                            builder.field("country_code", status.getPlace().getCountryCode());
                            builder.field("url", status.getPlace().getURL());
                            builder.endObject();
                        }
                        if (status.getURLEntities() != null) {
                            builder.startArray("link");
                            for (URLEntity url : status.getURLEntities()) {
                                if (url != null) {
                                    builder.startObject();
                                    if (url.getURL() != null) {
                                        builder.field("url", url.getURL());
                                    }
                                    if (url.getDisplayURL() != null) {
                                        builder.field("display_url", url.getDisplayURL());
                                    }
                                    if (url.getExpandedURL() != null) {
                                        builder.field("expand_url", url.getExpandedURL());
                                    }
                                    builder.field("start", url.getStart());
                                    builder.field("end", url.getEnd());
                                    builder.endObject();
                                }
                            }
                            builder.endArray();
                        }

                        builder.startObject("user");
                        builder.field("id", status.getUser().getId());
                        builder.field("name", status.getUser().getName());
                        builder.field("screen_name", status.getUser().getScreenName());
                        builder.field("location", status.getUser().getLocation());
                        builder.field("description", status.getUser().getDescription());
                        builder.field("profile_image_url", status.getUser().getProfileImageURL());
                        builder.field("profile_image_url_https", status.getUser().getProfileImageURLHttps());

                        builder.endObject();

                        builder.endObject();
                        bulkProcessor.add(Requests.indexRequest(indexName).type(typeName).id(Long.toString(status.getId())).create(true).source(builder));
                    }
                } else if (logger.isTraceEnabled()) {
                    logger.trace("ignoring status cause retweet {} : {}", status.getUser().getName(), status.getText());
                }

            } catch (Exception e) {
                logger.warn("failed to construct index request", e);
            }
        }

        @Override
        public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
            if (statusDeletionNotice.getStatusId() != -1) {
                bulkProcessor.add(Requests.deleteRequest(indexName).type(typeName).id(Long.toString(statusDeletionNotice.getStatusId())));
            }
        }

        @Override
        public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
            logger.info("received track limitation notice, number_of_limited_statuses {}", numberOfLimitedStatuses);
        }

        @Override
        public void onException(Exception ex) {
            logger.warn("stream failure, restarting stream...", ex);
            threadPool.generic().execute(new Runnable() {
                @Override
                public void run() {
                    reconnect();
                }
            });
        }
    }
    
    private class UserStreamHandler extends UserStreamAdapter {

    	private final StatusHandler statusHandler = new StatusHandler(); 
    	
		@Override
		public void onException(Exception ex) {
			statusHandler.onException(ex);
		}

		@Override
		public void onStatus(Status status) {
			statusHandler.onStatus(status);
		}

		@Override
		public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
			statusHandler.onDeletionNotice(statusDeletionNotice);
		}
    }
}
