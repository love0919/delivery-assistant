# 外送助手 Pro｜自動抓單版

保留原本的 WebView 計時器介面，新增 Android 無障礙服務：

- 只監看 Uber Driver（`com.ubercab.driver`）
- 從進單畫面文字擷取 `$97` 這類「貨幣符號＋金額」
- 偵測到新金額後，自動建立獨立訂單並立即開始計時
- 目前最多同時 4 筆，超過時不新增
- 同一進單畫面短時間重複事件會去重
- 金額、計時與歷史紀錄仍由原本網頁邏輯處理
- 每日訂單編號從 UE #1 重新開始

## 第一次使用

1. 安裝 APK。
2. 第一次開啟會詢問「開啟自動抓單」。
3. 點「前往設定」→「已安裝的應用程式／無障礙」→ 開啟「外送助手 Pro 自動抓單」。
4. 回到外送助手 Pro。
5. 打開 Uber Driver，出現例如 `$97` 的進單畫面時，外送助手會自動建立訂單並開始計時。

## 注意

此版本使用 Android AccessibilityService 讀取 Uber Driver 畫面中的可讀文字；如果 Uber 後續把金額改成非文字圖像，可能需要再加入 OCR。不要在行車中操作設定。

## GitHub Actions

`.github/workflows/build-apk.yml` 可用 GitHub Actions 建立 debug APK。
