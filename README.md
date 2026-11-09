# AA Music HUD
Trình phát nhạc cho Android Auto, hiển thị tốc độ, giới hạn tốc độ, cảnh báo camera/điểm nguy hiểm
lên thẻ media, đọc cảnh báo bằng giọng nói (TTS).

## Nguồn dữ liệu cảnh báo
- File CSV do bạn nhập (offline): xem `data/overpass_queries.md` và các file mẫu `data/sample_*.csv`.
- OpenStreetMap trực tuyến (tuỳ chọn, bật/tắt trong app).

## Build bằng GitHub Actions
1. `settings.gradle.kts` và `.github/` phải nằm ở thư mục GỐC của repo.
2. Tab Actions > "Build APK" > Run workflow (hoặc push lên main).
3. Tải artifact `AAMusicHud-debug-apk` ở cuối trang chạy xong, giải nén lấy file .apk.

## Dùng
Cấp quyền > nhập CSV > Lưu cấu hình > Bắt đầu dịch vụ > kết nối Android Auto.
