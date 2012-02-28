# About

This is a simple lightweight webSocket client (mostly for scala android usage) based on early websocket specs but it still works with latest netty server implem (as see related tests).

This code is based on java classical io lib, as I had problem using nio with TLS support with Android
and also faced some trouble using netty client implem with android (API level7 if this is related somehow).

Right now nexus-websocket-client is succesfully used in [SpotMint Android Project](https://github.com/aagahi/spotmint-android).
