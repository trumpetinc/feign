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

import static java.lang.String.format;

import feign.Request;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.lang.reflect.Type;

public class StringDecoder implements Decoder {

  @Override
  public Object decode(Response response, Type type) throws IOException {
    Response.Body body = response.body();
    if (response.status() == 404 || response.status() == 204) { 
      return null;
    }

	if (body.length() == null) return null; // TODO: KD - checking the length for null is super ugly.  Any way we can not do this?

    if (String.class.equals(type)) {
// TODO: KD - original code assumed that body encoding was UTF8, hopefully it is OK to use the actual encoding in the Body now?
    	String val = Response.Body.bodyAsString(body).orElse("");
    	return val;
    }
    throw new DecodeException(
        response.status(),
        format("%s is not a type supported by this decoder.", type),
        response.request());
  }
}
