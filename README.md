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
- **遠端強制鎖定**：每 3 秒向伺服器拉取黑名單，若發現小孩正在使用被鎖定的 App，會立刻彈出**全螢幕紅色警告畫面**(仲有少少問題，如先打開要鎖定的App，再於後台鎖定，會無法即時鎖定，要切換一下畫面才能鎖定）。

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
FYPHomeGuardian/
├── homeGuardianServer/     # Node.js 後端伺服器
│   ├── server.js           # 主程式 (API 路由、資料處理)
│   ├── admin.html          # 家長控制台網頁
│   ├── usage_data.json     # (執行後產生) 儲存最新的使用時間資料
│   └── package.json        # 依賴設定檔
│
└── HomeGuardian/             # Android 小孩端專案 (Android Studio)
    ├── app/src/main/
    │   ├── java/hk/homeguardian/
    │   │   ├── MainActivity.kt          # 前台 UI (顯示使用時間)
    │   │   ├── UsageTrackerService.kt   # 背景服務 (上傳資料、輪詢鎖定狀態)
    │   │   └── LockScreenActivity.kt    # 紅色警告鎖定畫面
    │   ├── res/
    │   └── AndroidManifest.xml
    └── build.gradle.kts
```

## 🚀 安裝與運行指南

1. 啟動 Node.js 伺服器
請確保電腦已安裝 Node.js。
```
bash
### 進入伺服器資料夾
cd Server

### 安裝依賴套件
npm install express

### 啟動伺服器
node server.js
```

啟動成功後，開啟瀏覽器進入 http://localhost:3000/admin 即可看到家長控制台。

### 2. 編譯並運行 Android App

1.使用 Android Studio 開啟 Android 專案。
2.**⚠️ 網路設定 (極度重要)**：
3.打開 `UsageTrackerService.kt` 與 `MainActivity.kt`。

若使用 **Android 模擬器**：請將 IP 保持為 `http://10.0.2.2:3000/`。

若使用 **實體手機**：請將 IP 改為你電腦的區域網路 IP（例如：http://192.168.x.xxx:3000/），且手機與電腦需在同一個 Wi-Fi 下。

3.執行 App。


### 🔑 必要權限設定 (Permissions)
為了讓系統正常運作，小孩端 App **必須**開啟以下權限，否則功能會失效：

1.**使用量資料存取權** (Usage Access)：用於統計 App 使用時間。

2.**通知權限** (Notifications)：用於顯示背景服務的常駐通知，防止被系統殺掉。

3.**顯示在其他應用程式上層** (Display over other apps)：**（最重要）** 若無此權限，遠端鎖定的紅色警告畫面將無法彈出。(自已入setting改)

## 📡 後端 API 端點 (API Endpoints)

| Method | Endpoint | 說明 |
| :--- | :--- | :--- |
| **POST** | `/data` | 接收小孩端上傳的時間與 Base64 圖片資料 |
| **GET** | `/admin` | 載入家長控制台網頁 |
| **GET** | `/get_all_apps` | 獲取小孩手機所有 App 列表與鎖定狀態 |
| **GET** | `/get_icon/:package` | 獲取特定 App 的圖片 (轉換為 JPEG) |
| **GET** | `/lock?package=xxx` | 將特定 App 加入黑名單 |
| **GET** | `/unlock?package=xxx` | 將特定 App 移出黑名單 |
| **GET** | `/view_data` | 查看原始 JSON 資料庫 (供開發除錯用) |
