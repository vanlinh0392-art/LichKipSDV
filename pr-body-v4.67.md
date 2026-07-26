## Thay đổi

- Khắc phục báo thức sau reboot: thêm `LOCKED_BOOT_COMPLETED`, `directBootAware` cho chuỗi báo thức và lưu trạng thái alarm/MDM trong device-protected storage.
- Tính lịch lễ âm bằng `VietCalendar` động sau năm 2029; thêm cache tiền tố theo năm để tính ca không còn quét từng ngày lặp lại khi vuốt lịch.
- Tách định danh PendingIntent của snooze khỏi báo thức ca chính để không âm thầm hủy lịch ngày hôm sau.
- Sửa thống kê HO ngày lễ, dùng lại `isOfficialHol` để tính đúng kíp ca Ngày.
- Giữ các cải tiến Auto MDM: không chạy pending service khi thiếu VSelfLock, gọi foreground đúng thứ tự, và tinh chỉnh switch iOS compact.

## Lý do

Các lỗi này có thể làm mất báo thức sau reboot, làm lệch chu kỳ ca từ năm 2030, tạo foreground service vô ích hoặc làm sai thống kê HO.

## Kiểm tra

- `testDebugUnitTest`
- `assembleRelease`
- `lintRelease`
- `apksigner verify --print-certs`
- APK `v4.67`, versionCode `88`, debug certificate SHA-256 `bb22b0a39ec267e89efe324e99680891e35a73f735b54b549abb7966d724d963`
