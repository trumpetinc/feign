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
 */package feign.stream;

import static java.lang.String.format;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Type;

import feign.HttpBodyFactory;
import feign.RequestTemplate;
import feign.Util;
import feign.codec.EncodeException;
import feign.codec.Encoder;

public class InputStreamAndFileEncoder implements Encoder {

	private final Encoder delegateEncoder;
	
	public InputStreamAndFileEncoder(Encoder delegateEncoder) {
		this.delegateEncoder = delegateEncoder;
	}
	
    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) {
		if (bodyType instanceof Class) {
			Class<?> bodyClass = (Class<?>)bodyType;
	    	if (InputStream.class.isAssignableFrom(bodyClass)) {
	    		InputStream is = (InputStream)object;
				// TODO: KD - Should we pull encoding from the headers??
	    		template.body(HttpBodyFactory.forInputStream(is));
	    		if (!template.headers().containsKey(Util.CONTENT_TYPE))
	    			template.headerLiteral(Util.CONTENT_TYPE, "application/octet-stream");
	    		return;
	    	}
	    	
	    	if (File.class.isAssignableFrom(bodyClass)) {
	    		try {
					File file = (File)object;
					template.body(HttpBodyFactory.forFile(file));
		    		if (!template.headers().containsKey(Util.CONTENT_TYPE))
		    			template.headerLiteral(Util.CONTENT_TYPE, "application/octet-stream");
		    		return;
				} catch (FileNotFoundException e) {
					throw new EncodeException(format("Unable to encode missing file - %s", e.getMessage(), object.getClass()));
				}
	    	}
	    	
		}
    	
      
		if (delegateEncoder != null) {
			delegateEncoder.encode(object, bodyType, template);
			return;
		}

		throw new EncodeException(format("%s is not a type supported by this encoder.", object.getClass()));
      
//    	if (bodyType == String.class) {
//        template.body(object.toString());
//      } else if (bodyType == byte[].class) {
//        template.body((byte[]) object, null);
//      } else if (object != null) {
//        throw new EncodeException(
//            format("%s is not a type supported by this encoder.", object.getClass()));
//      }
    }
}
