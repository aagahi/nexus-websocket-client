package ws.nexus.websocket.test

import org.specs2.Specification
import ws.nexus.websocket.server.WebSocketServer
import org.specs2.specification.{Fragments, Step}
import ws.nexus.websocket.client.{WebSocketEventHandler, Client}
import java.net.{URI, URL}
import collection.mutable.Queue


class TestWebSocketClient extends Specification  {

  override def map(fs: =>Fragments) = Step( startService ) ^ is ^ Step( stopService )

  def is =
    "Websocket client should"                                 ^
      "connect to websocket server using plain connection"    ! plainConnect ^
      "connect to websocket server using secure connection"   ! secureConnect ^
      "enqueue message when connection not established and push them when connected"  ! enqueueMessageWhenNotConnected ^
      "be close notified if socket is disconnected"           ! connectionClose


  def startService {
    WebSocketServer.start()
  }
  def stopService {
    WebSocketServer.stop()
  }

  

  class TestWebSocketEventHandler extends WebSocketEventHandler {

    val messageQueue = new Queue[String]

    override def onMessage( client:Client, text:String ){
      synchronized{
        messageQueue.enqueue( text )
        notify()
      }
    }
    
    def waitLastMessage() = synchronized{
      if( messageQueue.isEmpty ) wait( 5000 )
      messageQueue.dequeue()
    }
  }
  
  val str = "HELLO"

  // ------------------------------------------------
  // ------------------------------------------------
  // ------------------------------------------------
  def plainConnect = {

    val eventHandler = new TestWebSocketEventHandler{
      override def onOpen( client:Client ) { client.send( str )  }
    }
    
    val client = new Client( new URI("ws://localhost:8080/path"), Client.ConnectionOption.DEFAULT, eventHandler )

    val lastMessage = eventHandler.waitLastMessage()
    client.close()

     lastMessage === str.toLowerCase
  }

  // ------------------------------------------------
  // ------------------------------------------------
  // ------------------------------------------------
  def secureConnect = {
    val eventHandler = new TestWebSocketEventHandler{
      override def onOpen( client:Client ) { client.send( str )  }
    }
    val client = new Client( new URI("wss://localhost:8443/path"), Client.ConnectionOption.DEFAULT, eventHandler )

    val lastMessage = eventHandler.waitLastMessage()
    client.close()

    lastMessage === str.toLowerCase
  }

  // ------------------------------------------------
  // ------------------------------------------------
  // ------------------------------------------------
  def enqueueMessageWhenNotConnected = {
    val str1 = "HELLO1"
    val str2 = "HELLO2"
    val eventHandler = new TestWebSocketEventHandler
    val client = new Client( new URI("wss://localhost:8443/path"), Client.ConnectionOption.DEFAULT, eventHandler )
    // ok we assume that network connection will occure after enqueueing message... :/
    client.send( str1 )
    client.send( str2 )


    val lastMessage1 = eventHandler.waitLastMessage()
    val lastMessage2 = eventHandler.waitLastMessage()

    client.send( str )
    val lastMessage = eventHandler.waitLastMessage()

    client.close()

    eventHandler.messageQueue.isEmpty must beTrue
    client.sendQueueSize === 0
    lastMessage1 === str1.toLowerCase
    lastMessage2 === str2.toLowerCase
    lastMessage === str.toLowerCase
  }

  // ------------------------------------------------
  // ------------------------------------------------
  // ------------------------------------------------
  def connectionClose = {
    val eventHandler = new TestWebSocketEventHandler{
      var isClosed = false
      override def onOpen( client:Client ) { client.send( WebSocketServer.CLOSE_STRING_MESSAGE )  }

      override def onClose( client:Client ) = synchronized{ isClosed = true; notify() }

      def waitIsClose() = synchronized{
        if( !isClosed ) wait( 5000 )
        isClosed
      }
    }

    val client = new Client( new URI("ws://localhost:8080/path"), Client.ConnectionOption.DEFAULT, eventHandler )


    val isClosed = eventHandler.waitIsClose()

    client.close()
    isClosed must beTrue

  }
}
