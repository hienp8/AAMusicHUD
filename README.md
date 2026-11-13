# AA Music
Trình phát nhạc hiện đại cho điện thoại và Android Auto.

- Mở app vào màn hình nghe nhạc; nút Thư viện, Hàng đợi, Equalizer, Cài đặt nằm ở thanh trên
- Ghi nhớ hàng đợi, bài đang phát, vị trí, ngẫu nhiên / lặp lại và kết quả quét tag
- Thư viện: bài hát, thư mục, yêu thích, playlist, album, nghệ sĩ, radio; ẩn / xóa bài
- Đọc / ghi tag ID3 (tên, nghệ sĩ, album, bìa, lời), kiểm tra toàn thư viện, tìm thủ công
- Lời bài hát đồng bộ (chỉnh lệch thời gian), hiện thay tên nghệ sĩ trên Android Auto, thông báo, màn hình khoá
- Âm thanh: Equalizer 10 dải, bass booster, preamp, limiter, âm trường, tăng âm lượng
- Android Auto: duyệt hàng đợi / yêu thích / playlist / thư viện, tìm bằng giọng nói
- Hình DiOTO thay ảnh bìa trên xe (cần root), có dải ngang 2:1 cho chế độ thu nhỏ

## Build (GitHub Actions)
`settings.gradle.kts`, `app/`, `.github/` phải ở thư mục GỐC repo. Tab Actions > "Build AA Music" > Run workflow,
tải artifact `AA-Music` (file `AA Music.apk`).

## Khi cập nhật từ bản cũ
Xoá các file không còn dùng: TrafficMonitor.kt, PointStore.kt, Overpass.kt, Goong.kt, DataSync.kt, Speaker.kt,
thư mục data/ và .github/workflows/update-data.yml.
