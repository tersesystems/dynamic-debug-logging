# Dynamic Debug Logging

This is a proof of concept using docker compose that shows an application running with dynamic debug logging -- logging that can be targeted to log only a particular session id or IP address at runtime, without restarting the application.

## Rationale

Assume that you have an application running in production.  

You want to enable some debug statements in the application, but only some of them.  You could be testing out a new feature, hunting down a bug, or verifying the execution flow.

Once you have these debugging statements, you want to dump them out of the application and examine them in detail, without going through your operational logging stack (ELK, Splunk, etc).  You may want to pull logs from multiple instances or multiple services and make them all available at once so you can track the flow across logs.

### Why SQLite and LiteStream instead of an Observability Stack?

From a [reddit reply](https://www.reddit.com/r/java/comments/u547xw/dynamic_debug_logging/i59u913):

> Why wouldn’t you want to use your logging stack?

It's certainly not a requirement, but using the logging stack for debugging can be inconvenient, because production stacks have different parameters: 

* Operational logs may be retained for much longer than diagnostic logs
* Different cost centers (justification for huge diagnostic dumps in budget)
* Expected to be consistent (large surges in logs cause concern)
* Expected to be available (large surges can cause delays in the pipeline)

In addition, the operational logging pipeline may have additional security and auditing restraints depending on the audience.  All of this of course varies depending on the organization, teams, and data.

Implementing a second logging stack for diagnostics is primarily to keep it out of the way of operational logging, so ops is not "polluted" by diagnostic logs and there can be more control over how the diagnostic logs are processed and accessed.  Because diagnostic logs from production can contain sensitive info and internal state dumps, it's very important to be able to control that.

> Rather than use your logging stack, you implement a second logging stack. Backed by… sql lite? It seems like you’ve added a lot of features here for marginal utility, compared to rotating log files and uploading them to s3 in the background.

The blunt reason is that if logs aren't going through a stack, then there needs to be some other way to organize and search that data, preferably something that's self-contained, well supported, portable across multiple platforms, and is a "well known no surprises" technology. 

 I do think that SQLite has a [number of advantages](https://tersesystems.com/blog/2020/11/26/queryable-logging-with-blacklite/) over flat files, especially for diagnostic purposes:

* Embedded and standalone database, great for diagnostic backtraces.
* Sorting logs by multiple criteria is super useful in diagnostic logs that may take place within a millisecond -- under Logback's logging framework resolution.
* Immediate GUI support.
* Built-in JSON support.
* Filtering / Processing data to scrub and replace PII involves SQL, not JSON processing or jq.
* Ability to manage and import multiple sources (joining across flat files is a pain).
* replicating with livestream via WAL and snapshots is (arguably) cleaner than file rollovers and s3 appends.

Again, YMMV and writing as JSON flat-file / Cribl / Kinesis Firehose are all valid choices.

## Overview

The Spring Boot application in `app` runs and conditionally produces debug statements on every request, using a structured logging framework called [Echopraxia](https://github.com/tersesystems/echopraxia).  

The condition attached to the logger runs using a cached reference to a [Tweakflow script](https://github.com/tersesystems/echopraxia#dynamic-conditions-with-scripts) on Redis, and looks up the script at `com.example.Application`. If the script evaluation returns true, then the logger will produce a statement -- if there is no script, then the logger will operationally log at `INFO` and above.  The cache will [refresh](https://github.com/ben-manes/caffeine/wiki/Refresh) from Redis asynchronously, so if the script has changed on Redis then the cache will be updated.  

Tweakflow is designed to be [limited and secure](https://twineworks.github.io/tweakflow/#why-tweakflow) in what data it accesses, and it is possible to [limit execution time](https://twineworks.github.io/tweakflow/embedding.html#limiting-evaluation-time) to prevent denial of service, using an `AsyncLogger`.

Logging output is written to a bounded, row-limited SQLite database at `/app/app.db` using [Blacklite](https://github.com/tersesystems/blacklite).  This database is observed by [litestream](https://litestream.io/) which asynchronously replicates changes to the S3 location in [localstack](https://github.com/localstack/localstack), ensuring that the logs are available in a secure and reliable location.

Finally, the litestream replication data can be called from another Spring Boot application `download` to restore a database from localstack, making logs available for use outside the application.

Here's a picture showing a happy user getting their logs.

![workflow.png](images/workflow.png)

## Running

You can run the docker compose file as normal once you've [installed it](https://docs.docker.com/compose/install/).

```
docker-compose up --build
```

And the local application will be available at [`http://localhost:8080/`](http://localhost:8080).  Hitting the URL will call `logger.debug`, but nothing will be logged until a script is present and returning `true`.

During this time, the application will send operational logging output to an SQLite database, and that database will be asynchronously replicated to a localstack S3 bucket using [litestream](litestream.io/).

## Editing

You can change the logging levels of the application by using [http://localhost:8080/actuator/loggers](http://localhost:8080/actuator/loggers) which uses [Actuator](https://docs.spring.io/spring-boot/docs/2.5.6/reference/html/actuator.html#actuator.loggers).

To change the script, go to [`http://localhost:8081`](http://localhost:8081) to run Redis Commander.

Enter the key value as `com.example.Application` with the following [Tweakflow script](https://github.com/tersesystems/echopraxia#dynamic-conditions-with-scripts) as value:

```
library echopraxia {
  function evaluate: (string level, dict ctx) ->
    true;
}
```

![redis-commander.png](images/redis-commander.png)

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

You also have the option of visualizing the sqlite database using [Datasette](https://datasette.io/) or [Observable](observablehq.com/) using [SQL + Chart](https://observablehq.com/@observablehq/sql-chart), or querying using a [notebook interface](https://tersesystems.com/blog/2019/09/28/applying-data-science-to-logs-for-developer-observability/).

## Scaling

Finally, although it's not shown here, you can leverage SQLite dump and import tools to aggregate multiple logs together into a single database with multiple tables, and use `UNION` to query across all multiple tables.   
