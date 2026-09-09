# VCore 0.8 (Release)

Mã nguồn phiên bản **Release** (tên do Dev đặt) của client **VCore** dành cho **Minecraft 1.21 / 1.21.1 (Fabric)**.

## Credits

- File `.jar` leak bởi: **Glux** (Discord ID: `376860086619996178`) trong **Cheat Club** (https://discord.gg/cheathouse).
- Source remap & build setup: FTL 2nd.

---

## Hướng dẫn Build

### Yêu cầu môi trường
- **JDK**: Java 21 trở lên (khuyến nghị JDK 21 LTS).
- Dự án đã tích hợp sẵn Gradle Wrapper, không cần cài đặt Gradle riêng.

### Các bước build

- **Trên Windows**:
  ```cmd
  .\gradlew.bat build
  ```

- **Trên Linux / macOS**:
  ```bash
  chmod +x gradlew
  ./gradlew build
  ```

Sau khi quá trình biên dịch hoàn tất (`BUILD SUCCESSFUL`), file mod thành phẩm sẽ nằm tại:
```text
build/libs/vcore-Release.jar
```

---

## Hướng dẫn Sử dụng

1. **Cài đặt môi trường:**
   - Cài đặt **Minecraft 1.21** hoặc **1.21.1**.
   - Cài đặt **Fabric Loader** (khuyến nghị phiên bản `0.15.11` trở lên) cùng **Fabric API**.

2. **Cài đặt Mod:**
   - Copy file `vcore-Release.jar` (nằm trong thư mục `build/libs/`) vào thư mục `.minecraft/mods/`.
   - Khởi động Minecraft với profile Fabric.

3. **Thao tác trong Game:**
   - **Mở Menu ClickGUI:** Nhấn phím `RIGHT SHIFT` (Shift phải).
   - **Lệnh (Commands):** Sử dụng tiền tố (prefix) `@` trong khung chat (Ví dụ: `@help`, `@bind`, `@cfg`...).
