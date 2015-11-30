/*
 * Copyright 2015 Netflix, Inc.
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
 *
 */
package io.reactivex.netty.protocol.http.client.internal;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.channel.FlushSelectorOperator;
import io.reactivex.netty.contexts.RequestCorrelator;
import rx.Observable;
import rx.functions.Func1;

import java.util.Date;
import java.util.Map.Entry;

public final class RawRequest<I, O> {

    private final Redirector<I, O> redirector;
    private final RequestCorrelator correlator;
    private final HttpRequest headers;
    @SuppressWarnings("rawtypes")
    private final Observable content;
    private final Func1<?, Boolean> flushSelector;
    private final boolean hasTrailers;

    @SuppressWarnings("rawtypes")
    private RawRequest(HttpRequest headers, Observable content, Func1<?, Boolean> flushSelector, boolean hasTrailers,
                       Redirector<I, O> redirector, RequestCorrelator correlator) {
        this.headers = headers;
        this.content = content;
        this.flushSelector = flushSelector;
        this.hasTrailers = hasTrailers;
        this.redirector = redirector;
        this.correlator = correlator;
    }

    public RawRequest<I, O> addHeader(CharSequence name, Object value) {
        HttpRequest headersCopy = _copyHeaders();
        headersCopy.headers().add(name, value);
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> addHeaderValues(CharSequence name, Iterable<Object> values) {
        HttpRequest headersCopy = _copyHeaders();
        headersCopy.headers().add(name, values);
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> addCookie(Cookie cookie) {
        String cookieHeader = ClientCookieEncoder.STRICT.encode(cookie);
        return addHeader(HttpHeaderNames.COOKIE, cookieHeader);
    }

    public RawRequest<I, O> addDateHeader(CharSequence name, Date value) {
        HttpRequest headersCopy = _copyHeaders();
        headersCopy.headers().add(name, value);
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> addDateHeader(CharSequence name, Iterable<Date> values) {
        HttpRequest headersCopy = _copyHeaders();
        for (Date value : values) {
            headersCopy.headers().add(name, value);
        }
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> setDateHeader(CharSequence name, Date value) {
        HttpRequest headersCopy = _copyHeaders();
        headersCopy.headers().set(name, value);
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> setHeader(CharSequence name, Object value) {
        HttpRequest headersCopy = _copyHeaders();
        headersCopy.headers().set(name, value);
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> setHeaderValues(CharSequence name, Iterable<Object> values) {
        HttpRequest headersCopy = _copyHeaders();
        headersCopy.headers().set(name, values);
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> setDateHeader(CharSequence name, Iterable<Date> values) {
        HttpRequest headersCopy = _copyHeaders();
        boolean addNow = false;
        for (Date value : values) {
            if (addNow) {
                headersCopy.headers().add(name, value);
            } else {
                headersCopy.headers().set(name, value);
                addNow = true;
            }
        }
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> setKeepAlive(boolean keepAlive) {
        HttpRequest headersCopy = _copyHeaders();
        HttpUtil.setKeepAlive(headersCopy, keepAlive);
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> setTransferEncodingChunked() {
        HttpRequest headersCopy = _copyHeaders();
        HttpUtil.setTransferEncodingChunked(headersCopy, true);
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> removeHeader(CharSequence name) {
        HttpRequest headersCopy = _copyHeaders();
        headersCopy.headers().remove(name);
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> setMethod(HttpMethod method) {
        HttpRequest headersCopy = _copyHeaders(headers.uri(), method);
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> setUri(String uri) {
        HttpRequest headersCopy = _copyHeaders(uri, headers.method());
        return create(headersCopy, content, hasTrailers, redirector, correlator);
    }

    public RawRequest<I, O> followRedirect(Redirector<I, O> redirectHandler) {
        return create(headers, content, hasTrailers, redirectHandler, correlator);
    }

    public RawRequest<I, O> setCorrelator(RequestCorrelator correlator) {
        return create(headers, content, hasTrailers, redirector, correlator);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Observable asObservable(Connection<?, ?> connection) {
        Observable toReturn = Observable.just(headers);

        if (null != content) {
            if (null == flushSelector) {
                toReturn = toReturn.concatWith(content);
            } else {
                toReturn = toReturn.concatWith(content.lift(new FlushSelectorOperator(flushSelector, connection)));
            }
        }

        if (!hasTrailers) {
            toReturn = toReturn.concatWith(Observable.just(LastHttpContent.EMPTY_LAST_CONTENT));
        }

        return toReturn;
    }

    private HttpRequest _copyHeaders() {
        return _copyHeaders(headers.uri(), headers.method());
    }

    public HttpRequest _copyHeaders(String uri, HttpMethod method) {
        final HttpRequest newHeaders = new DefaultHttpRequest(headers.protocolVersion(), method, uri);
        // TODO: May be we can optimize this by not copying
        for (Entry<String, String> header : headers.headers()) {
            newHeaders.headers().set(header.getKey(), header.getValue());
        }
        return newHeaders;
    }

    public static <I, O> RawRequest<I, O> create(HttpVersion version, HttpMethod httpMethod, String uri,
                                                 Redirector<I, O> redirectHandler, RequestCorrelator correlator) {
        final HttpRequest headers = new DefaultHttpRequest(version, httpMethod, uri);
        return create(headers, null, null, false, redirectHandler, correlator);
    }

    @SuppressWarnings("rawtypes")
    public static <I, O> RawRequest<I, O> create(HttpRequest headers, Observable content, boolean hasTrailers,
                                                 Redirector<I, O> redirectHandler, RequestCorrelator correlator) {
        return create(headers, content, null, hasTrailers, redirectHandler, correlator);
    }

    @SuppressWarnings("rawtypes")
    public static <I, O> RawRequest<I, O>  create(HttpRequest headers, Observable content,
                                                  Func1<?, Boolean> flushSelector, boolean hasTrailers,
                                                  Redirector<I, O> redirectHandler, RequestCorrelator correlator) {
        return new RawRequest<>(headers, content, flushSelector, hasTrailers, redirectHandler, correlator);
    }

    public HttpRequest getHeaders() {
        return headers;
    }

    @SuppressWarnings("rawtypes")
    public Observable getContent() {
        return content;
    }

    public Func1<?, Boolean> getFlushSelector() {
        return flushSelector;
    }

    public boolean hasTrailers() {
        return hasTrailers;
    }

    public Redirector<I, O> getRedirector() {
        return redirector;
    }

    public RequestCorrelator getCorrelator() {
        return correlator;
    }
}
