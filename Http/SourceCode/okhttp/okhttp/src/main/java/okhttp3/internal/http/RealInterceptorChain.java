/*
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.internal.http;

import java.io.IOException;
import java.util.List;
import okhttp3.Connection;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.connection.StreamAllocation;

/**
 * A concrete interceptor chain that carries the entire interceptor chain: all application
 * interceptors, the OkHttp core, all network interceptors, and finally the network caller.
 */
public final class RealInterceptorChain implements Interceptor.Chain {
  private final List<Interceptor> interceptors;   // 保存了整条责任链
  private final StreamAllocation streamAllocation;
  private final HttpCodec httpCodec;
  private final Connection connection;
  private final int index;    // 当前处于责任链的哪个位置
  private final Request request;
  private int calls;

  public RealInterceptorChain(List<Interceptor> interceptors, StreamAllocation streamAllocation,
      HttpCodec httpCodec, Connection connection, int index, Request request) {
    this.interceptors = interceptors;
    this.connection = connection;
    this.streamAllocation = streamAllocation;
    this.httpCodec = httpCodec;
    this.index = index;
    this.request = request;
  }

  @Override public Connection connection() {
    return connection;
  }

  public StreamAllocation streamAllocation() {
    return streamAllocation;
  }

  public HttpCodec httpStream() {
    return httpCodec;
  }

  @Override public Request request() {
    return request;
  }

  @Override public Response proceed(Request request) throws IOException {
    return proceed(request, streamAllocation, httpCodec, connection);
  }

  /**
   * @author zhijianz
   *
   * @param httpCodec [description]使用Okio对Socket数据进行读写，对于不同的http版本有不同的实现
   * @param streamAllocation 按照类注解的描述，这个类用来平衡connection/stream/call三者之间的关系
   */
  public Response proceed(Request request, StreamAllocation streamAllocation, HttpCodec httpCodec,
      Connection connection) throws IOException {
    if (index >= interceptors.size()) throw new AssertionError();

    calls++;

    // If we already have a stream, confirm that the incoming request will use it.
    if (this.httpCodec != null && !sameConnection(request.url())) {
      throw new IllegalStateException("network interceptor " + interceptors.get(index - 1)
          + " must retain the same host and port");
    }

    // If we already have a stream, confirm that this is the only call to chain.proceed().
    if (this.httpCodec != null && calls > 1) {
      throw new IllegalStateException("network interceptor " + interceptors.get(index - 1)
          + " must call proceed() exactly once");
    }

    /**
     * @author zhijianz
     *
     * 下面的代码是将整个责任链连接起来的逻辑，按照我的理解，这里的模式应该是采用了
     * 责任链模式和策略模式的混合体，Chain接口定义了整个责任链模式的框架，具体在某个
     * 执行者上的处理逻辑由对应的Interceptor处理，在传入责任链的下一个对象给这个处理
     * 的接口
     */
    // Call the next interceptor in the chain.
    RealInterceptorChain next = new RealInterceptorChain(
        interceptors, streamAllocation, httpCodec, connection, index + 1, request);
    Interceptor interceptor = interceptors.get(index);
    Response response = interceptor.intercept(next);

    // Confirm that the next interceptor made its required call to chain.proceed().
    if (httpCodec != null && index + 1 < interceptors.size() && next.calls != 1) {
      throw new IllegalStateException("network interceptor " + interceptor
          + " must call proceed() exactly once");
    }

    // Confirm that the intercepted response isn't null.
    if (response == null) {
      throw new NullPointerException("interceptor " + interceptor + " returned null");
    }

    return response;
  }

  private boolean sameConnection(HttpUrl url) {
    return url.host().equals(connection.route().address().url().host())
        && url.port() == connection.route().address().url().port();
  }
}
