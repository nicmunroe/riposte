package com.nike.helloworld;

import com.nike.riposte.server.channelpipeline.ChannelAttributes;
import com.nike.riposte.server.http.HttpProcessingState;
import com.nike.riposte.server.http.RequestInfo;
import com.nike.riposte.server.http.ResponseInfo;
import com.nike.riposte.server.http.StandardEndpoint;
import com.nike.riposte.util.AsyncNettyHelper;
import com.nike.riposte.util.Matcher;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;

/**
 * A basic {@link StandardEndpoint} that listends for GET calls at the root path "/" and simply responds with
 * "Hello, world" in text/plain mime type.
 */
public class SignalFxTestEndpoint extends StandardEndpoint<Void,String> {

//    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Matcher matcher = Matcher.multiMatch(Arrays.asList("/hello/{someName}", "/alsoHello/foo/{someName}/bar"), HttpMethod.GET, HttpMethod.POST);
    private static final String content = "{\"hello\":\"world\"}";

    /**
     * @return The {@link Matcher} that maps incoming requests to this endpoint.
     */
    public Matcher requestMatcher() {
        return matcher;
    }

    /**
     * Sample service endpoint using CompletableFuture.supplyAsync to illustrate how to create an asynchronous
     * call. Where the service task will be completed without making external calls or compute-intensive code
     * it would be better to call:
     *
     * <pre>
     * return CompletableFuture.completedFuture(ResponseInfo.newBuilder("Hello, world!")
     *                                          .withDesiredContentWriterMimeType("text/plain")
     *                                          .build();
     * </pre>
     *
     * Also note that because we use {@link AsyncNettyHelper#supplierWithTracingAndMdc(Supplier, ChannelHandlerContext)}
     * to wrap the endpoint logic, the log message will be automatically tagged with the trace ID associated with the
     * incoming request.
     */
    @Override
    public CompletableFuture<ResponseInfo<String>> execute(RequestInfo<Void> request, Executor longRunningTaskExecutor, ChannelHandlerContext ctx) {
        String name = request.getPathParam("someName");
        HttpProcessingState state = ChannelAttributes.getHttpProcessingStateForChannel(ctx).get();
        String matchingPath = state.getMatchingPathTemplate();
        String sleepTimeString = request.getHeaders().get("sleep-time");
        long sleepTime = (sleepTimeString == null) ? 42 : Long.parseLong(sleepTimeString);

        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    Thread.sleep(sleepTime);
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return ResponseInfo.newBuilder(content.replace("world", name + " - " + matchingPath))
                            .withDesiredContentWriterMimeType("text/plain").build();
            },
            longRunningTaskExecutor
        );

//        return CompletableFuture.supplyAsync(supplierWithTracingAndMdc(
//            () -> {
//                logger.debug("Processing Request...");
//                return ResponseInfo.newBuilder("Hello, world!").withDesiredContentWriterMimeType("text/plain").build();
//            }, ctx)
//        );
    }


}
