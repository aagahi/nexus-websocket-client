package ws.nexus.websocket.test

import org.specs2.Specification
import ws.nexus.websocket.server.WebSocketServer
import org.specs2.specification.{Fragments, Step}


class TestWebSocketClient extends Specification  {

  override def map(fs: =>Fragments) = Step( startService ) ^ is ^ Step( stopService )

  def is =
    "json ws message serializer should"                 ^
      "de/serialize profile data"                       ! deAndSerializeProfile ^
      "deserialize profile data string"                 ! deserializeProfile ^
      "de/serialize Coordinate data"                        ! deAndSerializeLatLng



  def startService {
    WebSocketServer.start()
  }
  def stopService {
    WebSocketServer.stop()
  }

  def deAndSerializeProfile= {
    success
  }

  def deserializeProfile = {
    success
  }

  def deAndSerializeLatLng = {
    success
  }

}
