package ws.nexus.websocket.test

import org.specs2.Specification
import ws.nexus.websocket.server.WebSocketServer
import org.specs2.specification.{Fragments, Step}
import ws.nexus.websocket.client.{WebSocketEventHandler, Client}
import java.net.{URI, URL}
import collection.mutable.Queue
import java.util.concurrent.atomic.AtomicBoolean


class TestWebSocketClient extends Specification  {

  override def map(fs: =>Fragments) = Step( startService ) ^ is ^ Step( stopService )

  def is =
    "Websocket client should"                                 ^
      "connect to websocket server using plain connection"    ! plainConnect ^
      "connect to websocket server using secure connection"   ! secureConnect ^
      "enqueue message when connection not established and push them when connected"      ! enqueueMessageWhenNotConnected ^
      "manage large amount of message sent at same time"      ! supportLargeAmountOfMessageAtSameTime ^
      "be close notified if socket is disconnected"           ! connectionClose ^
      "auto reconnect if socket is remote disconnected"       ! autoReconnectAfterServerClose


  def startService {
    WebSocketServer.start()
  }
  def stopService {
    WebSocketServer.stop()
  }

  

  class TestWebSocketEventHandler extends WebSocketEventHandler {

    val messageQueue = new Queue[String]

    override def onMessage( client:Client, text:String ){
      messageQueue.synchronized{
        messageQueue.enqueue( text )
        messageQueue.notify()
      }
    }


    def waitLastMessage() = messageQueue.synchronized{
      if( messageQueue.isEmpty ) messageQueue.wait( 5000 )
      try{
        messageQueue.dequeue()
      }
      catch {
        case e => e.printStackTrace(); throw e
      }
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
    
    val client = new Client( eventHandler )
    client.connect( new URI("ws://localhost:8080/path"), Client.ConnectionOption.DEFAULT )

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
    val client = new Client( eventHandler )
    client.connect( new URI("wss://localhost:8443/path"), Client.ConnectionOption.DEFAULT )

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
    val client = new Client( eventHandler )
    client.connect( new URI("wss://localhost:8443/path"), Client.ConnectionOption.DEFAULT )
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
  def supportLargeAmountOfMessageAtSameTime = {
    val eventHandler = new TestWebSocketEventHandler
    val client = new Client( eventHandler )
    client.connect( new URI("wss://localhost:8443/path"), Client.ConnectionOption.DEFAULT )
    // ok we assume that network connection will occure after enqueueing message... :/

    val messages = 1 to 500 map{ "HELLO-"+_ }
    
    val (messages1, messages2 ) = messages.splitAt( 250 )
    messages1.foreach( client.send( _ ) )
    messages2.foreach( client.send( _ ) )


    val receiveMessages = messages.map( m => eventHandler.waitLastMessage() )
    client.close()

    eventHandler.messageQueue.isEmpty must beTrue
    client.sendQueueSize === 0

    receiveMessages.forall( m => messages.contains( m.toUpperCase ) ) must beTrue
    messages.forall( m => receiveMessages.contains( m.toLowerCase ) ) must beTrue

  }

  // ------------------------------------------------
  // ------------------------------------------------
  // ------------------------------------------------
  def connectionClose = {
    val eventHandler = new TestWebSocketEventHandler{
      var isClosed = false
      override def onOpen( client:Client ) { client.send( WebSocketServer.CLOSE_STRING_MESSAGE )  }
      override def onClose(client: Client) {
        synchronized{ isClosed = true; notify() }
      }

      def waitIsClose() = synchronized{
        if( !isClosed ) wait( 5000 )
        isClosed
      }
    }

    val client = new Client( eventHandler )
    client.connect( new URI("ws://localhost:8080/path"), Client.ConnectionOption.DEFAULT )

    val isClosed = eventHandler.waitIsClose()

    client.close()
    isClosed must beTrue

  }


  // ------------------------------------------------
  // ------------------------------------------------
  // ------------------------------------------------
  def autoReconnectAfterServerClose = {
    val str1 = "HELLO1"
    val str2 = "HELLO2"
    val eventHandler = new TestWebSocketEventHandler{
      var isClosed = false
      val LOCK = new Object

      override def onClose( client:Client ) = {
        if( !isClosed ) client.reconnect()
        LOCK.synchronized{ isClosed = true; LOCK.notify() }
      }

      def waitIsClose() = LOCK.synchronized{
        if( !isClosed ) LOCK.wait( 5000 )
        isClosed
      }

    }
    val client = new Client( eventHandler )
    client.connect( new URI("wss://localhost:8443/path"), Client.ConnectionOption.DEFAULT )

    client.send( str1 )
    client.send( WebSocketServer.CLOSE_STRING_MESSAGE )

    eventHandler.waitIsClose()

    client.send( str2 )



    val lastMessage1 = eventHandler.waitLastMessage()
    val lastMessage2 = eventHandler.waitLastMessage()

    client.close()

    eventHandler.messageQueue.isEmpty must beTrue
    client.sendQueueSize === 0
    lastMessage1 === str1.toLowerCase
    lastMessage2 === str2.toLowerCase

  }

}
