# AA Music
Trình phát nhạc hiện đại cho điện thoại và Android Auto.

- Thư viện, mini player, màn hình Đang phát (nền màu theo ảnh bìa), lời bài hát đồng bộ
- Đọc / ghi tag ID3 (tên, nghệ sĩ, album, bìa, lời) trực tiếp trong file nhạc
- Kiểm tra toàn bộ thư viện, đề xuất bổ sung tag từ internet
- Equalizer dọc + Bass booster, thoát hoàn toàn
- Cảnh báo camera / giới hạn tốc độ trên thẻ media của Android Auto, đọc bằng giọng nói

## Build (GitHub Actions)
`settings.gradle.kts`, `app/`, `.github/` phải ở thư mục GỐC repo. Tab Actions > "Build AA Music" > Run workflow,
tải artifact `AA-Music` (file `AA Music.apk`).

## Dữ liệu cảnh báo
Chạy workflow "Update traffic data" một lần để tạo `data/vn_cameras.csv` và `data/vn_signs.csv`
(lấy từ OpenStreetMap). App tự tải hai file này mỗi ngày.

## Cập nhật đè không báo "không tương thích"
Mọi bản build dùng chung khoá ký `app/aamusic.keystore` và versionCode tăng theo số lần build.
