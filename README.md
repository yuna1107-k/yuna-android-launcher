# yuna-android-launcher

Android 向けのホームアプリ（ランチャー）です。

> **このプロジェクトは [AOSP Launcher3](https://android.googlesource.com/platform/packages/apps/Launcher3) の派生（フォーク）です。**
> ベースは AOSP `main` の 25Q1 スナップショット（`BP1A.250305.020`、2025-03-10）で、元のコミット履歴をそのまま含んでいます。
> Launcher3 は The Android Open Source Project による Apache License 2.0 のソフトウェアで、このリポジトリも同じライセンスで配布しています（[LICENSE](LICENSE)）。
> Google や Sony とは関係のない個人プロジェクトです。

## 主な追加機能

追加したコードの大部分は `src/com/android/launcher3/custom/` にあり、AOSP のファイルへの変更は最小限のフックにとどめています。

### ホーム画面
- **ひと目で確認パネル**（ナイアガラランチャー風）: Sony 風の時計（時が大きく分が小さい・テーマカラー）、日付、天気、次の予定
  - 時計は他アプリの時計ウィジェットに差し替え可能
  - 長押しメニューから位置を行単位で上下に調整、非表示も可能
  - 天気は [Open-Meteo](https://open-meteo.com/)（おおよその位置情報を約 1km 単位に丸めて送信）
- **今後 1 週間パネル**: パネルをタップすると、7 日分の天気と予定を下から出るシートで表示。日ごとに予定追加ボタン、下スワイプで閉じる
- **検索バー**: 長押しで削除・設定から再表示
- **Google Discover**: 最初のページで右にスワイプ（Google アプリのランチャー用オーバーレイに接続）
- **下スワイプで通知パネル**
- ウィジェットを 1×1 から全画面まで自由にリサイズ
- 壁紙の変更は「壁紙とスタイル」アプリを直接開く

### アプリ一覧
- テーマカラーで色付けした半透明の背景 + 壁紙のぼかし（不透明度は設定で調整）
- アプリを開いて戻るとホームに戻る

### 全体
- フォント「無心」（MODI工場）を全体に適用
- アイコンサイズの調整（70〜130%）
- 設定画面をカテゴリー分け

## ビルド

AOSP のビルドシステム（Soong）用の `Android.bp` に加えて、単体でビルドできる Gradle 構成を追加しています。Quickstep（最近のアプリ画面・ジェスチャーナビ）を含まない Launcher3 をビルドします。

必要なもの: JDK 17、Android SDK（API 36）

```sh
# 依存する AOSP のライブラリ（frameworks/libs/systemui など）を external/ に取得
./gradle-build/fetch_aosp_deps.sh

# ビルドとインストール
./gradlew :app:assembleDebug
adb install -r gradle-build/app/build/outputs/apk/debug/app-debug.apk
```

- アプリ ID は `com.android.launcher3.dev` です（一部の端末では既存ランチャーが `com.android.launcher3.*` の権限を持っていて、元の ID ではインストールできないため）。
- aconfig のフラグは `gradle-build/flags` で Java コードに変換しています。値は `gradle-build/flags/flag_overrides.properties` で変更できます（未指定は false）。

## サードパーティ

- **フォント「無心」** — MODI工場／倒神神倒（<https://modi.jpn.org/font_mushin.php>）。MODI工場独自ライセンス（商用利用・改変・再配布・埋め込み可、表記不要、フォント単体での販売は不可）。詳細は [third_party/mushin](third_party/mushin) を参照。
- **天気データ** — [Open-Meteo](https://open-meteo.com/)
- **AOSP の依存ライブラリ**（ビルド時に取得）— Apache License 2.0
