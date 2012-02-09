package ws.nexus.websocket.test

import org.specs2.Specification
import ws.nexus.websocket.server.WebSocketServer
import org.specs2.specification.{Fragments, Step}


class TestWebSocketClient extends Specification  {

  override def map(fs: =>Fragments) = Step( startService ) ^ is ^ Step( stopService )

  def is =
    "Websocket client should"                                 ^
      "connect to websocket server using plain connection"    ! plainConnect ^
      "connect to websocket server using secure connection"   ! secureConnect


  def startService {
    WebSocketServer.start()
  }
  def stopService {
    WebSocketServer.stop()
  }

  def plainConnect= {
    success
  }

  def secureConnect = {
    success
  }

}
