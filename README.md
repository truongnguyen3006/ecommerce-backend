# 🛒 Hệ thống Backend Thương mại điện tử Microservices (Flash Sale Optimized)

![Java](https://img.shields.io/badge/Java-24-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-green?style=for-the-badge&logo=springboot)
![Kafka](https://img.shields.io/badge/Apache_Kafka-KRaft-black?style=for-the-badge&logo=apachekafka)
![Redis](https://img.shields.io/badge/Redis-Caching-red?style=for-the-badge&logo=redis)
![Docker](https://img.shields.io/badge/Docker-Infrastructure-blue?style=for-the-badge&logo=docker)

## 📖 Giới thiệu

Đây là hệ thống **Backend Thương mại điện tử** được xây dựng để giải quyết bài toán chịu tải cao trong các sự kiện **Flash Sale**. Dự án chuyển đổi từ kiến trúc Nguyên khối (Monolithic) sang **Microservices** kết hợp **Event-Driven Architecture**, tối ưu hóa khả năng xử lý hàng ngàn giao dịch mỗi giây.

Trọng tâm của dự án là giải quyết các thách thức về **Giao dịch phân tán (SAGA)**, **Nhất quán dữ liệu** và **Chống bán lố hàng (Oversell)**.

## 🎯 Mục tiêu & Phạm vi kiến trúc

Repo này được sử dụng như một **case study** cho đồ án tốt nghiệp của mình.

Trọng tâm  muốn thể hiện và sẽ tiếp tục trừu tượng hóa trong đồ án là:
- Cách xử lý **tranh chấp tài nguyên (overselling)** dưới tải cao
- Đảm bảo **nhất quán dữ liệu giữa các microservices** bằng SAGA
- Thiết kế **event-driven flow** giữa Order – Inventory – Payment

Các phần như UI, business logic chi tiết, hay tối ưu triển khai production **không phải trọng tâm chính** của repo này.

---

## 🚀 Giải pháp Kỹ thuật Nổi bật

| Thách thức | Giải pháp áp dụng |
| :--- | :--- |
| **Nghẽn cổ chai (Bottleneck)** | Sử dụng **Event-Driven Architecture** với **Apache Kafka** để xử lý bất đồng bộ (Non-blocking). |
| **Giao dịch phân tán** | Triển khai **SAGA Pattern (Choreography)** để đảm bảo tính toàn vẹn dữ liệu giữa Order, Payment và Inventory. |
| **Bán quá số lượng (Oversell)** | Sử dụng **Redis Atomic (HINCRBY)** và **Kafka Streams** với RocksDB để khóa và trừ tồn kho thời gian thực. |
| **Hiệu năng hệ thống** | Tinh chỉnh **TCP/IP Stack**, cấu hình **HikariCP** và chạy API Gateway trên nền tảng **Netty**. |

---

## 🛠 Công nghệ sử dụng

* **Ngôn ngữ:** [Java 24](https://jdk.java.net/24/) (Virtual Threads).
* **Framework:** Spring Boot 3, Spring Cloud Gateway, Spring WebFlux.
* **Message Broker:** Apache Kafka (Chế độ KRaft - No Zookeeper).
* **Database:** MySQL (Lưu trữ chính), Redis (Cache & Lock).
* **Security:** Keycloak (OAuth2/OpenID Connect).
* **Hạ tầng:** Docker, Nginx Load Balancer.
* **Giám sát:** Zipkin, Prometheus, Grafana.

---

## ⚙️ Yêu cầu cài đặt (Prerequisites)

Trước khi chạy dự án, hãy đảm bảo máy tính của bạn đã cài đặt các công cụ sau:

1.  [**Java JDK 24**](https://jdk.java.net/24/) - Môi trường chạy Java.
2.  [**Docker Desktop**](https://www.docker.com/products/docker-desktop/) - Để chạy hạ tầng (Bắt buộc bật WSL2 trên Windows).
3.  [**Git SCM**](https://git-scm.com/downloads) - Để tải mã nguồn.
4.  [**IntelliJ IDEA**](https://www.jetbrains.com/idea/download/) - IDE khuyên dùng để chạy Microservices.
5.  [**Postman**](https://www.postman.com/downloads/) - Để test API.

---

## 💾 Hướng dẫn Cài đặt & Chạy (Installation)

Hệ thống chạy theo mô hình **Hybrid**: Middleware chạy trên Docker, Microservices chạy trên IDE (Host).

### Bước 1: Clone Repository
Mở Terminal/CMD và chạy lệnh sau để tải dự án về máy:

```bash
git clone https://github.com/truongnguyen3006/ecommerce-microservices-backend.git
cd ecommerce-microservices-backend

Bước 2: Khởi chạy Hạ tầng (Middleware)
Di chuyển vào thư mục chứa file docker-compose.yml và chạy lệnh:
docker-compose up -d

⏳ Chờ khoảng 3-5 phút để 11 container (Kafka, Redis, MySQL, Keycloak, Zipkin...) khởi động hoàn toàn.

Bước 3: Khởi chạy Microservices

Mở dự án bằng IntelliJ IDEA. Chạy các service theo đúng thứ tự sau để tránh lỗi kết nối:

🟢 Discovery Server (Eureka) - Port 8761 (Chờ chạy xong).

🟢 API Gateway - Port 8080 (Chờ kết nối Eureka thành công).

🟢 Các Service nghiệp vụ (Chạy song song):

inventory-service (8082)

product-service (8083)

order-service (8086)

cart-service (8081)

user-service (8088)

payment-service (8089)

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



