package ws.nexus.websocket.test

import org.specs2.Specification
import ws.nexus.websocket.server.WebSocketServer
import org.specs2.specification.{Fragments, Step}
import ws.nexus.websocket.client.{WebSocketEventHandler, Client}
import java.net.{URI, URL}


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

  
  case class StringHolder( var s:Option[String] = None )

  def eventHandler( holder:StringHolder, str:String ):WebSocketEventHandler = new WebSocketEventHandler {
    override def onOpen( client:Client ) {
      client.send( str )
    }
    override def onMessage( client:Client, text:String ){
      holder.synchronized{
        holder.s = Some( text )
        holder.notify()
      }
    }
  }

  val str = "HELLO"

  def plainConnect = {
    val holder = StringHolder()

    val client = new Client( new URI("ws://localhost:8080/path"), Client.ConnectionOption.DEFAULT, eventHandler( holder, str ) )

    holder.synchronized{
      if( holder.s.isEmpty ) holder.wait()
    }
    client.close()

    holder.s.get === str.toLowerCase
  }

  def secureConnect = {
    val holder = StringHolder()

    val client = new Client( new URI("wss://localhost:8443/path"), Client.ConnectionOption.DEFAULT, eventHandler( holder, str ) )

    holder.synchronized{
      if( holder.s.isEmpty ) holder.wait()
    }
    client.close()

    holder.s.get === str.toLowerCase
  }

}
