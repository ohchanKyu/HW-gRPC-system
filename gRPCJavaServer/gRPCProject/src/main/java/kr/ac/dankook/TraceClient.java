package kr.ac.dankook;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class TraceClient {

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        TraceClientCaller traceClientCaller = new TraceClientCaller(channel);
        traceClientCaller.sendClientStreamingAsync();
    }
}
