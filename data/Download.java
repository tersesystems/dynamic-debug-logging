//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.springframework.boot:spring-boot-starter-web:2.3.4.RELEASE

package com.example;

import java.io.*;
import java.nio.charset.*;
import java.sql.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.net.*;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;

import org.slf4j.*;

@SpringBootApplication
public class Download {

  private static final Logger logger = LoggerFactory.getLogger(Download.class);

  public static void main(String[] args) throws Exception {
    SpringApplication.run(Data.class, args);
  }

  @RestController
  public class DownloadController {
    @GetMapping("/")
    public ResponseEntity restore() {
      logger.info("restore: ");
      try {
        String dbPath = System.getenv("DB_PATH");
        Path sqlitePath = Paths.get("/data/blacklite.db");
        if (restoreDatabase(dbPath, sqlitePath) == 0) {
          Resource resource = null;
          try {
            resource = new UrlResource(sqlitePath.toUri());
          } catch (MalformedURLException e) {
            logger.error("Malformed URL", e);
          }
          return ResponseEntity.ok()
              .contentType(MediaType.parseMediaType("application/vnd.sqlite3"))
              .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
              .body(resource);
        } else {
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
      } catch (Exception e) {
        logger.error("Ooops", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
      }
    }

    private int restoreDatabase(String dbPath, Path sqlitePath) throws IOException, InterruptedException {
      ProcessBuilder pb = new ProcessBuilder("litestream", "restore", "-v", "-o", sqlitePath.toString(), dbPath);
      Process p = pb.inheritIO().start();
      int result = p.waitFor();
      return result;
    }
  }
}