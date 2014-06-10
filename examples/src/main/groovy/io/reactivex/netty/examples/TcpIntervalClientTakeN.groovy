/*
 * Copyright 2014 Netflix, Inc.
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
package io.reactivex.netty.examples

import io.reactivex.netty.RxNetty
import io.reactivex.netty.channel.ObservableConnection
import io.reactivex.netty.pipeline.PipelineConfigurators
import rx.Observable

/**
 * Connects to IntervalServer, take N values and disconnects.
 * <p>
 * Should output results like:
 * <p>
 * <pre>
 * onNext: interval => 0
 * onNext: interval => 1
 * onNext: interval => 2
 * </pre>
 *
 */
class TcpIntervalClientTakeN {

    def static void main(String[] args) {

        RxNetty.createTcpClient("localhost", 8181, PipelineConfigurators.textOnlyConfigurator()).connect()
                .flatMap({ ObservableConnection<String, String> connection ->

                    // output 10 values at intervals and receive the echo back
                    Observable<String> subscribeWrite = connection.writeString("subscribe:").map({ return ""});

                    // capture the output from the server
                    Observable<String> data = connection.getInput().map({ String msg ->
                        return msg.trim()
                    });

                    return Observable.concat(subscribeWrite, data);
                })
                .take(3)
                .toBlocking().forEach({ String o ->
                    println("onNext: " + o) }
                );

    }


}
