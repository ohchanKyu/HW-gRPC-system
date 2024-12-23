from flask import Flask, request, render_template
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import io
import base64
import matplotlib

matplotlib.use('Agg')

app = Flask(__name__)
df = pd.DataFrame()

def update_data(new_data):
    global df
    new_df = pd.DataFrame(new_data)
    new_df['timestamp'] = pd.to_datetime(new_df['timestamp'], unit='ms')
    df = new_df

def plot_status_distribution():
    global df
    if df.empty:
        return None
    status_counts = df['status'].value_counts(normalize=True) * 100
    plt.figure(figsize=(8, 6))
    sns.barplot(x=status_counts.index, y=status_counts.values, hue=status_counts.index, palette="Blues_d", legend=False)
    plt.title('Status Code Proportion', fontsize=16)
    plt.xlabel('Status Code', fontsize=12)
    plt.ylabel('Percentage (%)', fontsize=12)
    plt.grid(axis='y', linestyle='--', alpha=0.7)

    buf = io.BytesIO()
    plt.savefig(buf, format='png')
    buf.seek(0)
    img = base64.b64encode(buf.read()).decode('utf-8')
    buf.close()
    return img


def plot_method_frequency():
    global df
    if df.empty:
        return None
    method_counts = df['method'].value_counts()
    plt.figure(figsize=(8, 6))
    sns.barplot(x=method_counts.index, y=method_counts.values, hue=method_counts.index, palette="Oranges_d", legend=False)
    plt.title('Request Method Frequency', fontsize=16)
    plt.xlabel('Method', fontsize=12)
    plt.ylabel('Frequency', fontsize=12)
    plt.grid(axis='y', linestyle='--', alpha=0.7)

    buf = io.BytesIO()
    plt.savefig(buf, format='png')
    buf.seek(0)
    img = base64.b64encode(buf.read()).decode('utf-8')
    buf.close()
    return img


def plot_avg_response_time():
    global df
    if df.empty:
        return None
    df = df.sort_values(by='timestamp')
    df['response_time_diff'] = df['timestamp'].diff().dt.total_seconds()
    average_response_time = round(df['response_time_diff'].mean(), 2)
    return average_response_time


def plot_status_category():
    global df
    if df.empty:
        return None
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

    plt.figure(figsize=(8, 6))
    sns.barplot(x=status_category_counts.index, y=status_category_counts.values, hue=status_category_counts.index, palette="Purples_d", legend=False)
    plt.title('Status Code Grouping', fontsize=16)
    plt.xlabel('Status Category', fontsize=12)
    plt.ylabel('Count', fontsize=12)
    plt.grid(axis='y', linestyle='--', alpha=0.7)

    buf = io.BytesIO()
    plt.savefig(buf, format='png')
    buf.seek(0)
    img = base64.b64encode(buf.read()).decode('utf-8')
    buf.close()
    return img


def plot_message_frequency():
    global df
    if df.empty:
        return None
    message_counts = df['message'].value_counts()
    return message_counts.to_dict()



@app.route('/dashboard', methods=['POST'])
def update_data_route():
    global df
    new_data = request.json
    update_data(new_data)
    return "Data updated successfully!"


@app.route('/dashboard')
def dashboard():
    status_img = plot_status_distribution()
    method_img = plot_method_frequency()
    average_response_time = plot_avg_response_time()
    status_category_img = plot_status_category()
    message_frequency_data = plot_message_frequency()

    return render_template('dashboard.html',
                           status_img=status_img,
                           method_img=method_img,
                           average_response_time=average_response_time,
                           status_category_img=status_category_img,
                           message_frequency_data=message_frequency_data)


if __name__ == '__main__':
    app.run(debug=True, host="0.0.0.0", port=8080)
