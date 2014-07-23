Twitter River Plugin for Elasticsearch
==================================

The Twitter River plugin allows index twitter stream using
[Elasticsearch rivers feature](http://www.elasticsearch.org/guide/en/elasticsearch/rivers/current/index.html).

In order to install the plugin, simply run: `bin/plugin -install elasticsearch/elasticsearch-river-twitter/2.2.0`.

* For master elasticsearch versions, look at [master branch](https://github.com/elasticsearch/elasticsearch-river-twitter/tree/master).
* For 1.2.x elasticsearch versions, look at [es-1.2 branch](https://github.com/elasticsearch/elasticsearch-river-twitter/tree/es-1.2).
* For 1.0.x elasticsearch versions, look at [es-1.0 branch](https://github.com/elasticsearch/elasticsearch-river-twitter/tree/es-1.0).
* For 0.90.x elasticsearch versions, look at [es-0.90 branch](https://github.com/elasticsearch/elasticsearch-river-twitter/tree/es-0.90).

|    Twitter River Plugin    |    elasticsearch    | Release date |
|----------------------------|---------------------|:------------:|
| 2.2.0                      | 1.2.0 -> 1.2        |  2014-07-23  |

Please read documentation relative to the version you are using:

* [2.2.0](https://github.com/elasticsearch/elasticsearch-river-twitter/blob/v2.2.0/README.md)

The twitter river indexes the public [twitter stream](http://dev.twitter.com/pages/streaming_api), aka the hose,
and makes it searchable.

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

```
PUT _river/my_twitter_river/_meta
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
        "bulk_size" : 100,
        "flush_interval" : "5s"
    }
}
```

The above lists all the options controlling the creation of a twitter river.

If you don't define `index.index`, it will use your river name (`my_twitter_river`) as the default index name.
If you don't define `index.type`, default `status` type will be used. 

Note that you can define any or all of your oauth settings in `elasticsearch.yml` file on each node by prefixing 
setting with `river.twitter.`:

```
river.twitter.oauth.consumer_key: "*** YOUR Consumer key HERE ***"
river.twitter.oauth.consumer_secret: "*** YOUR Consumer secret HERE ***"
river.twitter.oauth.access_token: "*** YOUR Access token HERE ***"
river.twitter.oauth.access_token_secret: "*** YOUR Access token secret HERE ***"
```

In that case, you can create the river using:

```
PUT _river/my_twitter_river/_meta
{
    "type" : "twitter"
}
```

You can also overload any of `elasticsearch.yml` setting. A good practice could be to have `consumer_key` and 
`consumer_secret` in `elasticsearch.yml` and provide to the river `access_token` and `access_token_secret` properties.

By default, the twitter river will read a small random of all public statuses using
[sample API](https://dev.twitter.com/docs/api/1.1/get/statuses/sample).

But, you can define statuses type you want to read:

* [sample](https://dev.twitter.com/docs/api/1.1/get/statuses/sample): the default one
* [filter](https://dev.twitter.com/docs/api/1.1/post/statuses/filter): track for text, users and locations.
See [Filtered Stream](#filtered-stream)
* [user](https://dev.twitter.com/docs/streaming-apis/streams/user): listen to tweets in the authenticated user's timeline.
See [User Stream](#user-stream)
* [firehose](https://dev.twitter.com/docs/api/1.1/get/statuses/firehose): all public statuses (restricted access)

For example:

```
PUT _river/my_twitter_river/_meta
{
    "type" : "twitter",
    "twitter" : {
        "type" : "firehose"
    }
}
```

Note that if you define a filter (see [next section](#filtered-stream)), type will be automatically set to `filter`.

Tweets will be indexed once a `bulk_size` of them have been accumulated (default to `100`)
or every `flush_interval` period (default to `5s`).

Filtered Stream
===============

Filtered stream can also be supported (as per the twitter stream API). Filter stream can be configured to
support `tracks`, `follow`, `locations` and `language`. The configuration is the same as the twitter API (a single comma
separated string value, or using json arrays). Here is an example:

```
PUT _river/my_twitter_river/_meta
{
    "type" : "twitter",
    "twitter" : {
        "filter" : {
            "tracks" : "test,something,please",
            "follow" : "111,222,333",
            "locations" : "-122.75,36.8,-121.75,37.8,-74,40,-73,41",
            "language" : "fr,en"
        }
    }
}
```

Note that locations use geoJSON order (longitude, latitude).

Note that if you want to use language filtering you need also to define at least one of `tracks`,
`follow` or `locations` filter.
Supported languages identifiers are [BCP 47](http://tools.ietf.org/html/bcp47). You can filter
whatever language defined in [Twitter Advanced Search](https://twitter.com/search-advanced).

Here is an array based configuration example:

```
PUT _river/my_twitter_river/_meta
{
    "type" : "twitter",
    "twitter" : {
        "filter" : {
            "tracks" : ["test", "something"],
            "follow" : [111, 222, 333],
            "locations" : [ [-122.75,36.8], [-121.75,37.8], [-74,40], [-73,41]],
            "language" : [ "fr", "en" ]
        }
    }
}
```

User Stream
===========

User stream can also be supported (as per the twitter stream API). This stream return tweets on the authenticated user's
timeline. Here is a basic configuration example:

```
PUT _river/my_twitter_river/_meta
{
    "type" : "twitter",
    "twitter" : {
        "type" : "user"
    }
}
```

Indexing RAW Twitter stream
===========================

By default, elasticsearch twitter river will convert tweets to an equivalent representation
in elasticsearch. If you want to index RAW twitter JSON content without any transformation,
you can set `raw` to `true`:

```
PUT _river/my_twitter_river/_meta
{
    "type" : "twitter",
    "twitter" : {
        "raw" : true
    }
}
```

Note that you should think of creating a mapping first for your tweets. See Twitter documentation on
[raw Tweet format](https://dev.twitter.com/docs/platform-objects/tweets):

```
PUT my_twitter_river/status/_mapping
{
    "status" : {
        "properties" : {
            "text" : {"type" : "string", "analyzer" : "standard"}
        }
    }
}
```

Ignoring Retweets
=================

If you don't want to index retweets (aka RT), just set `ignore_retweet` to `true` (default to `false`):

```
PUT _river/my_twitter_river/_meta
{
    "type" : "twitter",
    "twitter" : {
        "ignore_retweet" : true
    }
}
```

Geo location points as array
============================

By default, elasticsearch twitter river index `location` field using the *lat lon as properties* format.
You can set `geo_as_array` to `true` if you prefer having `location` indexed as an array `[lon, lat]`.

```
PUT _river/my_twitter_river/_meta
{
    "type" : "twitter",
    "twitter" : {
        "geo_as_array" : true
    }
}
```

Remove the river
================

If you need to stop the Twitter river, you have to remove it:

```
DELETE _river/my_twitter_river/
```

Tests
=====

Integrations tests in this plugin require working Twitter account and therefore disabled by default. 
You need to create your credentials as explained in [Prerequisites](#prerequisites).

To enable tests prepare a config file `elasticsearch.yml` with the following content:

```
river:
  twitter:
      oauth:
         consumer_key: "your_consumer_key"
         consumer_secret: "your_consumer_secret"
         access_token: "your_access_token"
         access_token_secret: "your_access_token_secret"
```

Replace all occurrences of `your_consumer_key`, `your_consumer_secret`, `your_access_token` and 
`your_access_token_secret` with your settings.

To run test:

```sh
mvn -Dtests.twitter=true -Dtests.config=/path/to/config/file/elasticsearch.yml clean test
```

Note that if you want to test User Stream, you need to define write rights for your twitter 
application.

License
-------

    This software is licensed under the Apache 2 license, quoted below.

    Copyright 2009-2014 Elasticsearch <http://www.elasticsearch.org>

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy of
    the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
    License for the specific language governing permissions and limitations under
    the License.
