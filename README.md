Twitter River Plugin for ElasticSearch
==================================

The Twitter River plugin allows index twitter stream using
[Elasticsearch rivers feature](http://www.elasticsearch.org/guide/reference/river/).

In order to install the plugin, simply run: `bin/plugin -install elasticsearch/elasticsearch-river-twitter/1.4.0`.

    ---------------------------------------------------
    | Twitter Plugin          | ElasticSearch         |
    ---------------------------------------------------
    | 1.5.0-SNAPSHOT (master) | 0.20-0.90 -> master   |
    ---------------------------------------------------
    | 1.4.0                   | 0.20-0.90 -> master   |
    ---------------------------------------------------
    | 1.3.0                   | 0.20-0.90 -> master   |
    ---------------------------------------------------
    | 1.2.0                   | 0.19                  |
    ---------------------------------------------------
    | 1.1.0                   | 0.19                  |
    ---------------------------------------------------
    | 1.0.0                   | 0.18                  |
    ---------------------------------------------------

The twitter river indexes the public [twitter stream](http://dev.twitter.com/pages/streaming_api), aka the hose,
and makes it searchable.

**Breaking news**: [Twitter does not support anymore HTTP user/password
authentication](https://twitter.com/twitterapi/status/344574437674328064).
You now must use [OAuth API](https://dev.twitter.com/docs/auth/obtaining-access-tokens).

Prerequisites
-------------

You need to get an OAuth token in order to use Twitter river.
Please follow [Twitter documentation](https://dev.twitter.com/docs/auth/tokens-devtwittercom), basically:

* Login to: https://dev.twitter.com/apps/
* Create a new Twitter application (let's say elasticsearch): https://dev.twitter.com/apps/new
You don't need a callback URL.
* When done, click on `Create my access token`.
* Open `OAuth tool` tab and note `Consumer key`, `Consumer secret`, `Access token` and `Access token secret`.


Create river
------------

Creating the twitter river can be done using:

```sh
curl -XPUT localhost:9200/_river/my_twitter_river/_meta -d '
{
    "type" : "twitter",
    "twitter" : {
        "oauth" : {
            "consumer_key" : "*** YOUR Consumer key HERE ***",
            "consumer_secret" : "*** YOUR Consumer secret HERE ***",
            "access_token" : "*** YOUR Access token HERE ***",
            "access_token_secret" : "*** YOUR Access token secret HERE ***"
        }
    },
    "index" : {
        "index" : "my_twitter_river",
        "type" : "status",
        "bulk_size" : 100
    }
}
'
```

The above lists all the options controlling the creation of a twitter river.

By default, the twitter river will read a small random of all public statuses using
[sample API](https://dev.twitter.com/docs/api/1.1/get/statuses/sample).

But, you can define statuses type you want to read:

* [sample](https://dev.twitter.com/docs/api/1.1/get/statuses/sample): the default one
* [filter](https://dev.twitter.com/docs/api/1.1/post/statuses/filter): track for text, users and locations.
See [Filtered Stream](#filtered-stream)
* [firehose](https://dev.twitter.com/docs/api/1.1/get/statuses/firehose): all public statuses (restricted access)

For example:

```sh
curl -XPUT localhost:9200/_river/my_twitter_river/_meta -d '
{
    "type" : "twitter",
    "twitter" : {
        "oauth" : {
            "consumer_key" : "*** YOUR Consumer key HERE ***",
            "consumer_secret" : "*** YOUR Consumer secret HERE ***",
            "access_token" : "*** YOUR Access token HERE ***",
            "access_token_secret" : "*** YOUR Access token secret HERE ***"
        },
        "type" : "firehose"
    }
}
'
```

Note that if you define a filter (see [next section](#filtered-stream)), type will be automatically set to `filter`.

Tweets will be indexed once a `bulk_size` of them have been accumulated.

Filtered Stream
===============

Filtered stream can also be supported (as per the twitter stream API). Filter stream can be configured to
support `tracks`, `follow`, and `locations`. The configuration is the same as the twitter API (a single comma
separated string value, or using json arrays). Here is an example:

```javascript
{
    "type" : "twitter",
    "twitter" : {
        "oauth" : {
            "consumer_key" : "*** YOUR Consumer key HERE ***",
            "consumer_secret" : "*** YOUR Consumer secret HERE ***",
            "access_token" : "*** YOUR Access token HERE ***",
            "access_token_secret" : "*** YOUR Access token secret HERE ***"
        },
        "filter" : {
            "tracks" : "test,something,please",
            "follow" : "111,222,333",
            "locations" : "-122.75,36.8,-121.75,37.8,-74,40,-73,41"
        }
    }
}
```

Here is an array based configuration example:

```javascript
{
    "type" : "twitter",
    "twitter" : {
        "oauth" : {
            "consumer_key" : "*** YOUR Consumer key HERE ***",
            "consumer_secret" : "*** YOUR Consumer secret HERE ***",
            "access_token" : "*** YOUR Access token HERE ***",
            "access_token_secret" : "*** YOUR Access token secret HERE ***"
        },
        "filter" : {
            "tracks" : ["test", "something"],
            "follow" : [111, 222, 333],
            "locations" : [ [-122.75,36.8], [-121.75,37.8], [-74,40], [-73,41]]
        }
    }
}
```

Indexing RAW Twitter stream
===========================

By default, elasticsearch twitter river will convert tweets to an equivalent representation
in elasticsearch. If you want to index RAW twitter JSON content without any transformation,
you can set `raw` to `true`:

```sh
curl -XPUT localhost:9200/_river/my_twitter_river/_meta -d '
{
    "type" : "twitter",
    "twitter" : {
        "oauth" : {
            "consumer_key" : "*** YOUR Consumer key HERE ***",
            "consumer_secret" : "*** YOUR Consumer secret HERE ***",
            "access_token" : "*** YOUR Access token HERE ***",
            "access_token_secret" : "*** YOUR Access token secret HERE ***"
        },
        "raw" : true
    }
}
'
```

Note that you should think of creating a mapping first for your tweets. See Twitter documentation on
[raw Tweet format](https://dev.twitter.com/docs/platform-objects/tweets):

```sh
curl -XPUT localhost:9200/my_twitter_river/status/_mapping -d '
{
    "status" : {
        "properties" : {
            "text" : {"type" : "string", "analyzer" : "standard"}
        }
    }
}
'
```

Ignoring Retweets
=================

If you don't want to index retweets (aka RT), just set `ignore_retweet` to `true` (default to `false`):

```sh
curl -XPUT localhost:9200/_river/my_twitter_river/_meta -d '
{
    "type" : "twitter",
    "twitter" : {
        "oauth" : {
            "consumer_key" : "*** YOUR Consumer key HERE ***",
            "consumer_secret" : "*** YOUR Consumer secret HERE ***",
            "access_token" : "*** YOUR Access token HERE ***",
            "access_token_secret" : "*** YOUR Access token secret HERE ***"
        },
        "ignore_retweet" : true
    }
}
'
```


Remove the river
================

If you need to stop the Twitter river, you have to remove it:

```sh
curl -XDELETE http://localhost:9200/_river/my_twitter_river/
```


License
-------

    This software is licensed under the Apache 2 license, quoted below.

    Copyright 2009-2013 Shay Banon and ElasticSearch <http://www.elasticsearch.org>

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy of
    the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
    License for the specific language governing permissions and limitations under
    the License.
