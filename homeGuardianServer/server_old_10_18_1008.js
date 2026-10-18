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
	// 取得目前的時間，格式化為易讀的字串
        const now = new Date();
        const uploadTime = now.toLocaleString('zh-TW', { timeZone: 'Asia/Hong_Kong' }); // 使用香港/台灣時間
        
	// 將收到的資料包裝起來，加上上傳時間
        const dataToSave = {
            uploadTime: uploadTime,
	    currentActiveApp: req.body.currentActiveApp, 
            data: req.body.rawData // 這裡面是原本的 App 使用時間陣列
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

// 2. 讓家長或你在瀏覽器查看存下來的 JSON
app.get('/view_data', (req, res) => {
    if (fs.existsSync(USAGE_FILE)) {
        res.sendFile(USAGE_FILE);
    } else {
        res.send("目前還沒有收到任何數據喔！");
    }
});

// 3. 伺服器下發更新 JSON 給 App (輪詢用)
let latestData = {
    "version": 1,
    "message": "這是伺服器的初始數據，一切正常。",
    "forceUpdate": false
};

app.get('/get_update', (req, res) => {
    res.json(latestData);
});

// 4. 供家長測試：觸發伺服器更新
app.get('/trigger', (req, res) => {
    latestData.version += 1;
    latestData.message = "伺服器在 " + new Date().toLocaleTimeString() + " 發佈了更新！";
    latestData.forceUpdate = true;
    console.log(`🚀 伺服器觸發更新，版本: ${latestData.version}`);
    res.send("✅ 更新已就緒，Android App 將在幾秒內拉取。");
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running at http://0.0.0.0:${PORT}`);
});
