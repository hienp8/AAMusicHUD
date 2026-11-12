# AA Music
Trình phát nhạc hiện đại cho điện thoại và Android Auto.

- Mở app vào màn hình nghe nhạc; nút Thư viện, Hàng đợi, Equalizer, Cài đặt nằm ngay trên đó
- Thư viện: bài hát, thư mục, yêu thích, playlist, album, nghệ sĩ, radio; hàng đợi; ẩn / xóa bài
- Đọc / ghi tag ID3 (tên, nghệ sĩ, album, bìa, lời), kiểm tra toàn thư viện, tìm thủ công
- Lời bài hát đồng bộ; dòng lời hiện trên màn hình khoá và thông báo; nút "Lời" mở toàn màn hình
- Equalizer 10 dải (dọc) + Bass booster (ngang)
- Android Auto: duyệt hàng đợi / yêu thích / playlist / thư viện, tìm bằng giọng nói, lời đồng bộ thay tên nghệ sĩ, cảnh báo camera / giới hạn tốc độ, hoặc hình chụp từ Wyn / DiOTO thay ảnh bìa (cần root)

## Build (GitHub Actions)
`settings.gradle.kts`, `app/`, `.github/` phải ở thư mục GỐC repo. Tab Actions > "Build AA Music" > Run workflow,
tải artifact `AA-Music` (file `AA Music.apk`).

## Dữ liệu cảnh báo
Chạy workflow "Update traffic data" một lần để tạo `data/vn_cameras.csv` và `data/vn_signs.csv`.
