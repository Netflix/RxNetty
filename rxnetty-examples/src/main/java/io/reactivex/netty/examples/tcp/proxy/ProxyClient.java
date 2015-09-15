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

package io.reactivex.netty.examples.tcp.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.examples.AbstractClientExample;
import io.reactivex.netty.examples.tcp.echo.EchoClient;
import io.reactivex.netty.protocol.tcp.client.TcpClient;
import rx.Observable;

import java.net.SocketAddress;
import java.nio.charset.Charset;

/**
 * A client to test {@link ProxyServer}. This client is provided here only for completeness of the example,
 * otherwise, it is exactly the same as {@link EchoClient}.
 */
public final class ProxyClient extends AbstractClientExample {

    public static void main(String[] args) {

        /*
         * Retrieves the server address, using the following algorithm:
         * <ul>
             <li>If any arguments are passed, then use the first argument as the server port.</li>
             <li>If available, use the second argument as the server host, else default to localhost</li>
             <li>Otherwise, start the passed server class and use that address.</li>
         </ul>
         */
        SocketAddress serverAddress = getServerAddress(ProxyServer.class, args);

        /*Create a new client for the server address*/
        TcpClient.<ByteBuf, ByteBuf>newClient(serverAddress)
                .enableWireLogging(LogLevel.DEBUG)
                /*Create a new connection request, each subscription creates a new connection*/
                .createConnectionRequest()
                /*Upon successful connection, write "Hello World" and listen to input*/
                .flatMap(connection ->
                                  /*Write the message*/
                                 connection.writeString(Observable.just("Hello World!"))
                                          /*Since, write returns a Void stream, cast it to ByteBuf to be able to merge
                                          with the input*/
                                         .cast(ByteBuf.class)
                                          /*Upon successful completion of the write, subscribe to the connection input*/
                                         .concatWith(connection.getInput())
                )
                /*Since, we only wrote a single message, we expect a single echo message back*/
                .take(1)
                /*Convert each ByteBuf to a string*/
                .map(bb -> bb.toString(Charset.defaultCharset()))
                /*Block till the response comes to avoid JVM exit.*/
                .toBlocking()
                /*Print each content chunk*/
                .forEach(logger::info);
    }
}
