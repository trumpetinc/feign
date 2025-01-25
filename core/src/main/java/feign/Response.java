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
package feign;

import static feign.Util.caseInsensitiveCopyOf;
import static feign.Util.checkNotNull;
import static feign.Util.checkState;
import static feign.Util.valuesOrEmpty;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import feign.Request.Body;
import feign.Request.ProtocolVersion;

/** An immutable response to an http invocation which only returns string content. */
public final class Response implements Closeable {

  private final int status;
  private final String reason;
  private final Map<String, Collection<String>> headers;
  private final Body body;
  private final Request request;
  private final ProtocolVersion protocolVersion;

  private Response(Builder builder) {
    checkState(builder.request != null, "original request is required");
    this.status = builder.status;
    this.request = builder.request;
    this.reason = builder.reason; // nullable
    this.headers = caseInsensitiveCopyOf(builder.headers);
    this.protocolVersion = builder.protocolVersion;
    
    Charset charset = computeCharsetFromHeaders(headers);
    Body body;
    if (builder.body != null)
    	body = builder.body;
    else if (builder.bodyBytes != null)
    	body = Body.create(builder.bodyBytes, charset);
    else if (builder.bodyInputStream != null)
    	body = Body.create(builder.bodyInputStream, builder.bodyLength, charset);
    else
    	body = Body.empty();
    
    this.body = body;

  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private static final ProtocolVersion DEFAULT_PROTOCOL_VERSION = ProtocolVersion.HTTP_1_1;
    int status;
    String reason;
    Map<String, Collection<String>> headers;
    
    Body body = Response.Body.empty();
    InputStream bodyInputStream;
    byte[] bodyBytes;
    Long bodyLength;
    Request request;
    
    private RequestTemplate requestTemplate;
    private ProtocolVersion protocolVersion = DEFAULT_PROTOCOL_VERSION;

    Builder() {}

    Builder(Response source) {
      this.status = source.status;
      this.reason = source.reason;
      this.headers = source.headers;
      this.body = source.body;
      this.request = source.request;
      this.protocolVersion = source.protocolVersion;
    }

    /**
     * @see Response#status
     */
    public Builder status(int status) {
      this.status = status;
      return this;
    }

    /**
     * @see Response#reason
     */
    public Builder reason(String reason) {
      this.reason = reason;
      return this;
    }

    /**
     * @see Response#headers
     */
    public Builder headers(Map<String, Collection<String>> headers) {
      this.headers = headers;
      return this;
    }

    private void clearBody() {
    	this.body = null;
    	this.bodyBytes = null;
    	this.bodyInputStream = null;
    	this.bodyLength = null;
    }
    
    /**
     * @see Response#body
     */
    public Builder body(Body body) {
    	clearBody();
    	this.body = body;
    	return this;
    }

    /**
     * @see Response#body
     */
    public Builder body(InputStream inputStream, Long length) {
    	clearBody();
    	this.bodyInputStream = inputStream;
    	this.bodyLength = length;
    	return this;
    }

    /**
     * @see Response#body
     */
    public Builder body(byte[] data) {
    	clearBody();
    	this.bodyBytes = data;
    	return this;
    }

    /**
     * @see Response#request
     */
    public Builder request(Request request) {
      checkNotNull(request, "request is required");
      this.request = request;
      return this;
    }

    /** HTTP protocol version */
    public Builder protocolVersion(ProtocolVersion protocolVersion) {
      this.protocolVersion = (protocolVersion != null) ? protocolVersion : DEFAULT_PROTOCOL_VERSION;
      return this;
    }

    /**
     * The Request Template used for the original request.
     *
     * @param requestTemplate used.
     * @return builder reference.
     */
    @Experimental
    public Builder requestTemplate(RequestTemplate requestTemplate) {
      this.requestTemplate = requestTemplate;
      return this;
    }

    public Response build() {
      return new Response(this);
    }
  }

  /**
   * status code. ex {@code 200}
   *
   * <p>See <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html" >rfc2616</a>
   */
  public int status() {
    return status;
  }

  /**
   * Nullable and not set when using http/2 See <a
   * href="https://github.com/http2/http2-spec/issues/202">...</a> See <a
   * href="https://github.com/http2/http2-spec/issues/202">...</a>
   */
  public String reason() {
    return reason;
  }

  /** Returns a case-insensitive mapping of header names to their values. */
  public Map<String, Collection<String>> headers() {
    return headers;
  }

  /** if present, the response had a body */
  public Body body() {
    return body;
  }

  /** the request that generated this response */
  public Request request() {
    return request;
  }

  /**
   * the HTTP protocol version
   *
   * @return HTTP protocol version or empty if a client does not provide it
   */
  public ProtocolVersion protocolVersion() {
    return protocolVersion;
  }

  /**
   * Returns a charset object based on the requests content type. Defaults to UTF-8 See <a
   * href="https://datatracker.ietf.org/doc/html/rfc7231#section-5.3.3">rfc7231 - Accept-Charset</a>
   * See <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-3.1.1.1">rfc7231 - Media
   * Type</a>
   */
  public Charset charset() {

	  // TODO: KD - should we use the body charset, or should we always use the header charset?  In most cases, the body charset is determined by the headers, but if the response is transformed to have a different body type, I would think we'd want to use the body charset and not the headers...
	  return body.getCharset().orElse(StandardCharsets.UTF_8);
	  
  }
  
  // TODO: KD - protected b/c we need this in FeignException to compute the charset - we can switch this back to private if we can get rid of some of the feignexception constructors
  static Charset computeCharsetFromHeaders(Map<String, Collection<String>> headers) {
	  
	  Collection<String> contentTypeHeaders = headers.get("Content-Type");
	  
	    if (contentTypeHeaders != null) {
	        for (String contentTypeHeader : contentTypeHeaders) {
	          String[] contentTypeParmeters = contentTypeHeader.split(";");
	          if (contentTypeParmeters.length > 1) {
	            String[] charsetParts = contentTypeParmeters[1].split("=");
	            if (charsetParts.length == 2 && "charset".equalsIgnoreCase(charsetParts[0].trim())) {
	              String charsetString = charsetParts[1].replaceAll("\"", "");
	              return Charset.forName(charsetString);
	            }
	          }
	        }
	      }

	      return Util.UTF_8;
	  
  }

  @Override
  public String toString() {
    StringBuilder builder =
        new StringBuilder(protocolVersion.toString()).append(" ").append(status);
    if (reason != null) builder.append(' ').append(reason);
    builder.append('\n');
    for (String field : headers.keySet()) {
      for (String value : valuesOrEmpty(headers, field)) {
        builder.append(field).append(": ").append(value).append('\n');
      }
    }
    if (body != null) builder.append('\n').append(body);
    return builder.toString();
  }

  @Override
  public void close() {
    Util.ensureClosed(body);
  }

  // TODO: KD - Request and Response.Body are identical now - I would really like to collapse these to a single interface and class hierarchy...  Maybe HttpBody ?
  public interface Body extends Closeable {

		public static Body create(InputStream inputStream, Long length, Charset charset) {
			return new InputStreamBody(inputStream, length, charset);
		}
		
		public static Body create(byte[] bytes, Charset charset){
			return new ByteArrayBody(bytes, charset);
		}
		
		public static Body empty() {
			return new EmptyBody();
		}
	  
		public static Optional<String> bodyAsString(Body body) {
	    	try {
	  	      return Optional.of( new String(body.asInputStream().readAllBytes(), body.getCharset().orElse(StandardCharsets.UTF_8)) );
	      	} catch (IOException ignore) {}
	    	
	    	return Optional.empty();
		}
		
		// used for testing
		static Optional<Reader> bodyAsReader(Body body) {
			if (body.getCharset().isEmpty()) return Optional.empty();
			
			return Optional.of( new InputStreamReader(body.asInputStream(), body.getCharset().orElseThrow()) );
		}
		
		// used for testing
		static Body transformResponseBodyAsString(Response.Body inBody, Function<String, String> func) {
			String str = Response.Body.bodyAsString(inBody).orElse("");
			String alt = func.apply(str);
			return Response.Body.create(alt.getBytes(), inBody.getCharset().orElse(StandardCharsets.UTF_8));
		}
		
		// used for testing
		static Body createFromRequestBody(Request.Body inBody) {
			return Response.Body.create(inBody.asInputStream(), inBody.length(), inBody.getCharset().orElse(null));
		}
		
    /**
     * length in bytes, if known. Null if unknown or greater than {@link Integer#MAX_VALUE}. <br>
     * <br>
     * <br>
     * <b>Note</b><br>
     * This is an integer as most implementations cannot do bodies greater than 2GB.
     */
		Long length();

    /** True if {@link #asInputStream()} and {@link #asReader()} can be called more than once. */
    boolean isRepeatable();

    /** It is the responsibility of the caller to close the stream. */
    InputStream asInputStream();

	  Optional<Charset> getCharset();
    
  }

  private static class EmptyBody implements Body{

	@Override
	public void close() throws IOException {

	}

	@Override
	public Long length() {
		return null;
	}

	@Override
	public boolean isRepeatable() {
		return true;
	}

	@Override
	public InputStream asInputStream() {
		return new ByteArrayInputStream(new byte[0]);
	}

	@Override
	public Optional<Charset> getCharset() {
		return Optional.empty();
	}
	  
  }

  
  private static final class InputStreamBody implements Response.Body {

    private final InputStream inputStream;
    private final Long length;
    private final Optional<Charset> charset;

    private InputStreamBody(InputStream inputStream, Long length, Charset charset) {
      this.inputStream = inputStream;
      this.length = length;
      this.charset = Optional.ofNullable(charset);
    }

    @Override
    public Long length() {
      return length;
    }

    @Override
    public boolean isRepeatable() {
      return false;
    }

    @Override
    public InputStream asInputStream() {
      return inputStream;
    }

    @Override
    public void close() throws IOException {
      inputStream.close();
    }

	@Override
	public Optional<Charset> getCharset() {
		return charset;
	}
  }

  private static final class ByteArrayBody implements Response.Body {

    private final byte[] data;
    private final Optional<Charset> charset;

    public ByteArrayBody(byte[] data, Charset charset) {
      this.data = data;
      this.charset = Optional.ofNullable(charset);
    }


    @Override
    public Long length() {
      return (long)data.length;
    }

    @Override
    public boolean isRepeatable() {
      return true;
    }

    @Override
    public InputStream asInputStream() {
      return new ByteArrayInputStream(data);
    }

	@Override
	public Optional<Charset> getCharset() {
		return charset;
	}

    
    @Override
    public void close() {}
  }
}
