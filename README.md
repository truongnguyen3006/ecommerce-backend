# 🛒 Hệ thống Backend Thương mại điện tử Microservices  
### (Tối ưu hóa Flash Sale & High Traffic)

![Java](https://img.shields.io/badge/Java-24-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green?style=flat-square&logo=springboot)
![Kafka](https://img.shields.io/badge/Apache_Kafka-KRaft-black?style=flat-square&logo=apachekafka)
![Redis](https://img.shields.io/badge/Redis-Caching-red?style=flat-square&logo=redis)
![Docker](https://img.shields.io/badge/Docker-Infrastructure-blue?style=flat-square&logo=docker)

---

## 📖 Giới thiệu

Đây là hệ thống **Backend Thương mại điện tử** được thiết kế nhằm giải quyết bài toán **chịu tải cao** trong các sự kiện mua sắm lớn như **Flash Sale**.

Dự án là kết quả của đề tài:

> **“Nghiên cứu giải pháp tối ưu hóa và mở rộng hệ thống thương mại điện tử  
> ứng dụng kiến trúc Microservices và Event-Driven Architecture”**

Hệ thống chuyển đổi từ kiến trúc **Monolithic** sang **Microservices**, kết hợp **Event-Driven Architecture** để:
- Tăng **Throughput**
- Giảm **Latency**
- Đảm bảo **tính toàn vẹn dữ liệu** trong môi trường phân tán

---

## 🚀 Các giải pháp kỹ thuật nổi bật

### 1️⃣ Event-Driven Architecture (EDA)
- **Vấn đề:** REST synchronous gây nghẽn cổ chai khi traffic tăng đột biến  
- **Giải pháp:**  
  - Sử dụng **Apache Kafka (KRaft – không ZooKeeper)**  
  - Xử lý bất đồng bộ (Non-blocking) các tác vụ:
    - Cập nhật kho
    - Thanh toán
    - Gửi thông báo

👉 Người dùng nhận phản hồi gần như tức thì.

---

### 2️⃣ Xử lý giao dịch phân tán – SAGA Pattern
- **Vấn đề:**  
  Dữ liệu phân tán giữa:
  - `Order Service`
  - `Inventory Service`
  - `Payment Service`
- **Giải pháp:**  
  - Triển khai **SAGA Choreography**
  - Tự động **Compensating Transaction** nếu xảy ra lỗi  
  - Đảm bảo **Eventual Consistency**

---

### 3️⃣ Giải quyết Overselling (Bán quá số lượng)
- **Redis Atomic Operations**  
  - Dùng `HINCRBY` cho thao tác giỏ hàng an toàn đa luồng
- **Kafka Streams + RocksDB**
  - Tính tồn kho real-time
  - Lưu state cục bộ → giảm tải DB

---

### 4️⃣ Tối ưu hiệu năng hệ thống
- **API Gateway:** Netty (Non-blocking I/O)
- **Database Pool:** HikariCP
- **Security:** JWT + Keycloak (Stateless)

---

## 🛠 Công nghệ sử dụng

| Layer | Công nghệ | Chi tiết |
|------|----------|---------|
| Ngôn ngữ | **Java 24** | Virtual Threads |
| Framework | **Spring Boot 3** | WebFlux, JPA, Gateway |
| Messaging | **Apache Kafka** | KRaft, Kafka Streams |
| Database | **MySQL, Redis** | Business & Cache |
| Security | **Keycloak** | OAuth2 / OIDC |
| Infrastructure | **Docker, Nginx** | Docker Compose |
| Monitoring | **Zipkin, Prometheus, Grafana** | Tracing & Metrics |

---

## ⚙️ Yêu cầu môi trường

Vui lòng cài đặt các công cụ sau (click để tải):

1. [Java JDK 24](https://jdk.java.net/24/)
2. [Docker Desktop](https://www.docker.com/products/docker-desktop/)
3. [Git](https://git-scm.com/downloads)
4. [Apache Maven 3.8+](https://maven.apache.org/download.cgi)
5. [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) *(Khuyên dùng)*

---

## 💾 Hướng dẫn cài đặt & chạy

### 1. Clone repository

git clone https://github.com/truongnguyen3006/ecommerce-microservices-backend.git
cd ecommerce-microservices-backend
2. Start middleware services (Docker)
bash
Copy code
docker-compose up -d
The following services will be initialized:

Apache Kafka (KRaft mode)

Redis

MySQL

Keycloak

Zipkin

Prometheus

Grafana

3. Run microservices
Start services in the following order:

Discovery Server (Eureka) – 8761

API Gateway – 8080

Business services (can be run in parallel):

Service	Port
Cart Service	8081
Inventory Service	8082
Product Service	8083
Order Service	8086
Notification Service	8087
User Service	8088
Payment Service	8089

API Testing
Use Postman or similar tools to test the APIs below:

Method	Endpoint	Description	Auth
GET	/api/product	Get product list	No
POST	/auth/login	User login	No
POST	/api/cart/add/{userId}	Add item to cart	Yes
POST	/api/order/checkout	Checkout order (SAGA flow)	Yes

For authenticated APIs, include the following HTTP header:

Copy code
Authorization: Bearer <access_token>
Author
Nguyễn Lâm Trường
Faculty of Computer Networks & Communications
Can Tho University
