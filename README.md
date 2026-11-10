# AA Music HUD (v2)
Trình phát nhạc kiểu Poweramp / Sony Music, có:
- Thư viện (bài hát, album, nghệ sĩ, radio), mini player, màn hình Đang phát
- Lời bài hát đồng bộ theo thời gian thực (LRC, tải từ LRCLIB)
- Tìm tên / album / ảnh bìa trên internet (iTunes Search) và chỉnh sửa thông tin (lưu trong app)
- Equalizer + Bass booster
- Hiển thị trên Android Auto (thẻ media: tên bài, tốc độ, giới hạn, cảnh báo camera), đọc cảnh báo bằng giọng nói
- Dữ liệu cảnh báo: CSV offline (xem data/) + OpenStreetMap trực tuyến, có màn hình chẩn đoán trong Cài đặt

## Build bằng GitHub Actions
`settings.gradle.kts` và `.github/workflows/build.yml` phải nằm ở thư mục GỐC repo.
Tab Actions > "Build APK" > Run workflow > tải artifact `AAMusicHud-debug-apk`.

## Android Auto không cần AA XLauncher
Android Auto > chạm 10 lần vào Phiên bản > menu ⋮ > Cài đặt dành cho nhà phát triển > bật "Nguồn không xác định".
