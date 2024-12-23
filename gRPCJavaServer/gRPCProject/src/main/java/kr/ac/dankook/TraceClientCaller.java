package kr.ac.dankook;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TraceClientCaller {

    private final TraceServiceGrpc.TraceServiceStub asyncStub;
    private Long requestId = 0L;
    private static final Map<Integer, String> statusMap = new HashMap<>();

    static {
        statusMap.put(200, "Request completed successfully.");
        statusMap.put(404, "Resource not found.");
        statusMap.put(403, "Access denied: Unauthorized action.");
        statusMap.put(500, "Internal server error encountered.");
        statusMap.put(401, "Unauthorized: Please provide valid credentials.");
        statusMap.put(400, "Bad request: Check input parameters.");
        statusMap.put(201, "Resource created successfully.");
        statusMap.put(503, "Service unavailable: Please try again later.");
    }

    private final String[] methods = {
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE"
    };
    public TraceClientCaller(ManagedChannel managedChannel) {
        asyncStub = TraceServiceGrpc.newStub(managedChannel);
    }

    public void sendClientStreamingAsync() throws InterruptedException {

        System.out.println(">>> Send Call");
        List<Trace.TraceRequest> traceRequestList = new ArrayList<>();
        for (int i=0;i<50;i++){
            Random random = new Random();
            int index = random.nextInt(methods.length);
            String selectedMethod = methods[index];
            long timestamp = System.currentTimeMillis();
            Object[] keys = statusMap.keySet().toArray();
            int randomStatus = (int) keys[random.nextInt(keys.length)];
            String randomMessage = statusMap.get(randomStatus);
            traceRequestList.add(
                    Trace.TraceRequest.newBuilder()
                            .setRequestId(++requestId)
                            .setServiceName("Web Application")
                            .setTimestamp(timestamp)
                            .setMethod(selectedMethod)
                            .setStatus(randomStatus)
                            .setMessage(randomMessage)
                            .build()
            );
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
            }
        }
        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<Trace.TraceResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(Trace.TraceResponse traceResponse) {
                System.out.println(">>> Response Data : "+traceResponse);
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

        StreamObserver<Trace.TraceRequest> requestObserver = asyncStub.streamTrace(responseObserver);
        try {
            for (Trace.TraceRequest request: traceRequestList) {
                requestObserver.onNext(request);
                System.out.println(">>> Request Info : "
                        + request.getMethod() + " / " + request.getStatus() + " / " + request.getMessage());

                Thread.sleep(1000);
                if (finishLatch.getCount() == 0) {
                    System.out.println(">>> Stop the next request");
                    return;
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Failed to send data to Java Server");
            requestObserver.onError(e);
        }
        requestObserver.onCompleted();
        if (finishLatch.await(1, TimeUnit.MINUTES)){
            System.out.println(">>> End Successful.");
        }
    }
}
