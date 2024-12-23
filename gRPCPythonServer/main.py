import pandas as pd
import grpc
import dashboard_pb2
import dashboard_pb2_grpc
from concurrent import futures
import threading
import requests


class DashboardServicer(dashboard_pb2_grpc.DashboardServiceServicer):

    def __init__(self):
        self.trace_data = []

    def SendTraceInfo(self, request_iterator, context):

        for request in request_iterator:
            try:
                trace_info = {
                    'service_name': request.service_name,
                    'timestamp': request.timestamp,
                    'method': request.method,
                    'status': request.status,
                    'message': request.message
                }
                self.trace_data.append(trace_info)
            except Exception as e:
                print(f"Error processing request: {e}")
                context.set_details('Error processing trace data')
                context.set_code(grpc.StatusCode.INTERNAL)
                return dashboard_pb2.DashboardResponse(status=500, message="Error processing trace data")

        response = dashboard_pb2.DashboardResponse(status=200, message="Trace data received successfully")
        threading.Thread(target=self.process_and_plot_data).start()

        return response

    def process_and_plot_data(self):
        if not self.trace_data:
            print("No trace data available to process and plot.")
            return

        df = pd.DataFrame(self.trace_data)
        df['timestamp'] = pd.to_datetime(df['timestamp'], unit='ms')

        print("\n===================")
        print("Trace Data Update!")
        print("===================")

        status_counts = df['status'].value_counts(normalize=True) * 100
        print("\n[ Status Code Proportion (%) ]")
        print("--------------------------------------------------")
        for status, percentage in status_counts.items():
            print(f"Status {status}: {percentage:.2f}%")

        method_counts = df['method'].value_counts()
        print("\n[ Request Method Frequency ] ")
        print("--------------------------------------------------")
        for method, count in method_counts.items():
            print(f"Method {method}: {count} requests")

        df = df.sort_values(by='timestamp')
        df['response_time_diff'] = df['timestamp'].diff().dt.total_seconds()
        average_response_time = df['response_time_diff'].mean()
        print("\n[ Average Response Time (seconds) ] ")
        print("--------------------------------------------------")
        print(f"Average Response Time: {average_response_time:.2f} seconds")

        def categorize_status(status):
            if 200 <= status < 300:
                return "2xx (Success)"
            elif 400 <= status < 500:
                return "4xx (Client Error)"
            elif 500 <= status < 600:
                return "5xx (Server Error)"
            else:
                return "Other"

        df['status_category'] = df['status'].apply(categorize_status)
        status_category_counts = df['status_category'].value_counts()
        print("\n[ Status Code Grouping ] ")
        print("--------------------------------------------------")
        for category, count in status_category_counts.items():
            print(f"{category}: {count} occurrences")

        message_counts = df['message'].value_counts()
        print("\n[ Message Frequency Analysis ] ")
        print("--------------------------------------------------")
        for message, count in message_counts.items():
            print(f"Message: '{message}' - {count} occurrences")

        response = requests.post('http://localhost:8080/dashboard', json=self.trace_data)
        print(f"\nPython Api server : {response.text}\n")


def server():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    dashboard_pb2_grpc.add_DashboardServiceServicer_to_server(DashboardServicer(), server)
    server.add_insecure_port('0.0.0.0:9091')
    print("Python Server started on port 9091")
    server.start()
    server.wait_for_termination()


if __name__ == '__main__':
    server()
