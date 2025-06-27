# gRPC-System

## HW 개요
이 애플리케이션은 Java gRPC Client ⇄ Java gRPC Server ⇄ Python gRPC Server 구조로, <br>
클라이언트 스트리밍을 통해 요청 데이터를 수집하고 분석하는 시스템입니다. <br>
Java는 데이터 수집을, Python은 분석 및 시각화를 담당하며, <br>
분석 결과는 Flask 서버의 /dashboard URI를 통해 실시간으로 사용자에게 시각화되어 제공됩니다. <br>
gRPC의 다중 언어 지원과 Python의 분석/그래프 능력을 효과적으로 결합한 구조입니다. <br>

<br>

## Java ⇄ Python 기반 gRPC 데이터 흐름
#### ﻿Java gRPC Client ⇄ Server
Java gRPC Client는 클라이언트 스트리밍 방식으로 다수의 요청/응답 데이터를 Java gRPC Server에 전송합니다. <br>
Server는 이 데이터를 수집한 뒤, 20초 간격으로 Python gRPC Server에 스트리밍 전송하여 분석 작업을 위임합니다. <br>

<br>

#### Python gRPC Server의 데이터 분석
Python gRPC Server는 Pandas를 이용해 요청/응답 패턴을 분석하고, 평균 응답 시간, 요청 유형 빈도 등 주요 지표를 도출합니다. <br>
이를 통해 시스템 상태를 진단하고 인사이트를 제공합니다. <br>

<br>

#### Flask Server + Matplotlib를 통해 시각화 및 제공
분석된 데이터는 Flask 서버에 HTTP POST 방식으로 전달되며, <br>
Flask는 데이터를 matplotlib를 이용해 시각화하고 /dashboard를 통해 사용자에게 실시간 그래프를 제공합니다. <br>

<br>

## **Directory**
```
📦DashboardServer
 ┣ 📂templates
 ┃ ┗ 📜dashboard.html
 ┗ 📜main.py
📦gRPCJavaServer
 ┗ 📂gRPCProject
 ┃ ┣ 📂src
 ┃ ┃ ┗ 📂main
 ┃ ┃ ┃ ┣ 📂java
 ┃ ┃ ┃ ┃ ┗ 📂kr
 ┃ ┃ ┃ ┃ ┃ ┗ 📂ac
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📂dankook
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜CentralTraceServer.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜TraceClient.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TraceClientCaller.java
 ┃ ┃ ┃ ┗ 📂proto
 ┃ ┃ ┃ ┃ ┣ 📜dashboard.proto
 ┃ ┃ ┃ ┃ ┗ 📜trace.proto
 ┃ ┣ 📜build.gradle
 ┃ ┗ 📜settings.gradle
📦gRPCPythonServer
 ┣ 📜dashboard.proto
 ┗ 📜main.py
```

<br>


## 주요 Proto File
### trace.proto ( Java gRPC Client ⇄ Java gRPC Server )
```proto
syntax = "proto3";
  option java_multiple_files = false;
  option java_package = "kr.ac.dankook";
  service TraceService {
  rpc StreamTrace (stream TraceRequest) returns (TraceResponse);
  }
  message TraceRequest {
  int64 request_id = 1;
  string service_name = 2;
  int64 timestamp = 3;
  string method = 4;
  int64 status = 5;
  string message = 6;
  }
  message TraceResponse {
  int64 status = 1;
  string message = 2;
}
```
<br> 

### dashboard.proto ( Java gRPC Server ⇄ Python gRPC Server )
```proto
syntax = "proto3";
message DashboardRequest {
  string service_name = 1;
  int64 timestamp = 2;
  string method = 3;
  int32 status = 4;
  string message = 5;
}
message DashboardResponse {
  int32 status = 1;
  string message = 2;
}
service DashboardService {
  rpc SendTraceInfo (stream DashboardRequest) returns (DashboardResponse);
}
```

<br>

## 애플리케이션 시나리오
![image](https://github.com/user-attachments/assets/2f84363f-3ca4-40ee-8c38-88b47b748649)

<br>

1️⃣ Java gRPC Client ⇒ Java gRPC Server (Client Streaming) <br>
Java gRPC Client는 Trace 데이터를 생성하여 Java gRPC Server에 Client Streaming 방식으로 전송합니다. <br>
Java gRPC Server는 모든 Trace 데이터를 수신한 뒤, 하나의 응답 메시지를 Client에게 반환합니다. <br>

2️⃣ Java gRPC Server ⇒ Python gRPC Server (주기적 전송) <br>
Java gRPC Server 내부 백그라운드 스레드는 20초마다 수집된 Trace 데이터 전체를 <br>
Python gRPC Server로 전송합니다. 전송은 역시 Client Streaming 방식이며, <br>
전달 완료 후 Python gRPC Server로부터 응답 메시지를 수신합니다.

3️⃣ Python gRPC Server: 데이터 분석 및 전송 <br>
Python gRPC Server는 수신한 Trace 데이터를 기반으로 Pandas를 활용한 분석을 수행합니다. <br>
수신 데이터가 1건 이상일 경우, 지금까지 누적된 전체 Trace 데이터를 포함하여 <br>
Flask Server에 POST 요청을 보냅니다. <br>

4️⃣ Flask Server: 데이터 저장 및 시각화 <br>
Flask Server는 POST 요청으로 전달된 Trace 데이터를 갱신 및 저장합니다. <br>
사용자가 /dashboard에 GET 요청을 보내면, matplotlib을 활용한 그래프를 생성하여 <br>
시각화된 대시보드를 제공합니다. <br>

<br>

## **Settings**
### Ubuntu 기본 환경 구축
#### 사용자 홈 디렉토리로 이동 및 패키지 목록 업데이트
```Bash
cd ~
sudo apt update
```
#### JDK 21 다운로드 및 설정
```Bash
﻿wget https://download.oracle.com/java/21/archive/jdk-21_linux-x64_bin.tar.gz
﻿tar -xzf jdk-21_linux-x64_bin.tar.gz
﻿sudo mv jdk-21 /usr/local/
﻿nano ~/.bashrc
```
﻿※ 파일의 맨 아래에 다음 내용을 추가
```Bash
﻿export JAVA_HOME=/usr/local/jdk-21
export PATH=$JAVA_HOME/bin:$PATH
```
※ 변경 사항 적용
```Bash
source ~/.bashrc
```
#### 프로젝트 clone 및 해당 프로젝트로 이동
```Bash
git clone https://github.com/ohchanKyu/HW-gRPC-system.git
cd ./HW-gRPC-system
```

<br>

### 파이썬 라이브러리 설치
#### 필요한 패키지 설치
```Bash
sudo apt-get install python3-pip
pip3 install flask pandas matplotlib seaborn
```
#### Python gRPC 및 gRPC Python 코드 생성
```Bash
cd ./gRPCPythonServer
pip3 install grpcio grpcio-tools requests
python3 -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. ./dashboard.proto
```
#### Java gRPC 코드 생성 및 컴파일
```Bash
cd ./gRPCJavaServer/gRPCProject
chmod +x gradlew
./gradlew generateProto
./gradlew build
```
<br>

### 3개의 서버 및 클라이언트 실행
#### Flask 서버 실행
```Bash
cd ./DashboardServer
python3 main.py
```
#### Pyhon gRPC Server 실행
```Bash
cd ./gRPCPythonServer
python3 main.py
```
#### Java gRPC Server 실행
```Bash
cd ./gRPCJavaServer/gRPCProject
./gradlew runCentralTraceServer
```
#### Java gRPC Client 실행
```Bash
cd ./gRPCJavaServer/gRPCProject
./gradlew runTraceClient
```

<br>

## **Test Application**
### Java gRPC Server에서 Python gRPC Server에게 데이터 전달
Java gRPC Server에서 클라이언트 스트리밍을 통해 Python gRPC Server에게 데이터를 전달합니다. <br>
Trace 데이터는 모두 Java gRPC Server에서 List로 관리중이며, 모든 리스트에 있는 데이터를 <br>
스트리밍을 통해 전달합니다. 전달이 모두 완료되면 Python gRPC Server로부터 <br>
하나의 응답 메시지를 전달받습니다. <br>

**Java gRPC Server 출력 화면** <br>
![image](https://github.com/user-attachments/assets/38b86bbe-e6c8-40d0-b15d-0c5daf5673e0)

<br>

### 스트리밍 데이터를 전달받은 Python gRPC Server 
Python gRPC Server에서는 스트리밍으로 Java Server로부터 모든 추적 데이터를 전달받고, <br>
Pandas 라이브러리를 통해 해당 데이터를 분석합니다. 하지만 아직 Server로부터 받은 <br>
추적 데이터의 개수가 0이므로 다음과 같은 출력을 하고 Flask Server에게 아무 데이터도 전달하지 않습니다. <br>

- **Python gRPC Server 출력 화면** <br>
![image](https://github.com/user-attachments/assets/2b5fe8b9-6066-418f-b990-55929b74c7ee)

- **Flask Server 렌더링 초기 화면** <br>
```
http://localhost:8080/dashboard
```
![image](https://github.com/user-attachments/assets/80c8fe47-d0fc-4462-a705-e03df192b13a)

<br>

### Java gRPC Client 실행
```Bash
cd ./gRPCJavaServer/gRPCProject
./gradlew runTraceClient
```
Java gRPC Client에서 클라이언트 스트리밍을 통해 Java gRPC Server에게 추적 데이터를 전달합니다. <br>
Trace 정보는 모두 Java gRPC Server에서 List로 관리되며, Java gRPC Client는 for문을 돌면서 50개의 <br>
추적 데이터를 클라이언트 스트리밍을 통해 Java gRPC Server에게 전달합니다. <br>
전달이 모두 완료되면 Java gRPC Server로부터 하나의 응답 메시지를 전달 받습니다. <br>

- **Java gRPC Client 출력 화면** <br>
![image](https://github.com/user-attachments/assets/bebf815c-84c8-4fcf-b3d3-f9b0cc1723e0)

- **모든 데이터 전송 후 Java gRPC Server로부터 받은 메세지 출력** <br>
![image](https://github.com/user-attachments/assets/8ba00463-8122-444b-9a11-8e39f877a52d)

- **데이터를 전달 받은 Java gRPC Server의 출력** <br>
![image](https://github.com/user-attachments/assets/20642da2-e2af-4b25-94a3-52ea05b1684c)

- **데이터를 전달 받은 Python gRPC Server의 출력** <br>
- 응답 코드 비율 / HTTP Method 요청 빈도 수 출력 <br>
![image](https://github.com/user-attachments/assets/1aa516e1-8dd0-4cd8-b8cd-6e79a26f8e2f)

<br>
- 평균 응답 시간 / 응답 코드의 그룹화 / 응답 메시지 빈도 수 출력 <br>

![image](https://github.com/user-attachments/assets/e1b80da7-fe19-40b6-8f14-17aa63f858f5)

**데이터를 전달 받은 Flask Server** <br>
Flask Server는 Python gRPC Server에게 데이터가 유효하다면, 20초마다 POST 요청을 받습니다. <br>

- **POST 요청을 받은 Flask Server의 출력** <br>
![image](https://github.com/user-attachments/assets/e7eff57b-4f04-4d69-8e13-e349e173c661)

- **데이터를 반영한 웹 페이지 화면** <br>
![image](https://github.com/user-attachments/assets/1d5003a5-ab7a-4b71-a38c-7b071a0186fc)
![image](https://github.com/user-attachments/assets/fa0d1e35-c4c4-4802-9874-63921fcd0cc2)

