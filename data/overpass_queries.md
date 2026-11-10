# Xuất dữ liệu mở từ OpenStreetMap (miễn phí)

1. Vào https://overpass-turbo.eu (mở bằng trình duyệt trên điện thoại hoặc máy tính).
2. Dán một truy vấn bên dưới, bấm Run (Chạy). Với cả nước có thể mất vài phút.
3. Mở tab "Data" (Dữ liệu), sao chép toàn bộ chữ, lưu thành file .csv.
4. Trong app AA Music HUD, bấm nhập CSV tương ứng.

Độ phủ của OSM ở Việt Nam chưa đầy đủ, hãy kiểm tra số lượng điểm trước khi tin vào kết quả.

## Camera tốc độ (nhập vào "camera / cảnh báo")
[out:csv(::lat,::lon,"maxspeed";false;",")][timeout:180];
area["ISO3166-1"="VN"][admin_level=2]->.a;
node["highway"="speed_camera"](area.a);
out;

## Biển giới hạn tốc độ (nhập vào "biển giới hạn")
[out:csv(::lat,::lon,"maxspeed";false;",")][timeout:180];
area["ISO3166-1"="VN"][admin_level=2]->.a;
node["traffic_sign"="maxspeed"]["maxspeed"](area.a);
out;

## Thu hẹp một khu vực (ví dụ quanh Hà Nội)
Thay hai dòng "area" và "node" bằng một dòng dùng hộp toạ độ (nam, tây, bắc, đông):
node["highway"="speed_camera"](20.8,105.6,21.2,106.0);
