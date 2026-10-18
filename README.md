# 🛡️ HomeGuardian - 智能家長控制與螢幕時間監控系統

本專案為 FYP（畢業專題）作品，旨在結合 **邊緣運算 AI 影像辨識 (Jetson Nano)** 與 **Android 系統 API**，打造一個全方位的兒童數位健康守護系統。家長可以透過網頁控制台，遠端監控小孩的手機使用狀況，並即時進行遠端 App 鎖定與時間管理。

## 📌 系統架構

本專案分為兩大已開發模組，以及一個開發中模組：
1. **Android App (小孩端)**：負責背景監控、使用時間統計、資料與圖片上傳、接收遠端鎖定指令。
2. **Node.js Server & 網頁控制台 (家長端)**：負責接收數據、儲存資料、提供即時監控網頁與一鍵鎖定功能。
3. **Jetson Nano + Webcam (AI 監控端 - 開發中)**：透過影像辨識判斷小孩是否在讀書或玩手機，並將結果回傳至伺服器。

## ✨ 核心功能 (已完成)

### 📱 Android App (小孩端)
- **螢幕時間統計**：透過 `UsageStatsManager` 精準取得各 App 的前台使用時間。
- **背景長駐服務**：使用 `Foreground Service` 確保 App 在背景穩定運作，每 30 秒自動上傳數據。
- **App 圖示同步**：自動將手機內 App 圖示轉換為 Base64 字串（JPG 格式）並上傳。
- **遠端強制鎖定**：每 3 秒向伺服器拉取黑名單，若發現小孩正在使用被鎖定的 App，會立刻彈出**全螢幕紅色警告畫面**。

### 💻 網頁控制台 (家長端)
- **即時狀態監控**：即時顯示小孩目前正在使用的 App。
- **視覺化 App 列表**：列出小孩手機上所有 App，包含真實圖示、名稱與今日使用時間。
- **一鍵鎖定/解鎖**：家長只需點擊按鈕，即可遠端封鎖或解鎖特定 App（如：Chrome、YouTube）。

## 🛠️ 技術棧 (Tech Stack)

- **後端與網頁**: Node.js, Express.js, HTML, Vanilla JavaScript
- **客戶端**: Kotlin, Jetpack Compose, Coroutines, Foreground Service, UsageStatsManager
- **AI 邊緣運算 (規劃中)**: Python, NVIDIA Jetson Nano, OpenCV

## 📂 專案目錄結構

```text
HomeGuardian/
├── Server/                 # Node.js 後端伺服器
│   ├── server.js           # 主程式 (API 路由、資料處理)
│   ├── admin.html          # 家長控制台網頁
│   ├── usage_data.json     # (執行後產生) 儲存最新的使用時間資料
│   └── package.json        # 依賴設定檔
│
└── AndroidApp/             # Android 小孩端專案 (Android Studio)
    ├── app/src/main/
    │   ├── java/hk/homeguardian/
    │   │   ├── MainActivity.kt          # 前台 UI (顯示使用時間)
    │   │   ├── UsageTrackerService.kt   # 背景服務 (上傳資料、輪詢鎖定狀態)
    │   │   └── LockScreenActivity.kt    # 紅色警告鎖定畫面
    │   ├── res/
    │   └── AndroidManifest.xml
    └── build.gradle.kts
