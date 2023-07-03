//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.springframework.boot:spring-boot-starter-web:2.6.7
//DEPS org.springframework.boot:spring-boot-starter-actuator:2.6.7
//DEPS com.tersesystems.echopraxia:logger:3.0.1
//DEPS com.tersesystems.echopraxia:logstash:3.0.1
//DEPS com.tersesystems.echopraxia:scripting:3.0.1
//DEPS ch.qos.logback:logback-classic:1.4.8
//DEPS com.tersesystems.blacklite:blacklite-logback:1.2.2
//DEPS com.tersesystems.logback:logback-uniqueid-appender:1.0.3
//DEPS redis.clients:jedis:4.1.1
//DEPS com.github.ben-manes.caffeine:caffeine:2.9.3
//SOURCES JedisFilter.java
//FILES logback.xml
//FILES echopraxia.properties
//FILES application.properties

package com.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;

import com.tersesystems.echopraxia.Logger;
import com.tersesystems.echopraxia.LoggerFactory;
import com.tersesystems.echopraxia.api.*;

import java.time.LocalDateTime;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  private final Logger<HttpRequestFieldBuilder> logger = LoggerFactory.getLogger(Application.class, new HttpRequestFieldBuilder())
      .withFields(
        fb -> {
          // Any fields that you set in context you can set conditions on later,
          // i.e. on the URI path, content type, or extra headers.
          // These fields will be visible in the JSON file, not shown in console.
          return fb.requestFields(httpServletRequest());
        });

  @RestController
  public class GreetingController {
    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/")
    public Greeting greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
      logger.debug("greeting param {}", fb -> fb.string("name", name));
      return new Greeting(counter.incrementAndGet(), String.format(template, name));
    }
  }

  private HttpServletRequest httpServletRequest() {
    return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
  }

  public static class HttpRequestFieldBuilder implements FieldBuilder {
    public FieldBuilderResult requestFields(HttpServletRequest request) {
      Field urlField = string("request_uri", request.getRequestURI());
      Field methodField = string("request_method", request.getMethod());
      Field remoteAddressField = string("request_remote_addr", request.getRemoteAddr());
      return list(urlField, methodField, remoteAddressField);
    }
  }

  public static class Greeting {

    private final long id;
    private final String content;

    public Greeting(long id, String content) {
      this.id = id;
      this.content = content;
    }

    public long getId() {
      return id;
    }

    public String getContent() {
      return content;
    }
  }
}
