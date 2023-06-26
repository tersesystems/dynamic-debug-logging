package com.example;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import com.tersesystems.echopraxia.api.*;
import com.tersesystems.echopraxia.spi.*;
import com.tersesystems.echopraxia.scripting.ScriptCondition;
import com.tersesystems.echopraxia.scripting.ScriptHandle;

import org.slf4j.Logger;

import redis.clients.jedis.*;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.providers.PooledConnectionProvider;
import redis.clients.jedis.resps.ScanResult;

public class JedisFilter implements CoreLoggerFilter, AutoCloseable {

  private final JedisPooled client;
  private static final Logger logger = org.slf4j.LoggerFactory.getLogger(JedisFilter.class);

  private final LoadingCache<String, Condition> cache;

  public JedisFilter() {
    HostAndPort config = new HostAndPort("redis", 6379);
    PooledConnectionProvider provider = new PooledConnectionProvider(config);
    client = new JedisPooled(provider);

    // Set up cache and refresh
    cache =
        Caffeine.newBuilder()
            .maximumSize(10_000)
            .refreshAfterWrite(Duration.ofSeconds(1)) // refresh after every cache access
            .build(this::queryRedis);

    // Load up a starting set of keys from cache
    Set<String> keys = getKeysFromRedis();
    cache.getAll(keys);
  }

  private Set<String> getKeysFromRedis() {
    ScanParams scanParams = new ScanParams().count(1000);
    Set<String> allKeys = new HashSet<>();
    String cur = ScanParams.SCAN_POINTER_START;
    do {
      ScanResult<String> scanResult = client.scan(cur, scanParams);
      allKeys.addAll(scanResult.getResult());
      cur = scanResult.getCursor();
      if (allKeys.size() >= 1000) break;
    } while (!cur.equals(ScanParams.SCAN_POINTER_START));

    return allKeys;
  }

  private Condition queryRedis(String key) {
    String script = client.get(key);
    // the default condition value is false, so it will not log 
    // if the script cannot be loaded.
    return script != null ? ScriptCondition.create(false, script, e -> logger.error("Cannot compile script!", e)) : null;
  }

  public void close() {
    client.close();
  }

  @Override
  public CoreLogger apply(CoreLogger coreLogger) {
    Condition condition = new JedisCondition(coreLogger.getName());
    return coreLogger.withCondition(condition);
  }

  class JedisCondition implements Condition {
    private final String name;

    public JedisCondition(String name) {
      this.name = name;
    }

    @Override
    public boolean test(Level level, LoggingContext context) {
      final Condition scriptCondition = cache.get(name);
      if (scriptCondition != null) {
        return scriptCondition.test(level, context);
      }
      // if no script, only return true if "ERROR" / "WARN" / "INFO"
      return Condition.operational().test(level, context);
    }
  }
}
