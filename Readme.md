# Sign Language Recognition
2023梅竹黑客松Google組比賽成果（榮獲第二名），開發供安卓手機用戶使用的手語翻譯app，此repo收錄安卓端部分的實作。

## AI part
Check out https://github.com/ting0602/Sign-Language-Recognition--MediaPipe-DTW.

## 實作簡介
- 使用android Camera2 API存取相機並產生預覽畫面和取得影像截圖。
- 使用Java socket實作client端傳輸影像至電腦端架設的Python server，由server執行AI，包括關節辨識、手語翻譯以及文法轉換等一系列處理，最後回傳翻譯結果給安卓端。
- (UI實作待補)

## Demo
(待補)
