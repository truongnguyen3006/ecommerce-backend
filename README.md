# Hệ thống Backend Thương mại điện tử Microservices (Tối ưu hóa Flash Sale)

![Java](https://img.shields.io/badge/Java-24-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-green)
![Kafka](https://img.shields.io/badge/Apache_Kafka-KRaft-black)
![Redis](https://img.shields.io/badge/Redis-Caching-red)
![Docker](https://img.shields.io/badge/Docker-Infrastructure-blue)

## 📖 Giới thiệu
Đây là hệ thống **Backend Thương mại điện tử** được thiết kế chuyên biệt để giải quyết bài toán chịu tải cao trong các sự kiện mua sắm lớn như **Flash Sale**. Hệ thống chuyển đổi từ kiến trúc Nguyên khối (Monolithic) sang **Microservices** kết hợp với **Event-Driven Architecture** để tối ưu hóa thông lượng (Throughput) và khả năng mở rộng (Scalability).

[cite_start]Trọng tâm của dự án là giải quyết các thách thức về **Giao dịch phân tán (Distributed Transactions)** và **Nhất quán dữ liệu (Data Consistency)** mà không làm giảm hiệu năng hệ thống[cite: 1736, 1737].

## 🚀 Các giải pháp kỹ thuật nổi bật

### 1. Kiến trúc Hướng sự kiện (Event-Driven Architecture)
* [cite_start]**Vấn đề:** Các cuộc gọi REST đồng bộ (Synchronous) gây nghẽn cổ chai và tăng độ trễ khi lưu lượng truy cập tăng đột biến[cite: 1721].
* **Giải pháp:** Sử dụng **Apache Kafka** (chế độ KRaft mới nhất) làm xương sống giao tiếp. [cite_start]Các tác vụ nặng như cập nhật kho, thanh toán, gửi thông báo được xử lý bất đồng bộ (Non-blocking) giúp phản hồi người dùng tức thì[cite: 1806].

### 2. Xử lý Giao dịch phân tán (SAGA Pattern)
* **Vấn đề:** Làm sao đảm bảo tính toàn vẹn dữ liệu giữa `Order Service`, `Inventory Service` và `Payment Service` khi mỗi dịch vụ dùng một Database riêng?
* **Giải pháp:** Triển khai mẫu thiết kế **SAGA Choreography**. [cite_start]Nếu thanh toán thất bại, hệ thống tự động kích hoạt "Giao dịch bù trừ" (Compensating Transaction) để hoàn trả hàng vào kho, đảm bảo dữ liệu luôn đúng[cite: 1810, 2041].

### 3. Giải quyết bài toán "Bán quá số lượng" (Overselling)
* [cite_start]**Vấn đề:** Hàng nghìn người cùng bấm mua 1 sản phẩm còn lại trong kho cùng một lúc (Race Condition)[cite: 1792].
* **Giải pháp:**
    * [cite_start]Sử dụng **Redis Atomic Operations** (`HINCRBY`) tại Cart Service để xử lý giỏ hàng an toàn đa luồng[cite: 2037].
    * [cite_start]Sử dụng **Kafka Streams** kết hợp với **RocksDB** (State Store) tại Inventory Service để tính toán tồn kho thời gian thực với độ trễ thấp nhất[cite: 2012, 2013].

### 4. Tối ưu hóa Hiệu năng (Performance Tuning)
* [cite_start]**API Gateway:** Chạy trên nền tảng **Netty** (Non-blocking I/O) với cấu hình tinh chỉnh TCP/IP để chịu tải kết nối lớn[cite: 1941, 2025].
* [cite_start]**Database:** Tinh chỉnh **HikariCP** Connection Pool để tránh cạn kiệt kết nối[cite: 1738].
* [cite_start]**Security:** Sử dụng cơ chế xác thực phi trạng thái (Stateless) với **JWT** và **Keycloak**[cite: 1827].

## 🛠 Công nghệ sử dụng

| Phân lớp | Công nghệ | Chi tiết |
| :--- | :--- | :--- |
| **Ngôn ngữ** | Java 24 | [cite_start]Sử dụng tính năng mới nhất của Java[cite: 1972]. |
| **Framework** | Spring Boot 3 | [cite_start]Spring Cloud Gateway, Spring WebFlux, Spring Data JPA[cite: 1974]. |
| **Messaging** | Apache Kafka | [cite_start]Chế độ KRaft (không Zookeeper), Kafka Streams[cite: 1744]. |
| **Database** | MySQL, Redis | [cite_start]MySQL cho lưu trữ bền vững, Redis cho Caching & Locking[cite: 1930]. |
| **Bảo mật** | Keycloak | [cite_start]OAuth2 / OpenID Connect Provider[cite: 1952]. |
| **Hạ tầng** | Docker, Nginx | [cite_start]Docker Compose quản lý Middleware, Nginx Load Balancer[cite: 1750]. |
| **Giám sát** | Zipkin, Prometheus | [cite_start]Truy vết phân tán (Distributed Tracing) và Metrics[cite: 1961]. |

## 📂 Danh sách Microservices

Hệ thống bao gồm các dịch vụ lõi sau:

| Service Name | Port | Chức năng chính |
| :--- | :--- | :--- |
| **API Gateway** | `8080` | Cổng vào duy nhất, Định tuyến, Rate Limiting, JWT Security. |
| **Discovery Server** | `8761` | Netflix Eureka (Service Registry). |
| **Inventory Service** | `8082` | Quản lý kho, Kafka Streams Topology. |
| **Order Service** | `8086` | Quản lý đơn hàng, SAGA Orchestrator. |
| **Cart Service** | `8081` | Giỏ hàng In-Memory (Redis). |
| **Product Service** | `8083` | Quản lý sản phẩm, Cache dữ liệu đọc nhiều. |
| **Payment Service** | `8089` | Xử lý thanh toán (Mock). |
| **Notification Service**| `8087` | WebSocket Server đẩy thông báo Real-time. |
| **User Service** | `8088` | Quản lý thông tin người dùng. |

## ⚙️ Hướng dẫn Cài đặt & Chạy (Môi trường Hybrid)

[cite_start]Để tối ưu tài nguyên phát triển, dự án chạy theo mô hình **Hybrid**: Các phần mềm nền tảng (Middleware) chạy trên Docker, các Microservices chạy trực tiếp trên máy Host (IntelliJ IDEA)[cite: 1749].

### Bước 1: Chuẩn bị môi trường
* Java JDK 24
* Maven 3.8+
* Docker & Docker Compose

### Bước 2: Khởi chạy Hạ tầng (Middleware)
Di chuyển vào thư mục chứa file `docker-compose.yml` và chạy lệnh:

```bash
docker-compose up -d
Lệnh này sẽ khởi động: Kafka, Zookeeper (hoặc KRaft controller), Redis, MySQL, Keycloak, Zipkin, Prometheus, Grafana.

Bước 3: Khởi chạy Microservices
Thứ tự khởi động bắt buộc để hệ thống hoạt động đúng:

Discovery Server (Eureka) - Chờ khởi động xong hoàn toàn.

API Gateway - Chờ kết nối thành công với Eureka.

Core Services: Inventory, Product, Order, Cart... (Thứ tự không quan trọng).

4. Kiểm tra hệ thống (API Endpoints)
Dưới đây là một số API chính để kiểm thử:
Method	Endpoint	Mô tả	Auth
GET	http://localhost:8080/api/product	Lấy danh sách sản phẩm	❌
POST	http://localhost:8080/auth/login	Đăng nhập (lấy Token từ Keycloak)	❌
POST	http://localhost:8080/api/cart/add/{userId}	Thêm sản phẩm vào giỏ hàng	✅
POST	http://localhost:8080/api/order	Đặt hàng (Checkout)	✅
(Lưu ý: Các API có Auth yêu cầu Header Authorization: Bearer <access_token>)
📝 License
Dự án này là một phần của đề tài niên luận ngành Mạng máy tính & Truyền thông dữ liệu.
