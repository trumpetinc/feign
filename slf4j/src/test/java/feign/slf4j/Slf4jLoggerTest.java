/**
 * Copyright 2012-2018 The Feign Authors
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.slf4j;

import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.util.Collection;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class Slf4jLoggerTest {

  private static final String CONFIG_KEY = "someMethod()";
  private static final Request REQUEST =
      new RequestTemplate().method("GET").append("http://api.example.com").request();
  private static final Response RESPONSE =
      Response.builder()
          .status(200)
          .reason("OK")
          .headers(Collections.<String, Collection<String>>emptyMap())
          .body(new byte[0])
          .build();
  @Rule public final RecordingSimpleLogger slf4j = new RecordingSimpleLogger();
  private Slf4jLogger logger;

  @Test
  public void useFeignLoggerByDefault() throws Exception {
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.Logger - [someMethod] This is my message\n");

    logger = new Slf4jLogger();
    logger.log(CONFIG_KEY, "This is my message");
  }

  @Test
  public void useLoggerByNameIfRequested() throws Exception {
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG named.logger - [someMethod] This is my message\n");

    logger = new Slf4jLogger("named.logger");
    logger.log(CONFIG_KEY, "This is my message");
  }

  @Test
  public void useLoggerByClassIfRequested() throws Exception {
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.Feign - [someMethod] This is my message\n");

    logger = new Slf4jLogger(Feign.class);
    logger.log(CONFIG_KEY, "This is my message");
  }

  @Test
  public void useSpecifiedLoggerIfRequested() throws Exception {
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG specified.logger - [someMethod] This is my message\n");

    logger = new Slf4jLogger(LoggerFactory.getLogger("specified.logger"));
    logger.log(CONFIG_KEY, "This is my message");
  }

  @Test
  public void logOnlyIfDebugEnabled() throws Exception {
    slf4j.logLevel("info");

    logger = new Slf4jLogger();
    logger.log(CONFIG_KEY, "A message with %d formatting %s.", 2, "tokens");
    logger.logRequest(CONFIG_KEY, Logger.Level.BASIC, REQUEST);
    logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.BASIC, RESPONSE, 273);
  }

  @Test
  public void logRequestsAndResponses() throws Exception {
    slf4j.logLevel("debug");
    slf4j.expectMessages(
        "DEBUG feign.Logger - [someMethod] A message with 2 formatting tokens.\n"
            + "DEBUG feign.Logger - [someMethod] ---> GET http://api.example.com HTTP/1.1\n"
            + "DEBUG feign.Logger - [someMethod] <--- HTTP/1.1 200 OK (273ms)\n");

    logger = new Slf4jLogger();
    logger.log(CONFIG_KEY, "A message with %d formatting %s.", 2, "tokens");
    logger.logRequest(CONFIG_KEY, Logger.Level.BASIC, REQUEST);
    logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.BASIC, RESPONSE, 273);
  }
}
