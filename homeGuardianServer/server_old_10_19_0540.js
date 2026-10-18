const express = require('express');
const path = require('path');
const fs = require('fs');

const app = express();
const PORT = 3000;
const USAGE_FILE = path.join(__dirname, 'usage_data.json');

app.use(express.json({ limit: '50mb' }));

// 1. 接收 App 上傳的使用時間
app.post('/data', (req, res) => {
    try {
        const now = new Date();
        const uploadTime = now.toLocaleString('zh-TW', { timeZone: 'Asia/Hong_Kong' });
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

// 2. 查看存下來的 JSON
app.get('/view_data', (req, res) => {
    if (fs.existsSync(USAGE_FILE)) res.sendFile(USAGE_FILE);
    else res.send("目前還沒有收到任何數據喔！");
});

// 3. 伺服器下發更新 JSON 給 App
let latestData = { version: 1, message: "一切正常", forceUpdate: false };
app.get('/get_update', (req, res) => res.json(latestData));

// ==========================================
// ✅ 核心修改：多 App 黑名單機制
// ==========================================
let lockedApps = []; // 改為陣列，可以存多個

// 鎖定 App (可以重複呼叫加入多個)
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
