Twitter River Plugin for ElasticSearch
==================================

The Twitter River plugin allows index twitter stream.

In order to install the plugin, simply run: `bin/plugin -install elasticsearch/elasticsearch-river-twitter/1.0.0`.

    -------------------------------------
    | Twitter Plugin | ElasticSearch    |
    -------------------------------------
    | master         | master (0.19)    |
    -------------------------------------
    | 1.0.0          | 0.18 -> master   |
    -------------------------------------

The twitter river indexes the public [twitter stream](http://dev.twitter.com/pages/streaming_api), aka the hose, and makes it searchable.

Creating the twitter river can be done using:

	curl -XPUT localhost:9200/_river/my_twitter_river/_meta -d '
	{
	    "type" : "twitter",
	    "twitter" : {
	        "user" : "twitter_user",
	        "password" : "twitter_passowrd"
	    },
	    "index" : {
	        "index" : "my_twitter_river",
	        "type" : "status",
	        "bulk_size" : 100
	    }
	}
	'

The above lists all the options controlling the creation of a twitter river. The user and password are required in order to connect to the twitter stream.

Tweets will be indexed once a `bulk_size` of them have been accumulated.

Filtered Stream
===============

Filtered stream can also be supported (as per the twitter stream API). Filter stream can be configured to support `tracks`, `follow`, and `locations`. The configuration is the same as the twitter API (a single comma separated string value, or using json arrays). Here is an example:

	{
	    "type" : "twitter",
	    "twitter" : {
	        "user" : "me",
	        "password" : "123456",
	        "filter" : {
	            "tracks" : "test,something,please",
	            "follow" : "111,222,333",
	            "locations" : "-122.75,36.8,-121.75,37.8,-74,40,-73,41"
	        }
	    }
	}

Here is an array based configuration example:

	{
	    "type" : "twitter",
	    "twitter" : {
	        "user" : "me",
	        "password" : "123456",
	        "filter" : {
	            "tracks" : ["test", "something"],
	            "follow" : [111, 222, 333],
	            "locations" : [ [-122.75,36.8], [-121.75,37.8], [-74,40], [-73,41]]
	        }
	    }
	}
