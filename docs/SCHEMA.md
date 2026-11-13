# Thiết kế cơ sở dữ liệu và trạng thái đơn

Múi giờ nghiệp vụ: **Asia/Ho_Chi_Minh**. Schema do **Flyway** quản lý: `src/main/resources/db/migration/V1__init.sql`.

Profile `mysql`: `ddl-auto=validate`, Flyway bật.<br>
Profile `demo`: H2 trong bộ nhớ, `ddl-auto=create-drop`, Flyway tắt — **chỉ minh họa**, mất dữ liệu khi tắt app.

## Sơ đồ quan hệ (ERD)

```mermaid
erDiagram
  accounts ||--o{ courts : owns
  accounts ||--o{ bookings : places
  courts ||--o{ bookings : receives
  courts ||--o{ price_rules : has
  courts ||--o{ court_images : has
  courts ||--o{ blocked_slots : blocks

  accounts {
    bigint id PK
    varchar name
    varchar email UK
    varchar phone
    varchar password
    varchar role
    datetime created_at
  }

  courts {
    bigint id PK
    bigint owner_id FK
    varchar name
    varchar sport
    varchar area
    varchar address
    varchar description
    varchar amenities
    decimal price
    decimal min_price
    int open_hour
    int close_hour
    boolean active
    datetime created_at
  }

  price_rules {
    bigint id PK
    bigint court_id FK
    int start_hour
    int end_hour
    decimal price
  }

  court_images {
    bigint id PK
    bigint court_id FK
    varchar stored_name UK
    varchar content_type
    int sort_order
  }

  bookings {
    bigint id PK
    bigint court_id FK
    bigint customer_id FK
    date play_date
    int start_hour
    decimal amount
    varchar status
    varchar active_slot_key UK
    datetime created_at
  }

  blocked_slots {
    bigint id PK
    bigint court_id FK
    date play_date
    int start_hour
    varchar slot_key UK
  }
```

## Chỉ mục và chống trùng

- `accounts.email` unique.
- `courts(active, sport, area)`, `courts(owner_id)`, `courts(min_price)`.
- `bookings(customer_id, created_at)`, `(court_id, play_date)`, `(status, play_date)`.
- `bookings.active_slot_key` **unique nullable**: đơn đang giữ giờ có khóa `courtId:date:hour`. Hủy đặt `NULL` (MySQL cho nhiều NULL).
- `blocked_slots.slot_key` unique cùng định dạng khóa.
- Đặt sân: transaction `READ_COMMITTED` + `SELECT ... FOR UPDATE` trên hàng `courts` + unique key. Hai request cùng khung: một thành công, một lỗi ràng buộc/nghiệp vụ.

## Giá

- `courts.price`: giá mặc định mỗi khung 60 phút.
- `price_rules`: khoảng `[start_hour, end_hour)` đè giá mặc định.
- `courts.min_price`: giá thấp nhất (lọc danh sách), cập nhật khi lưu sân.
- `bookings.amount`: **ảnh giá lúc đặt**. Đổi bảng giá không sửa đơn cũ.

## Ảnh

- File lưu ngoài DB: `{UPLOAD_DIR}/courts/{courtId}/{uuid}.jpg|png|webp`.
- Tên tệp chỉ UUID; không dùng tên gốc; không phục vụ như script.
- Giới hạn 2 MB/ảnh, tối đa 8 ảnh/sân; kiểm tra magic bytes JPEG/PNG/WEBP.

## Máy trạng thái đơn

```text
(đặt thành công) --> CONFIRMED
CONFIRMED --> CANCELLED   khách: trước giờ bắt đầu > 2 giờ
CONFIRMED --> CANCELLED   chủ sân: trước giờ bắt đầu
CONFIRMED --> COMPLETED   sau giờ kết thúc khung + chủ xác nhận đã thu
CANCELLED, COMPLETED: kết thúc, không hoàn tác trên UI
```

Doanh thu = tổng `amount` các đơn `COMPLETED` theo `play_date` trong khoảng lọc, nhóm theo sân của **chủ đang đăng nhập**.
