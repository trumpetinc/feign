/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import feign.Request;
import feign.RequestTemplate;

class DefaultEncoderTest {

  private final Encoder encoder = new Encoder.Default();

  @Test
  void encodesStrings() throws Exception {
    String content = "This is my content";
    RequestTemplate template = new RequestTemplate();
    encoder.encode(content, String.class, template);
    assertThat(Request.Body.bodyAsString(template.body())).isEqualTo(content);
  }

  @Test
  void encodesByteArray() throws Exception {
    byte[] content = {12, 34, 56};
    RequestTemplate template = new RequestTemplate();
    encoder.encode(content, byte[].class, template);
    
    assertThat(Arrays.equals(content, Request.Body.bodyAsBytes(template.body()).orElseThrow())).isTrue();
  }

  @Test
  void refusesToEncodeOtherTypes() throws Exception {
    Throwable exception =
        assertThrows(
            EncodeException.class,
            () -> encoder.encode(Clock.systemUTC(), Clock.class, new RequestTemplate()));
    assertThat(exception.getMessage()).contains("is not a type supported by this encoder.");
  }
}
