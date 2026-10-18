
const express = require('express');
const path = require('path');
const fs = require('fs');

const app = express();
const PORT = 3000;
const USAGE_FILE = path.join(__dirname, 'usage_data.json');

app.use(express.json({ limit: '50mb' }));

let appIcons = {};
let latestKidStatus = "Unknown";
let latestAppsData = []; // ✅ 新增：儲存最新的 App 列表
let lockedApps = []; 

// 1. 接收 App 上傳
app.post('/data', (req, res) => {
    try {
        const now = new Date();
        const uploadTime = now.toLocaleString('zh-TW', { timeZone: 'Asia/Hong_Kong' });
        
        if (req.body.rawData) {
            latestAppsData = req.body.rawData; // ✅ 更新列表
            req.body.rawData.forEach(app => {
                if (app.iconBase64 && app.iconBase64 !== "") {
                    appIcons[app.packageName] = app.iconBase64;
                }
            });
        }
        
        if (req.body.currentActiveApp) {
            latestKidStatus = req.body.currentActiveApp;
        }

        const dataToSave = {
            uploadTime: uploadTime,
            currentActiveApp: req.body.currentActiveApp, 
            data: req.body.rawData 
        };
        fs.writeFileSync(USAGE_FILE, JSON.stringify(dataToSave, null, 4), 'utf8');
        
        res.status(200).json({ message: "OK" });
    } catch (error) {
        res.status(500).json({ error: "伺服器儲存失敗" });
    }
});

//查看存下來的原始 JSON 檔案
app.get('/view_data', (req, res) => {
    if (fs.existsSync(USAGE_FILE)) {
        res.sendFile(USAGE_FILE);
    } else {
        res.send("目前還沒有收到任何數據喔！");
    }
});

// ✅ 新增：提供給控制台的完整 App 列表 API
app.get('/get_all_apps', (req, res) => {
    // 遍歷最新數據，標記是否已鎖定
    const apps = latestAppsData.map(app => ({
        packageName: app.packageName,
        appName: app.appName,
        totalSeconds: app.totalSeconds,
        isLocked: lockedApps.includes(app.packageName)
    }));
    res.json(apps);
});

// 取得小孩最新狀態
app.get('/get_kid_status', (req, res) => {
    res.json({ currentActiveApp: latestKidStatus });
});

// 取得特定 App 的圖片 (轉換為真實圖片檔案)
app.get('/get_icon/:package', (req, res) => {
    const pkg = req.params.package;
    const base64String = appIcons[pkg];
    
    if (base64String && base64String.includes('base64,')) {
        const base64Data = base64String.split('base64,')[1];
        const imgBuffer = Buffer.from(base64Data, 'base64');
        res.writeHead(200, { 'Content-Type': 'image/jpeg' });
        res.end(imgBuffer);
    } else {
        res.status(404).send('Not found');
    }
});

// 鎖定/解鎖 API (保持不變)
app.get('/lock', (req, res) => {
    const pkg = req.query.package;
    if (pkg && !lockedApps.includes(pkg)) lockedApps.push(pkg);
    res.send("OK");
});

app.get('/unlock', (req, res) => {
    const pkg = req.query.package;
    lockedApps = lockedApps.filter(app => app !== pkg);
    res.send("OK");
});

app.get('/unlock_all', (req, res) => {
    lockedApps = [];
    res.send("OK");
});

app.get('/get_lock_status', (req, res) => {
    res.json({ lockedApps: lockedApps });
});

app.get('/admin', (req, res) => {
    res.sendFile(path.join(__dirname, 'admin.html'));
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running at http://0.0.0.0:${PORT}`);
});
