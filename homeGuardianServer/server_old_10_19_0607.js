const express = require('express');
const path = require('path');
const fs = require('fs');

const app = express();
const PORT = 3000;
const USAGE_FILE = path.join(__dirname, 'usage_data.json');

// ✅ 放寬限制以接收圖片
app.use(express.json({ limit: '50mb' }));

// ✅ 用來儲存所有 App 的圖片 (Base64) 與 最新狀態
let appIcons = {};
let latestKidStatus = "Unknown";

// 1. 接收 App 上傳的使用時間
app.post('/data', (req, res) => {
    try {
        const now = new Date();
        const uploadTime = now.toLocaleString('zh-TW', { timeZone: 'Asia/Hong_Kong' });
        
        // ✅ 提取並儲存圖片
        if (req.body.rawData) {
            req.body.rawData.forEach(app => {
                if (app.iconBase64 && app.iconBase64 !== "") {
                    appIcons[app.packageName] = app.iconBase64;
                }
            });
        }
        
        // ✅ 記錄小孩當前正在用的 App
        if (req.body.currentActiveApp) {
            latestKidStatus = req.body.currentActiveApp;
        }

        const dataToSave = {
            uploadTime: uploadTime,
            currentActiveApp: req.body.currentActiveApp, 
            data: req.body.rawData 
        };
        fs.writeFileSync(USAGE_FILE, JSON.stringify(dataToSave, null, 4), 'utf8');
        
        console.log(`📥 收到數據！前台 App: ${latestKidStatus}，時間: ${uploadTime}`);
        res.status(200).json({ message: "OK" });
    } catch (error) {
        console.error("儲存失敗:", error);
        res.status(500).json({ error: "伺服器儲存失敗" });
    }
});

// 2. 查看存下來的 JSON
app.get('/view_data', (req, res) => {
    if (fs.existsSync(USAGE_FILE)) res.sendFile(USAGE_FILE);
    else res.send("目前還沒有收到任何數據喔！");
});

// 3. 伺服器下發更新 JSON 給 App
let latestData = { version: 1, message: "一切正常", forceUpdate: false };
app.get('/get_update', (req, res) => res.json(latestData));

// ==========================================
// ✅ 家長控制台需要的 API
// ==========================================

// 取得小孩最新狀態
app.get('/get_kid_status', (req, res) => {
    res.json({ currentActiveApp: latestKidStatus });
});

// 取得特定 App 的圖片 (轉換為真實圖片檔案)
app.get('/get_icon/:package', (req, res) => {
    const pkg = req.params.package;
    const base64String = appIcons[pkg];
    
    if (base64String && base64String.includes('base64,')) {
        // 把 "data:image/jpeg;base64," 這段前綴切掉，只留後面的資料
        const base64Data = base64String.split('base64,')[1];
        // 將 Base64 字串轉換為二進位的 Buffer
        const imgBuffer = Buffer.from(base64Data, 'base64');
        
        // 告訴瀏覽器這是一張 JPEG 圖片
        res.writeHead(200, {
            'Content-Type': 'image/jpeg',
            'Content-Length': imgBuffer.length
        });
        // 發送圖片資料
        res.end(imgBuffer);
    } else {
        res.status(404).send('Not found');
    }
});

// 黑名單機制
let lockedApps = []; 

// 鎖定 App
app.get('/lock', (req, res) => {
    const pkg = req.query.package;
    if (pkg && !lockedApps.includes(pkg)) {
        lockedApps.push(pkg);
        console.log(`🔒 新增鎖定目標: ${pkg}`);
    }
    res.send(`目前鎖定的 App: [ ${lockedApps.join(', ')} ]`);
});

// 解鎖單一 App
app.get('/unlock', (req, res) => {
    const pkg = req.query.package;
    if (pkg) {
        lockedApps = lockedApps.filter(app => app !== pkg);
        console.log(`🔓 解除鎖定: ${pkg}`);
    }
    res.send(`目前鎖定的 App: [ ${lockedApps.join(', ')} ]`);
});

// 解除所有鎖定
app.get('/unlock_all', (req, res) => {
    lockedApps = [];
    console.log(`🔓 已解除所有鎖定！`);
    res.send("✅ 已解除所有鎖定");
});

// App 查詢鎖定狀態
app.get('/get_lock_status', (req, res) => {
    res.json({ lockedApps: lockedApps });
});

// 控制台網頁
app.get('/admin', (req, res) => {
    res.sendFile(path.join(__dirname, 'admin.html'));
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running at http://0.0.0.0:${PORT}`);
});
