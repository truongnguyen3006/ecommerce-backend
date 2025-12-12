# Hệ thống Thương mại điện tử Microservices Hiệu năng cao (Tối ưu hóa Flash Sale)

![Java](https://img.shields.io/badge/Java-24-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-green)
![Kafka](https://img.shields.io/badge/Apache_Kafka-KRaft-black)
![Redis](https://img.shields.io/badge/Redis-Caching-red)

## 📖 Giới thiệu
Dự án này là hệ thống **Backend Thương mại điện tử** được thiết kế để xử lý các kịch bản lưu lượng truy cập cao như **Flash Sale**. Hệ thống chuyển đổi từ kiến trúc Nguyên khối (Monolithic) sang **Microservices** để giải quyết các vấn đề về hiệu năng và khả năng mở rộng.

Trọng tâm của dự án là đảm bảo tính nhất quán dữ liệu trong giao dịch phân tán và xử lý độ trễ thấp bằng cách sử dụng **Kiến trúc hướng sự kiện (Event-Driven Architecture)**.

## 🚀 Tính năng nổi bật & Giải pháp kỹ thuật

* **Kiến trúc Event-Driven:** Tách biệt các dịch vụ bằng **Apache Kafka** (chế độ KRaft) để đảm bảo thông lượng cao và xử lý bất đồng bộ (Non-blocking).
* **Giao dịch phân tán (SAGA):** Triển khai mẫu thiết kế **SAGA Choreography** để đảm bảo tính nhất quán dữ liệu giữa các dịch vụ Đơn hàng (Order), Thanh toán (Payment) và Kho hàng (Inventory) mà không cần khóa Database.
* **Xử lý tồn kho chịu tải cao:** Giải quyết triệt để vấn đề "Bán quá số lượng" (Overselling/Race Conditions) bằng **Kafka Streams** để xử lý trạng thái (Stateful processing) và các **Thao tác nguyên tử trên Redis (Redis Atomic Operations)**.
* **Tối ưu hóa hiệu năng:** Tinh chỉnh sâu **Netty** (cho API Gateway), HikariCP (Connection Pool) và cấu hình Kafka Producer để chịu tải tối đa.
* **Giám sát hệ thống (Observability):** Tích hợp **Zipkin** và **Micrometer** để truy vết phân tán xuyên suốt (End-to-end distributed tracing).

## 🛠 Công nghệ sử dụng

* **Core Framework:** Java 24, Spring Boot 3, Spring Cloud (Gateway, Netflix Eureka).
* **Messaging & Streaming:** Apache Kafka (KRaft mode), Kafka Streams.
* **Database & Cache:** MySQL, Redis, RocksDB (State Store).
* **Bảo mật:** Keycloak (OAuth2 / OpenID Connect).
* **Hạ tầng:** Docker, Docker Compose, Nginx Load Balancer.
* **Giám sát:** Zipkin, Prometheus, Grafana.

## 📂 Cấu trúc Microservices

| Tên Service | Port | Mô tả |
| :--- | :--- | :--- |
| **API Gateway** | `8080` | Cổng vào duy nhất, chạy trên nền Netty, xác thực JWT, Rate Limiting. |
| **Discovery Server** | `8761` | Netflix Eureka dùng cho Service Registry. |
| **Inventory Service** | `8082` | Xử lý logic Kafka Streams để cập nhật tồn kho thời gian thực. |
| **Order Service** | `8086` | Điều phối SAGA, tiếp nhận đơn hàng bất đồng bộ. |
| **Cart Service** | `8081` | Quản lý giỏ hàng tập trung vào Redis (High write throughput). |
| **Product Service** | `8083` | Danh mục sản phẩm với cơ chế Multi-layer Caching. |
| **Notification Service**| `8087` | Đẩy thông báo thời gian thực qua WebSocket. |

## ⚙️ Hướng dẫn cài đặt (Môi trường Hybrid)

Dự án này chạy trên **Môi trường Lai (Hybrid)**: Middleware chạy trên Docker, trong khi các Microservices chạy trực tiếp trên máy Host (IntelliJ IDEA) để tiện debug.

### 1. Yêu cầu tiên quyết
* Java JDK 24
* Docker & Docker Compose
* Maven

### 2. Khởi chạy Hạ tầng
Chạy Kafka, Redis, MySQL, Keycloak và Zipkin bằng Docker Compose:

```bash
cd docker-infrastructure
docker-compose up -d

3. Khởi chạy Microservices
Thứ tự khởi động khuyến nghị:

Discovery Server (Eureka)

API Gateway

Config/Auth Services (nếu có)

Core Services: Inventory, Product, Order, Cart...

4. Tài liệu API
Import bộ Collection Postman được cung cấp (trong thư mục /docs).

Endpoints công khai:

GET /api/product: Xem danh sách sản phẩm.

POST /auth/login: Lấy Access Token qua Keycloak.

