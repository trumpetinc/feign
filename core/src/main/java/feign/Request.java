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

import static feign.Util.checkNotNull;
import static feign.Util.getThreadIdentifier;
import static feign.Util.valuesOrEmpty;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** An immutable request to an http server. */
// TODO: KD - technically not immutable anymore if body has isRetryable=false
public final class Request implements Serializable {

  public enum HttpMethod {
    GET,
    HEAD,
    POST(true),
    PUT(true),
    DELETE,
    CONNECT,
    OPTIONS,
    TRACE,
    PATCH(true);

    private final boolean withBody;

    HttpMethod() {
      this(false);
    }

    HttpMethod(boolean withBody) {
      this.withBody = withBody;
    }

    public boolean isWithBody() {
      return this.withBody;
    }
  }

  public enum ProtocolVersion {
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1"),
    HTTP_2("HTTP/2.0"),
    MOCK;

    final String protocolVersion;

    ProtocolVersion() {
      protocolVersion = name();
    }

    ProtocolVersion(String protocolVersion) {
      this.protocolVersion = protocolVersion;
    }

    @Override
    public String toString() {
      return protocolVersion;
    }
  }

  /**
   * No parameters can be null except {@code body} and {@code charset}. All parameters must be
   * effectively immutable, via safe copies, not mutating or otherwise.
   *
   * @deprecated {@link #create(HttpMethod, String, Map, byte[], Charset)}
   */
  @Deprecated
  public static Request create(
      String method,
      String url,
      Map<String, Collection<String>> headers,
      byte[] body,
      Charset charset) {
    checkNotNull(method, "httpMethod of %s", method);
    final HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
    return create(httpMethod, url, headers, body, charset, null);
  }

  /**
   * Builds a Request. All parameters must be effectively immutable, via safe copies.
   *
   * @param httpMethod for the request.
   * @param url for the request.
   * @param headers to include.
   * @param body of the request, can be {@literal null}
   * @param charset of the request, can be {@literal null}
   * @return a Request
   */
  @Deprecated
  public static Request create(
      HttpMethod httpMethod,
      String url,
      Map<String, Collection<String>> headers,
      byte[] body,
      Charset charset) {
    return create(httpMethod, url, headers, Body.create(body, charset), null);
  }

  /**
   * Builds a Request. All parameters must be effectively immutable, via safe copies.
   *
   * @param httpMethod for the request.
   * @param url for the request.
   * @param headers to include.
   * @param body of the request, can be {@literal null}
   * @param charset of the request, can be {@literal null}
   * @return a Request
   */
  public static Request create(
      HttpMethod httpMethod,
      String url,
      Map<String, Collection<String>> headers,
      byte[] body,
      Charset charset,
      RequestTemplate requestTemplate) {
    return create(httpMethod, url, headers, Body.create(body, charset), requestTemplate);
  }

  /**
   * Builds a Request. All parameters must be effectively immutable, via safe copies.
   *
   * @param httpMethod for the request.
   * @param url for the request.
   * @param headers to include.
   * @param body of the request, can be {@literal null}
   * @return a Request
   */
  public static Request create(
      HttpMethod httpMethod,
      String url,
      Map<String, Collection<String>> headers,
      Body body,
      RequestTemplate requestTemplate) {
    return new Request(httpMethod, url, headers, body, requestTemplate);
  }

  private final HttpMethod httpMethod;
  private final String url;
  private final Map<String, Collection<String>> headers;
  private final Body body;
  private final RequestTemplate requestTemplate;
  private final ProtocolVersion protocolVersion;

  /**
   * Creates a new Request.
   *
   * @param method of the request.
   * @param url for the request.
   * @param headers for the request.
   * @param body for the request, optional.
   * @param requestTemplate used to build the request.
   */
  Request(
      HttpMethod method,
      String url,
      Map<String, Collection<String>> headers,
      Body body,
      RequestTemplate requestTemplate) {
    this.httpMethod = checkNotNull(method, "httpMethod of %s", method.name());
    this.url = checkNotNull(url, "url");
    this.headers = checkNotNull(headers, "headers of %s %s", method, url);
    this.body = body;
    this.requestTemplate = requestTemplate;
    protocolVersion = ProtocolVersion.HTTP_1_1;
  }

  /**
   * Http Method for this request.
   *
   * @return the HttpMethod string
   * @deprecated @see {@link #httpMethod()}
   */
  @Deprecated
  public String method() {
    return httpMethod.name();
  }

  /**
   * Http Method for the request.
   *
   * @return the HttpMethod.
   */
  public HttpMethod httpMethod() {
    return this.httpMethod;
  }

  /**
   * URL for the request.
   *
   * @return URL as a String.
   */
  public String url() {
    return url;
  }

  /**
   * Request Headers.
   *
   * @return the request headers.
   */
  public Map<String, Collection<String>> headers() {
    return Collections.unmodifiableMap(headers);
  }

  /**
   * Add new entries to request Headers. It overrides existing entries
   *
   * @param key
   * @param value
   */
  public void header(String key, String value) {
    header(key, Arrays.asList(value));
  }

  /**
   * Add new entries to request Headers. It overrides existing entries
   *
   * @param key
   * @param values
   */
  public void header(String key, Collection<String> values) {
    headers.put(key, values);
  }

  /**
   * Charset of the request.
   *
   * @return the current character set for the request, may be {@literal null} for binary data.
   */
  // TODO: KD - not sure that returning null is great here.  It seems like it would be better to just let the caller grab the charset from the body (instead of having a dedicated method on the Request) 
  public Charset charset() {
    return body.getCharset().orElse(null);
  }

  /**
   * If present, this is the replayable body to send to the server. In some cases, this may be
   * interpretable as text.
   *
   * @see #charset()
   */
  public Body body() {
    return body;
  }

// TODO: KD - Shouldn't contenttype by directly associated with the body (not the request)?  Leaving this for backwards compatibility, but it seems like this should go - we are already making a breaking change with the body() method.
  public boolean isBinary() {
    return body.getCharset().isEmpty();
  }

  /**
   * Request Length.
   *
   * @return size of the request body.
   */
  public long length() {
    return this.body.length();
  }

  /**
   * Request HTTP protocol version
   *
   * @return HTTP protocol version
   */
  public ProtocolVersion protocolVersion() {
    return protocolVersion;
  }

  /**
   * Request as an HTTP/1.1 request.
   *
   * @return the request.
   */
  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder
        .append(httpMethod)
        .append(' ')
        .append(url)
        .append(' ')
        .append(protocolVersion)
        .append('\n');
    
    // TODO: KD - including headers in toString seems really risky - so easy to have that wind up in log output, which could contain the auth header...
    
    for (final String field : headers.keySet()) {
      for (final String value : valuesOrEmpty(headers, field)) {
        builder.append(field).append(": ").append(value).append('\n');
      }
    }
    if (body != null && body.isRepeatable()) {
      builder
      	.append('\n')
      	.append(Body.bodyAsString(body).orElse("-- " + body.length() + " length stream --"));
    }
    return builder.toString();
  }

  /**
   * Controls the per-request settings currently required to be implemented by all {@link Client
   * clients}
   */
  public static class Options {

    private final long connectTimeout;
    private final TimeUnit connectTimeoutUnit;
    private final long readTimeout;
    private final TimeUnit readTimeoutUnit;
    private final boolean followRedirects;
    private final Map<String, Map<String, Options>> threadToMethodOptions;

    /**
     * Get an Options by methodName
     *
     * @param methodName it's your FeignInterface method name.
     * @return method Options
     */
    @Experimental
    public Options getMethodOptions(String methodName) {
      Map<String, Options> methodOptions =
          threadToMethodOptions.getOrDefault(getThreadIdentifier(), new HashMap<>());
      return methodOptions.getOrDefault(methodName, this);
    }

    /**
     * Set methodOptions by methodKey and options
     *
     * @param methodName it's your FeignInterface method name.
     * @param options it's the Options for this method.
     */
    @Experimental
    public void setMethodOptions(String methodName, Options options) {
      String threadIdentifier = getThreadIdentifier();
      Map<String, Request.Options> methodOptions =
          threadToMethodOptions.getOrDefault(threadIdentifier, new HashMap<>());
      threadToMethodOptions.put(threadIdentifier, methodOptions);
      methodOptions.put(methodName, options);
    }

    /**
     * Creates a new Options instance.
     *
     * @param connectTimeoutMillis connection timeout in milliseconds.
     * @param readTimeoutMillis read timeout in milliseconds.
     * @param followRedirects if the request should follow 3xx redirections.
     * @deprecated please use {@link #Options(long, TimeUnit, long, TimeUnit, boolean)}
     */
    @Deprecated
    public Options(int connectTimeoutMillis, int readTimeoutMillis, boolean followRedirects) {
      this(
          connectTimeoutMillis,
          TimeUnit.MILLISECONDS,
          readTimeoutMillis,
          TimeUnit.MILLISECONDS,
          followRedirects);
    }

    /**
     * Creates a new Options Instance.
     *
     * @param connectTimeout value.
     * @param connectTimeoutUnit with the TimeUnit for the timeout value.
     * @param readTimeout value.
     * @param readTimeoutUnit with the TimeUnit for the timeout value.
     * @param followRedirects if the request should follow 3xx redirections.
     */
    public Options(
        long connectTimeout,
        TimeUnit connectTimeoutUnit,
        long readTimeout,
        TimeUnit readTimeoutUnit,
        boolean followRedirects) {
      super();
      this.connectTimeout = connectTimeout;
      this.connectTimeoutUnit = connectTimeoutUnit;
      this.readTimeout = readTimeout;
      this.readTimeoutUnit = readTimeoutUnit;
      this.followRedirects = followRedirects;
      this.threadToMethodOptions = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new Options instance that follows redirects by default.
     *
     * @param connectTimeoutMillis connection timeout in milliseconds.
     * @param readTimeoutMillis read timeout in milliseconds.
     * @deprecated please use {@link #Options(long, TimeUnit, long, TimeUnit, boolean)}
     */
    @Deprecated
    public Options(int connectTimeoutMillis, int readTimeoutMillis) {
      this(connectTimeoutMillis, readTimeoutMillis, true);
    }

    /**
     * Creates a new Options Instance.
     *
     * @param connectTimeout value.
     * @param readTimeout value.
     * @param followRedirects if the request should follow 3xx redirections.
     */
    public Options(Duration connectTimeout, Duration readTimeout, boolean followRedirects) {
      this(
          connectTimeout.toMillis(),
          TimeUnit.MILLISECONDS,
          readTimeout.toMillis(),
          TimeUnit.MILLISECONDS,
          followRedirects);
    }

    /**
     * Creates the new Options instance using the following defaults:
     *
     * <ul>
     *   <li>Connect Timeout: 10 seconds
     *   <li>Read Timeout: 60 seconds
     *   <li>Follow all 3xx redirects
     * </ul>
     */
    public Options() {
      this(10, TimeUnit.SECONDS, 60, TimeUnit.SECONDS, true);
    }

    /**
     * Defaults to 10 seconds. {@code 0} implies no timeout.
     *
     * @see java.net.HttpURLConnection#getConnectTimeout()
     */
    public int connectTimeoutMillis() {
      return (int) connectTimeoutUnit.toMillis(connectTimeout);
    }

    /**
     * Defaults to 60 seconds. {@code 0} implies no timeout.
     *
     * @see java.net.HttpURLConnection#getReadTimeout()
     */
    public int readTimeoutMillis() {
      return (int) readTimeoutUnit.toMillis(readTimeout);
    }

    /**
     * Defaults to true. {@code false} tells the client to not follow the redirections.
     *
     * @see HttpURLConnection#getFollowRedirects()
     */
    public boolean isFollowRedirects() {
      return followRedirects;
    }

    /**
     * Connect Timeout Value.
     *
     * @return current timeout value.
     */
    public long connectTimeout() {
      return connectTimeout;
    }

    /**
     * TimeUnit for the Connection Timeout value.
     *
     * @return TimeUnit
     */
    public TimeUnit connectTimeoutUnit() {
      return connectTimeoutUnit;
    }

    /**
     * Read Timeout value.
     *
     * @return current read timeout value.
     */
    public long readTimeout() {
      return readTimeout;
    }

    /**
     * TimeUnit for the Read Timeout value.
     *
     * @return TimeUnit
     */
    public TimeUnit readTimeoutUnit() {
      return readTimeoutUnit;
    }
  }

  @Experimental
  public RequestTemplate requestTemplate() {
    return this.requestTemplate;
  }

  /**
   * Request Body
   *
   * <p>Considered experimental, will most likely be made internal going forward.
   */
  // TODO: KD - I am keeping this separate from Request.Body right now to minimize disruption, but it would be a lot cleaner if there was a single interface for Body (functionality appears to be identical between Request and Response)
  // TODO: KD - consider adding contenttype to Body - this seems like a really natural thing to do, and it could be used to set content type headers... 
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
		
		// Used for testing only
		static Optional<byte[]> bodyAsBytes(Body body) {
			try {
				return Optional.of( body.asInputStream().readAllBytes() );
			} catch (IOException e) {
			}
			
			return Optional.empty();
		}
		
	  /**
	   * length in bytes, if known. Null if unknown or greater than {@link Integer#MAX_VALUE}. <br>
	   */
		Long length();

	  /** True if {@link #asInputStream()} and {@link #asReader()} can be called more than once. */
	  boolean isRepeatable();

	  /** It is the responsibility of the caller to close the stream. */
	  InputStream asInputStream();

	  Optional<Charset> getCharset();
	  
  }
  
  static class EmptyBody implements Body{

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
  
	public static class InputStreamBody implements Body {

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
	    public Optional<Charset> getCharset() {
	    	return charset;
	    }
	    
	    @Override
	    public void close() throws IOException {
	      inputStream.close();
	    }
	  }	
	
	public static class ByteArrayBody implements Body {

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
