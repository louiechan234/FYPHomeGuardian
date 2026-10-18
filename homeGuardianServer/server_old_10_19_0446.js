const express = require('express');
const path = require('path');
const fs = require('fs');

const app = express();
const PORT = 3000;
const USAGE_FILE = path.join(__dirname, 'usage_data.json');

app.use(express.json());

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
        
        const dataCount = req.body.rawData ? req.body.rawData.length : 0;
        console.log(`📥 收到數據！前台 App: ${req.body.currentActiveApp || '未知'}，共 ${dataCount} 筆，時間: ${uploadTime}`);
        
        res.status(200).json({ message: "數據儲存成功！" });
    } catch (error) {
        console.error("儲存失敗:", error);
        res.status(500).json({ error: "伺服器儲存失敗" });
    }
});

// 2. 查看存下來的 JSON
app.get('/view_data', (req, res) => {
    if (fs.existsSync(USAGE_FILE)) {
        res.sendFile(USAGE_FILE);
    } else {
        res.send("目前還沒有收到任何數據喔！");
    }
});

// 3. 伺服器下發更新 JSON 給 App
let latestData = {
    "version": 1,
    "message": "這是伺服器的初始數據，一切正常。",
    "forceUpdate": false
};

app.get('/get_update', (req, res) => {
    res.json(latestData);
});

app.get('/trigger', (req, res) => {
    latestData.version += 1;
    latestData.message = "伺服器在 " + new Date().toLocaleTimeString() + " 發佈了更新！";
    latestData.forceUpdate = true;
    console.log(`🚀 伺服器觸發更新，版本: ${latestData.version}`);
    res.send("✅ 更新已就緒，Android App 將在幾秒內拉取。");
});

// ==========================================
// ✅ 新增：遠端鎖定功能
// ==========================================
let lockState = {
    isLocked: false,
    packageName: ""
};

// 家長鎖定特定 App (例如: http://localhost:3000/lock?package=com.android.chrome)
app.get('/lock', (req, res) => {
    const pkg = req.query.package;
    if (pkg) {
        lockState.isLocked = true;
        lockState.packageName = pkg;
        console.log(`🔒 發送鎖定指令！目標: ${pkg}`);
        res.send(`✅ 已成功鎖定: ${pkg}`);
    } else {
        res.send("❌ 請提供 package 參數，例如: /lock?package=com.android.chrome");
    }
});

// 家長解除鎖定
app.get('/unlock', (req, res) => {
    lockState.isLocked = false;
    lockState.packageName = "";
    console.log(`🔓 已解除鎖定！`);
    res.send("✅ 已解除所有鎖定");
});

// App 查詢鎖定狀態
app.get('/get_lock_status', (req, res) => {
    res.json(lockState);
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running at http://0.0.0.0:${PORT}`);
});
