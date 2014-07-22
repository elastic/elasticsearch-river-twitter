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

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import java.io.File;
import java.io.IOException;

/**
 * To run this test you have to provide your twitter oauth credentials.
 * You can also define test duration in seconds (default to 10)
 */
public abstract class TwitterRiverAbstractTest {

    protected abstract XContentBuilder addSpecificRiverSettings(XContentBuilder xb) throws IOException;

    protected String oauth_consumer_key = null;
    protected String oauth_consumer_secret = null;
    protected String oauth_access_token = null;
    protected String oauth_access_token_secret = null;
    protected String track = "obama";
    protected long duration = 10;

    protected XContentBuilder riverSettings() throws IOException {
        XContentBuilder xb = XContentFactory.jsonBuilder()
            .startObject()
                .field("type", "twitter");

        // We inject specific test settings here
        xb = addSpecificRiverSettings(xb);

        xb.endObject();
        return xb;
    }

    public void launcher(String[] args) throws InterruptedException, IOException {
        // Checking args
        if (args.length < 2) {

        }

        try {
            for (int c = 0; c < args.length; c++) {
                String command = args[c];
                if (command.equals("-k") || command.equals("--consumer_key")) {
                    oauth_consumer_key = args[++c];
                } else if (command.equals("-s") || command.equals("--consumer_secret")) {
                    oauth_consumer_secret = args[++c];
                } else if (command.equals("-t") || command.equals("--access_token")) {
                    oauth_access_token = args[++c];
                } else if (command.equals("-a") || command.equals("--access_token_secret")) {
                    oauth_access_token_secret = args[++c];
                } else if (command.equals("-d") || command.equals("--duration")) {
                    duration = Long.parseLong(args[++c]);
                } else if (command.equals("-t") || command.equals("--track")) {
                    track = args[++c];
                } else if (command.equals("-h") || command.equals("--help")) {
                    displayHelp(null);
                } else {
                    displayHelp("Command [" + args[c] + "] unknown.");
                    // Unknown command. We break...
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            displayHelp("Error while installing running test, reason: " + e.getClass().getSimpleName() +
                    ": " + e.getMessage());
            System.exit(1);
        }

        if (oauth_consumer_key == null || oauth_consumer_secret == null
                || oauth_access_token == null || oauth_access_token_secret == null) {
            displayHelp("OAuth credentials need to be set.");
            System.exit(1);
        }

        // First we delete old datas...
        File dataDir = new File("./target/es/data");
        if(dataDir.exists()) {
            FileSystemUtils.deleteRecursively(dataDir, true);
        }

        // Then we start our node for tests
        Node node = NodeBuilder
                .nodeBuilder()
                .local(true)
                .settings(
                        ImmutableSettings.settingsBuilder()
                                .put("gateway.type", "local")
                                .put("path.data", "./target/es/data")
                                .put("path.logs", "./target/es/logs")
                                .put("path.work", "./target/es/work")
                                .put("river.twitter.oauth.consumer_key", oauth_consumer_key)
                                .put("river.twitter.oauth.consumer_secret", oauth_consumer_secret)
                                .put("river.twitter.oauth.access_token", oauth_access_token)
                                .put("river.twitter.oauth.access_token_secret", oauth_access_token_secret)
                ).node();

        // We wait now for the yellow (or green) status
        node.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();

        // We clean existing rivers
        try {
            node.client().admin().indices()
                    .delete(new DeleteIndexRequest("_river")).actionGet();
            // We wait for one second to let ES delete the river
            Thread.sleep(1000);
        } catch (IndexMissingException e) {
            // Index does not exist... Fine
        }

        node.client().prepareIndex("_river", "twitter", "_meta").setSource(riverSettings()).execute().actionGet();

        Thread.sleep(duration * 1000);

        // We remove the river as well. Not mandatory here as the JVM will stop
        // but it's an example on how to remove a running river (and call close() method).
        node.client().admin().indices().prepareDeleteMapping("_river").setType("twitter").execute().actionGet();
    }

    private static void displayHelp(String message) {
        System.out.println("Usage:");
        System.out.println("    -k, --consumer_key        [key]          : Set your twitter user account");
        System.out.println("    -s, --consumer_secret     [secret]       : Set your twitter password");
        System.out.println("    -t, --access_token        [token]        : Set your twitter password");
        System.out.println("    -a, --access_token_secret [token secret] : Set your twitter password");
        System.out.println("    -d, --duration            [duration]     : Set test duration in seconds (default to 10)");
        System.out.println("    -t, --track               [term]         : Term you want to track (default to obama)");
        System.out.println("    -h, --help                               : Prints this help message");
        System.out.println();

        if (message != null) {
            System.out.println();
            System.out.println("Message:");
            System.out.println("   " + message);
        }
    }
}
