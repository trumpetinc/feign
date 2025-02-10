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
package feign.http2client;

import java.net.http.HttpRequest.BodyPublisher;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

/**
 * Used to force the length value of an existing BodyPublisher.
 * Used primarily because the built in InputStreamBodyPublisher assumes length=-1, but if we actually know the length, we want to specify it.
 */
class LengthOverrideBodyPublisher implements BodyPublisher {

	private final BodyPublisher delegate;
	private final long length;
	
	public LengthOverrideBodyPublisher(BodyPublisher delegate, long length) {
		this.delegate = delegate;
		this.length = length;
	}
	
        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        	delegate.subscribe(subscriber);
        }

        @Override
        public long contentLength() {
            return length;
        }

}
