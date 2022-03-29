# Dynamic Debug Logging using Docker Compose

This is a proof of concept that shows an application running with dynamic debug logging.

## Rationale

Assume that you have an application running in production.  

You want to enable some debug statements in the application, but only some of them.  You could be testing out a new feature, hunting down a bug, or verifying the execution flow.

Once you have these debugging statements, you want to dump them out of the application and examine them in detail, without going through your operational logging stack (ELK, Splunk, etc).

## Overview

The Spring Boot application in `app` runs and conditionally produces debug statements on every request.  The condition attached to the logger runs using a cached reference to a [Tweakflow script](https://github.com/tersesystems/echopraxia#dynamic-conditions-with-scripts) on Redis, and looks up the script at `com.example.Application`. If the script evaluation returns true, then the logger will produce a statement -- if there is no script, then the logger will operationally log at `INFO` and above.

Logging output is written to a bounded SQLite database at `/app/app.db`.  This database is observed by [litestream](https://litestream.io/) which asynchronously replicates changes to the S3 location in [localstack](https://github.com/localstack/localstack).

Finally, the litestream replication data can be called to restore a database from localstack, making it available to download outside the application.

## Running

You can run the docker compose file as normal once you've installed it.

```
docker-compose up --build
```

And the local application will be available at [`http://localhost:8080/`](http://localhost:8080).  Hitting the URL will call `logger.debug`, but nothing will be logged until a script is present and returning `true`.

During this time, the application will send operational logging output to an SQLite database, and that database will be asynchronously replicated to a localstack S3 bucket using [litestream](litestream.io/).

## Editing

To change the script, go to [`http://localhost:8081`](http://localhost:8081) to run Redis Commander.

Enter the key value as `com.example.Application` with the following [Tweakflow script](https://github.com/tersesystems/echopraxia#dynamic-conditions-with-scripts) as value:

```
library echopraxia {
  function evaluate: (string level, dict ctx) ->
    true;
}
```

![redis-commander.png](redis-commander.png)

You can also search for conditions matching against arguments.  For example, using `find_string`:

```
library echopraxia {
  function evaluate: (string level, dict ctx) ->
    let {
      find_string: ctx[:find_string];
    }
    find_string("$.name") == "World";
}
```

## Download

To download and extract the litestream replication data into an SQLite database from localstack S3, you can go to [`http://localhost:9001/`](http://localhost:9001).  A Spring Boot application will call [`litestream restore`](https://litestream.io/reference/restore/) and provide the SQLite database for download.

## Analysis

Once you've downloaded the database, you can use [DB Browser for SQLite](https://sqlitebrowser.org/) to query the database, including JSON in the `content` column using [`json_extract`](https://www.sqlite.org/json1.html#jex):

```sql
select 
	json_extract(content, "$.id") as id, 
	json_extract(content, "$.logger_name") as logger_name,
	json_extract(content, "$.@timestamp") as timestamp,
	json_extract(content, "$.message") as message
FROM entries
```

You also have the option of visualizing the sqlite database using [Datasette](https://datasette.io/) or [Observable](observablehq.com/) using [SQL + Chart](https://observablehq.com/@observablehq/sql-chart).
