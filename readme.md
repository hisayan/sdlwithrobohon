# SDL with RoBoHoN

![SDL with RoBoHoN](./app/src/main/res/mipmap-xxxhdpi/ic_launcher.png "ドライブモード")

## このアプリについて

[SDL (Smart Device Link)](https://smartdevicelink.com/) をつかったデモアプリです。

SDL とは、スマートフォンとクルマをつなげ、車内でアプリを利用するためのオープンソースプロジェクトです。

## デモ動画

[![デモ動画](https://img.youtube.com/vi/NiK-ihdnvIs/0.jpg)](https://www.youtube.com/watch?v=NiK-ihdnvIs)


### シナリオ

* パーキングブレーキがOFFになると「出発進行！」
* パーキングブレーキがOFFになったけど、シートベルトしてなかったら「シートベルトつけて」
* 時速が 105km/h こえたら、「キンコーン、キンコーン」と口で言う
* ガソリンが減ってきたら「ガソリンスタンドに行かない？」と提案
* ギアがバックにはいると「バックします。ご注意ください」
* 77777km になったら「なにかいいことあるかなぁ」
* ワイパーが動いたら「♪雨、雨、ふれふれかぁさんが」と歌い出す

## 実行に際して

1. コンパイルには、RoBoHoN SDK が必要です
2. 実行には、RoBoHoN が必要です
3. SdlService.java 内の APP_ID を、任意のものに設定して下さい

## SDLシミュレーター

### Manticore

SDLのシミュレーター環境が準備されています。

下記URLから、起動します。

https://smartdevicelink.com/resources/manticore/

その後、アプリを起動し、Manticore との接続情報を設定してください。


### Docker

Manticore は、共用環境です。

時々、たくさんのひとが利用しているのか、待たされて使えないケースがあります。

ローカルで、ガンガン開発＆テストするために、SDL Core と SDL HMI が動く、Docker Image も用意しました。

```
$ docker run -d -p 12345:12345 -p 8080:8080 -p 8087:8087 -p 3001:3001 --name sdl5 hisayan/sdl_core_docker:5.0.0
```

アプリを起動し、この Docker との接続情報を設定してください。


## TODO

+ SDLService を上手に終了できないため、設定メニューの再起動ボタンが機能しない。SDLについて要調査。
+ アプリ起動直後、SDL への接続を試すため、設定アイコンをタップしても、反応しないケースがある。その場合は、しばらくまってから、設定アイコンタップを試すこと。こちら接続リトライ状況について、わかりやすくガイダンス表示すること。
+ Manticore などの SDLシミュレーターと連携しての動作を想定しているため、SDL との接続は、TCP/IP接続のみを実装。実機で動かす場合は、USB接続あるいは Bluetooth接続の対応が必要。
