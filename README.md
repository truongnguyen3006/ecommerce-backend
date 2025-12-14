# Hệ thống Backend Thương mại điện tử Microservices (Tối ưu hóa Flash Sale)

![Java](https://img.shields.io/badge/Java-24-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green?style=flat-square&logo=springboot)
![Kafka](https://img.shields.io/badge/Apache_Kafka-KRaft-black?style=flat-square&logo=apachekafka)
![Redis](https://img.shields.io/badge/Redis-Caching-red?style=flat-square&logo=redis)
![Docker](https://img.shields.io/badge/Docker-Infrastructure-blue?style=flat-square&logo=docker)

## 📖 Giới thiệu

Đây là hệ thống **Backend Thương mại điện tử** được thiết kế chuyên biệt để giải quyết bài toán chịu tải cao trong các sự kiện mua sắm lớn như **Flash Sale**. Dự án này là kết quả của đề tài *"Nghiên cứu giải pháp tối ưu hóa và mở rộng hệ thống thương mại điện tử ứng dụng kiến trúc Microservices và Event-Driven"*.

Hệ thống chuyển đổi từ kiến trúc Nguyên khối (Monolithic) sang **Microservices** kết hợp với **Event-Driven Architecture** để tối ưu hóa thông lượng (Throughput), giảm độ trễ (Latency) và đảm bảo tính toàn vẹn dữ liệu trong môi trường phân tán.

## 🚀 Các giải pháp kỹ thuật nổi bật

### 1. Kiến trúc Hướng sự kiện (Event-Driven Architecture)
- **Vấn đề:** Các cuộc gọi REST đồng bộ (Synchronous) truyền thống gây nghẽn cổ chai (Blocking) khi lưu lượng truy cập tăng đột biến.
- **Giải pháp:** Sử dụng **Apache Kafka** (chế độ KRaft mới nhất - không cần ZooKeeper) làm xương sống giao tiếp. Các tác vụ nặng như cập nhật kho, thanh toán, gửi thông báo được xử lý bất đồng bộ (Non-blocking), giúp phản hồi người dùng tức thì.

### 2. Xử lý Giao dịch phân tán (SAGA Pattern)
- **Vấn đề:** Đảm bảo tính toàn vẹn dữ liệu giữa `Order Service`, `Inventory Service` và `Payment Service` khi mỗi dịch vụ sử dụng Database riêng biệt (Database per Service).
- **Giải pháp:** Triển khai mẫu thiết kế **SAGA Choreography**. Nếu thanh toán hoặc trừ kho thất bại, hệ thống tự động kích hoạt "Giao dịch bù trừ" (Compensating Transaction) để hoàn trả trạng thái về ban đầu (Rollback), đảm bảo dữ liệu luôn nhất quán (Eventual Consistency).

### 3. Giải quyết bài toán "Bán quá số lượng" (Overselling)
- **Vấn đề:** Hàng nghìn người cùng bấm mua 1 sản phẩm còn lại trong kho cùng một lúc (Race Condition).
- **Giải pháp:**
    - **Redis Atomic Operations:** Sử dụng lệnh `HINCRBY` tại Cart Service để xử lý thao tác thêm giỏ hàng an toàn đa luồng.
    - **Kafka Streams & RocksDB:** Sử dụng Kafka Streams tại Inventory Service để tính toán tồn kho thời gian thực với độ trễ thấp nhất, lưu trữ trạng thái cục bộ trên RocksDB thay vì truy vấn Database liên tục.

### 4. Tối ưu hóa Hiệu năng (Performance Tuning)
- **API Gateway:** Chạy trên nền tảng **Netty** (Non-blocking I/O) với cấu hình tinh chỉnh TCP/IP để chịu tải hàng ngàn kết nối đồng thời.
- **Database Connection:** Tinh chỉnh **HikariCP** Connection Pool để tối ưu hóa kết nối cơ sở dữ liệu.
- **Security:** Sử dụng cơ chế xác thực phi trạng thái (Stateless) với **JWT** và **Keycloak**, giảm tải cho việc quản lý Session.

## 🛠 Công nghệ sử dụng

| Phân lớp | Công nghệ | Chi tiết |
| :--- | :--- | :--- |
| **Ngôn ngữ** | Java 24 | Tận dụng tính năng mới nhất (Virtual Threads) |
| **Framework** | Spring Boot 3 | Spring Cloud Gateway, Spring WebFlux, Spring Data JPA |
| **Messaging** | Apache Kafka | Chế độ KRaft, Kafka Streams |
| **Database** | MySQL, Redis | MySQL cho dữ liệu nghiệp vụ, Redis cho Caching & Locking |
| **Bảo mật** | Keycloak | OAuth2 / OpenID Connect Provider |
| **Hạ tầng** | Docker, Nginx | Docker Compose quản lý Middleware, Nginx làm Load Balancer |
| **Giám sát** | Zipkin, Prometheus, Grafana | Truy vết phân tán (Tracing) và trực quan hóa Metrics |

## ⚙️ Yêu cầu môi trường (Prerequisites)

Trước khi cài đặt, vui lòng đảm bảo máy tính của bạn đã cài đặt các công cụ sau (nhấn vào tên để tải về):

1.  [**Java JDK 24**](https://jdk.java.net/24/) - Môi trường chạy Java.
2.  [**Docker Desktop**](https://www.docker.com/products/docker-desktop/) - Để chạy hạ tầng Middleware (Bắt buộc bật WSL2 trên Windows).
3.  [**Git**](https://git-scm.com/downloads) - Để tải mã nguồn.
4.  [**Apache Maven**](https://maven.apache.org/download.cgi) (3.8+) - Công cụ build dự án.
5.  [**IntelliJ IDEA**](https://www.jetbrains.com/idea/download/) (Khuyên dùng) - IDE để chạy Microservices.

## 💾 Hướng dẫn Cài đặt & Chạy

Hệ thống được thiết lập chạy theo mô hình **Hybrid**: Các phần mềm nền tảng (Middleware) chạy trên Docker, các Microservices chạy trực tiếp trên máy Host (Localhost) để tối ưu tài nguyên phát triển.

### Bước 1: Tải mã nguồn
Mở Terminal hoặc Command Prompt và chạy lệnh sau:

```bash
git clone [https://github.com/truongnguyen3006/ecommerce-backend.git](https://github.com/truongnguyen3006/ecommerce-backend.git)
cd ecommerce-microservices-backend
Dựa trên nội dung trong file PDF báo cáo của bạn và các yêu cầu cụ thể (thêm link tải công cụ, hướng dẫn git clone, tham chiếu tác giả), dưới đây là bản README.md được viết lại hoàn chỉnh, chuyên nghiệp và chi tiết.

Bạn có thể copy đoạn mã dưới đây vào file README.md trên Github của bạn.

Markdown

# Hệ thống Backend Thương mại điện tử Microservices (Tối ưu hóa Flash Sale)

![Java](https://img.shields.io/badge/Java-24-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green?style=flat-square&logo=springboot)
![Kafka](https://img.shields.io/badge/Apache_Kafka-KRaft-black?style=flat-square&logo=apachekafka)
![Redis](https://img.shields.io/badge/Redis-Caching-red?style=flat-square&logo=redis)
![Docker](https://img.shields.io/badge/Docker-Infrastructure-blue?style=flat-square&logo=docker)

## 📖 Giới thiệu

Đây là hệ thống **Backend Thương mại điện tử** được thiết kế chuyên biệt để giải quyết bài toán chịu tải cao trong các sự kiện mua sắm lớn như **Flash Sale**. Dự án này là kết quả của đề tài *"Nghiên cứu giải pháp tối ưu hóa và mở rộng hệ thống thương mại điện tử ứng dụng kiến trúc Microservices và Event-Driven"*.

Hệ thống chuyển đổi từ kiến trúc Nguyên khối (Monolithic) sang **Microservices** kết hợp với **Event-Driven Architecture** để tối ưu hóa thông lượng (Throughput), giảm độ trễ (Latency) và đảm bảo tính toàn vẹn dữ liệu trong môi trường phân tán.

## 🚀 Các giải pháp kỹ thuật nổi bật

### 1. Kiến trúc Hướng sự kiện (Event-Driven Architecture)
- **Vấn đề:** Các cuộc gọi REST đồng bộ (Synchronous) truyền thống gây nghẽn cổ chai (Blocking) khi lưu lượng truy cập tăng đột biến.
- **Giải pháp:** Sử dụng **Apache Kafka** (chế độ KRaft mới nhất - không cần ZooKeeper) làm xương sống giao tiếp. Các tác vụ nặng như cập nhật kho, thanh toán, gửi thông báo được xử lý bất đồng bộ (Non-blocking), giúp phản hồi người dùng tức thì.

### 2. Xử lý Giao dịch phân tán (SAGA Pattern)
- **Vấn đề:** Đảm bảo tính toàn vẹn dữ liệu giữa `Order Service`, `Inventory Service` và `Payment Service` khi mỗi dịch vụ sử dụng Database riêng biệt (Database per Service).
- **Giải pháp:** Triển khai mẫu thiết kế **SAGA Choreography**. Nếu thanh toán hoặc trừ kho thất bại, hệ thống tự động kích hoạt "Giao dịch bù trừ" (Compensating Transaction) để hoàn trả trạng thái về ban đầu (Rollback), đảm bảo dữ liệu luôn nhất quán (Eventual Consistency).

### 3. Giải quyết bài toán "Bán quá số lượng" (Overselling)
- **Vấn đề:** Hàng nghìn người cùng bấm mua 1 sản phẩm còn lại trong kho cùng một lúc (Race Condition).
- **Giải pháp:**
    - **Redis Atomic Operations:** Sử dụng lệnh `HINCRBY` tại Cart Service để xử lý thao tác thêm giỏ hàng an toàn đa luồng.
    - **Kafka Streams & RocksDB:** Sử dụng Kafka Streams tại Inventory Service để tính toán tồn kho thời gian thực với độ trễ thấp nhất, lưu trữ trạng thái cục bộ trên RocksDB thay vì truy vấn Database liên tục.

### 4. Tối ưu hóa Hiệu năng (Performance Tuning)
- **API Gateway:** Chạy trên nền tảng **Netty** (Non-blocking I/O) với cấu hình tinh chỉnh TCP/IP để chịu tải hàng ngàn kết nối đồng thời.
- **Database Connection:** Tinh chỉnh **HikariCP** Connection Pool để tối ưu hóa kết nối cơ sở dữ liệu.
- **Security:** Sử dụng cơ chế xác thực phi trạng thái (Stateless) với **JWT** và **Keycloak**, giảm tải cho việc quản lý Session.

## 🛠 Công nghệ sử dụng

| Phân lớp | Công nghệ | Chi tiết |
| :--- | :--- | :--- |
| **Ngôn ngữ** | Java 24 | Tận dụng tính năng mới nhất (Virtual Threads) |
| **Framework** | Spring Boot 3 | Spring Cloud Gateway, Spring WebFlux, Spring Data JPA |
| **Messaging** | Apache Kafka | Chế độ KRaft, Kafka Streams |
| **Database** | MySQL, Redis | MySQL cho dữ liệu nghiệp vụ, Redis cho Caching & Locking |
| **Bảo mật** | Keycloak | OAuth2 / OpenID Connect Provider |
| **Hạ tầng** | Docker, Nginx | Docker Compose quản lý Middleware, Nginx làm Load Balancer |
| **Giám sát** | Zipkin, Prometheus, Grafana | Truy vết phân tán (Tracing) và trực quan hóa Metrics |

## ⚙️ Yêu cầu môi trường (Prerequisites)

Trước khi cài đặt, vui lòng đảm bảo máy tính của bạn đã cài đặt các công cụ sau (nhấn vào tên để tải về):

1.  [**Java JDK 24**](https://jdk.java.net/24/) - Môi trường chạy Java.
2.  [**Docker Desktop**](https://www.docker.com/products/docker-desktop/) - Để chạy hạ tầng Middleware (Bắt buộc bật WSL2 trên Windows).
3.  [**Git**](https://git-scm.com/downloads) - Để tải mã nguồn.
4.  [**Apache Maven**](https://maven.apache.org/download.cgi) (3.8+) - Công cụ build dự án.
5.  [**IntelliJ IDEA**](https://www.jetbrains.com/idea/download/) (Khuyên dùng) - IDE để chạy Microservices.

## 💾 Hướng dẫn Cài đặt & Chạy

Hệ thống được thiết lập chạy theo mô hình **Hybrid**: Các phần mềm nền tảng (Middleware) chạy trên Docker, các Microservices chạy trực tiếp trên máy Host (Localhost) để tối ưu tài nguyên phát triển.

### Bước 1: Tải mã nguồn
Mở Terminal hoặc Command Prompt và chạy lệnh sau:

```bash
git clone [https://github.com/truongnguyen3006/ecommerce-microservices-backend.git](https://github.com/truongnguyen3006/ecommerce-microservices-backend.git)
cd ecommerce-microservices-backend
(Lưu ý: Thay thế đường dẫn repo nếu tên repository thực tế khác)

Bước 2: Khởi chạy Hạ tầng (Middleware)
Di chuyển vào thư mục chứa file docker-compose.yml (thường nằm ở thư mục gốc hoặc thư mục docker) và chạy lệnh:
docker-compose up -d
⏳ Chờ khoảng 2-5 phút để các container khởi động hoàn toàn. Lệnh này sẽ dựng:

Kafka Cluster (KRaft)

Redis

MySQL Business DB

Keycloak & Keycloak DB

Zipkin, Prometheus, Grafana
Bước 3: Khởi chạy Microservices
Mở dự án bằng IntelliJ IDEA. Chạy các service theo đúng thứ tự sau để tránh lỗi kết nối:

Discovery Server (Eureka) - Port 8761 (Chờ khởi động xong hoàn toàn).

API Gateway - Port 8080 (Chờ kết nối thành công với Eureka).

Các Service nghiệp vụ (Chạy song song):

inventory-service - Port 8082

product-service - Port 8083

order-service - Port 8086

cart-service - Port 8081

user-service - Port 8088

payment-service - Port 8089

notification-service - Port 8087

📂 Cấu trúc Service & Port
Service Name	Port	Chức năng chính
API Gateway	8080	Cổng vào duy nhất, Định tuyến, Rate Limiting, Security.
Discovery Server	8761	Netflix Eureka (Service Registry).
Inventory Service	8082	Quản lý kho, Xử lý Kafka Streams Topology.
Product Service	8083	Quản lý thông tin sản phẩm, Cache dữ liệu đọc nhiều.
Order Service	8086	Quản lý đơn hàng, Điều phối SAGA.
Cart Service	8081	Giỏ hàng hiệu năng cao (In-Memory Redis).
User Service	8088	Quản lý hồ sơ người dùng & Đồng bộ Keycloak.
Payment Service	8089	Giả lập xử lý thanh toán (Mock Payment).
Notification	8087	WebSocket Server đẩy thông báo Real-time.

🧪 Kiểm tra hệ thống (API Endpoints)
Bạn có thể sử dụng Postman để test các API sau:
Method	Endpoint	Mô tả	Yêu cầu Auth
GET	http://localhost:8080/api/product	Lấy danh sách sản phẩm	❌
POST	http://localhost:8080/auth/login	Đăng nhập (lấy Token từ Keycloak)	❌
POST	http://localhost:8080/api/cart/add/{userId}	Thêm sản phẩm vào giỏ hàng	✅
POST	http://localhost:8080/api/order/checkout	Đặt hàng (Kích hoạt SAGA flow)	✅

Lưu ý: Với các API có Auth (✅), bạn cần thêm Header: Authorization: Bearer <access_token_nhan_duoc_khi_login>

Được thực hiện bởi:

Nguyễn Lâm Trường

Khoa: Mạng Máy Tính & Truyền Thông - Đại học Cần Thơ
