package kr.ac.dankook;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CentralTraceServer {

    private static final List<Trace.TraceRequest> traceData = new ArrayList<>();
    private static final int PYTHON_SERVER_PORT = 9091;


    public static void main(String[] args) throws IOException, InterruptedException {

        Server server = ServerBuilder.forPort(9090)
                .addService(new CentralTraceServer.TraceServiceImpl())
                .build();

        System.out.println("Central Trace Server is running on port 9090");
        server.start();
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    sendAllDataToPythonServer();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, 0, 20 * 1000);
        server.awaitTermination();
    }
    private static void sendAllDataToPythonServer() throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", PYTHON_SERVER_PORT)
                .usePlaintext()
                .build();
        DashboardServiceGrpc.DashboardServiceStub asyncStub = DashboardServiceGrpc.newStub(channel);

        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<Dashboard.DashboardResponse> responseObserver = new StreamObserver<>() {

            @Override
            public void onNext(Dashboard.DashboardResponse response) {
                System.out.println(">>> Python Server Response : " + response);
            }

            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                System.err.println(">>> Warning => [" + status.toString() + "]");
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println(">>> Finished.");
                finishLatch.countDown();
            }
        };

        StreamObserver<Dashboard.DashboardRequest> requestObserver = asyncStub.sendTraceInfo(responseObserver);
        for(Trace.TraceRequest trace : traceData){
            try {
                Dashboard.DashboardRequest request = Dashboard.DashboardRequest.newBuilder()
                        .setServiceName(trace.getServiceName())
                        .setTimestamp(trace.getTimestamp())
                        .setMethod(trace.getMethod())
                        .setStatus(trace.getStatus())
                        .setMessage(trace.getMessage())
                        .build();
                requestObserver.onNext(request);
            } catch (Exception e) {
                System.err.println("Failed to send data to Python Server");
                requestObserver.onError(e);
            }
        }
        traceData.clear();
        requestObserver.onCompleted();
        if (finishLatch.await(1, TimeUnit.MINUTES)){
            System.out.println(">>> End Successful.");
        }
        channel.shutdown();
    }

    static class TraceServiceImpl extends TraceServiceGrpc.TraceServiceImplBase {

        @Override
        public StreamObserver<Trace.TraceRequest> streamTrace(StreamObserver<Trace.TraceResponse> responseObserver) {
            return new StreamObserver<>() {
                @Override
                public void onNext(Trace.TraceRequest request) {

                    String traceInfo = String.format("Service: %s | Timestamp: %d | Method : %s | Status: %s | Message: %s\n",
                            request.getServiceName(), request.getTimestamp(),
                            request.getMethod(), request.getStatus(), request.getMessage());

                    traceData.add(request);
                    System.out.println(traceInfo);
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("Error during streamTrace: " + t);
                    responseObserver.onError(t);
                }

                @Override
                public void onCompleted() {
                    Trace.TraceResponse response = Trace.TraceResponse.newBuilder()
                            .setStatus(200)
                            .setMessage("All traces received successfully")
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                }
            };
        }
    }

}
