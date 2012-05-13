/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.river.twitter;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.FilterBuilders.missingFilter;
import static org.elasticsearch.index.query.FilterBuilders.notFilter;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import junit.framework.Assert;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

public class TwitterRiverTest {
  
  protected final static ESLogger logger = Loggers.getLogger(TwitterRiverTest.class);
  private static String           _username;
  private static String           _password;
  
  public static void main(String[] args) throws Exception {
    if (null == args || args.length != 2) {
      System.err.println("Please provide a username and password for Twitter.");
      System.exit(1);
    }
    _username = args[0];
    _password = args[1];
    Node node = NodeBuilder.nodeBuilder()
                           .settings(ImmutableSettings.settingsBuilder().put("gateway.type", "none"))
                           .node();
    
    XContentBuilder riverDefinition = jsonBuilder().startObject()
                                                   .field("type", "twitter")
                                                   .field("twitter")
                                                     .startObject()
                                                     .field("user", _username)
                                                     .field("password", _password)
                                                     .field("full", true)
                                                     .endObject()
                                                   .endObject();
    logger.info("River Definition: {}", riverDefinition.prettyPrint().string());
    
    node.client()
        .prepareIndex("_river", "twitter_river_test", "_meta")
        .setSource(riverDefinition)
        .execute()
        .actionGet();
    
    Thread.sleep(10000);
    
    QueryBuilder qb = constantScoreQuery(notFilter(missingFilter("user.followers_count")));
    logger.info("Query JSON: {}", ((BaseQueryBuilder) qb).toString());
    SearchResponse response = node.client()
                                  .prepareSearch("twitter_river_test")
                                  .setQuery(qb)
                                  .setFrom(0)
                                  .setSize(10)
                                  .execute()
                                  .actionGet();
    
    logger.info("Found {} hits so far", response.hits().getTotalHits());
    Assert.assertTrue("Was expecting documents with the user.followers_count field present",
                      response.hits().getTotalHits() > 0);
    
    node.close();
  }
}
