# Lịch SDV (Lịch Kíp SDV)

Ứng dụng Android hỗ trợ nhân viên SDV xem lịch làm việc và lịch kíp một cách trực quan, nhanh chóng.

## 🌟 Tính năng chính
* **Xem lịch kíp:** Xem chi tiết lịch làm việc của các kíp A, B, C, HC.
* **Tích hợp tiện ích:** Hỗ trợ phím tắt và các chức năng mở nhanh, quản lý MDM phù hợp cho thiết bị.
* **Hệ thống theo dõi & cập nhật (Tracking System):** 
  * Tự động ghi nhận số lượng cài đặt mới (New Install) và cập nhật phiên bản (App Update) lên Google Sheets.
  * Tự động gửi thông báo thời gian thực về Telegram cá nhân của quản trị viên khi có lượt cài đặt hoặc cập nhật mới.
  * Tối ưu hóa hiệu năng: Tác vụ chạy ngầm siêu nhẹ, chỉ thực hiện kết nối mạng **1 lần duy nhất mỗi ngày** khi mở app, hoàn toàn không gây tốn pin hay tài nguyên máy.
  * Hoạt động an toàn (Fail-safe): Tự động bỏ qua im lặng khi thiết bị không có kết nối internet, đảm bảo không ảnh hưởng đến trải nghiệm người dùng.

## 🔒 Bảo mật thông tin (Security Guidelines)
* **Bảo mật Token**: Toàn bộ thông tin nhạy cảm của hệ thống thông báo (như Telegram Bot Token và Chat ID) được lưu trữ an toàn, độc quyền tại backend Google Apps Script cá nhân của quản trị viên.
* **Không lưu trữ trên GitHub**: Không có bất kỳ API Key hay Bot Token nào được đẩy lên repository công khai trên GitHub. Mã nguồn Android chỉ tương tác thông qua cổng Webhook Web App trung gian của Google Apps Script.
