package ws.nexus.websocket.client

import javax.net.ssl.{SSLSocketFactory, X509TrustManager, SSLContext}
import java.security.cert.X509Certificate
import java.io._
import java.net.{URI, SocketTimeoutException, Socket}
import collection.mutable.{SynchronizedQueue}
import java.util.concurrent.atomic.AtomicBoolean


// ------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------------------------------------------------------------
abstract class  WebSocketEventHandler {
  def onOpen( client:Client ){}

  def onMessage( client:Client, message:String){}
  def onMessage(  client:Client, message:Array[Byte]){}

  def onError(  client:Client, e:Throwable ){}

  def onClose(  client:Client ){}
  def onStop(  client:Client  ){}

}


// ------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------------------------------------------------------------
object Client {


  object ConnectionOption {
    val NORMAL_SSL_SOCKET_FACTORY =  () => { SSLSocketFactory.getDefault() }

    val TRUST_ALL_SSL_SOCKET_FACTORY = () => {
      val context = SSLContext.getInstance( "TLS" );
      context.init( null, Array( new X509TrustManager {
        def checkClientTrusted( certs: Array[X509Certificate], s: String) {}

        def checkServerTrusted( certs: Array[X509Certificate], s: String) {}

        def getAcceptedIssuers: Array[X509Certificate] = Array()
      }), null );

      context.getSocketFactory()
    }

    // soTimeout 30000 => workaround for android issu => close on socket doesnt throw ex.
    val DEFAULT = ConnectionOption( true, 30000, TRUST_ALL_SSL_SOCKET_FACTORY )
  }
  case class ConnectionOption( tcpNoDelay:Boolean, soTimeout:Int, sslSocketFactory: () => SSLSocketFactory)

}

// ------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------------------------------------------------------------

class Client( eventHandler:WebSocketEventHandler ) {

  private val sendQueue = new SynchronizedQueue[String]

  var clientThread:ClientThread = _

  var url:URI = _
  var connectionOption:Client.ConnectionOption = Client.ConnectionOption.DEFAULT
  // --------------------------------------------------------
  def connect( url:URI, connectionOption:Client.ConnectionOption = Client.ConnectionOption.DEFAULT, throwableLogger: Option[Throwable => Any] = None  ){
    this.url = url
    this.connectionOption = connectionOption
    clientThread = new ClientThread( url, eventHandler, connectionOption, throwableLogger )
    clientThread.start()
  }
  // --------------------------------------------------------
  def reconnect(){
    if( url != null ) connect( url, connectionOption )
  }

  // --------------------------------------------------------
  def sendQueueSize = sendQueue.size

  // --------------------------------------------------------
  def send( message:String  ){
    sendQueue.enqueue( message )
    clientThread.sendingThread.synchronized( clientThread.sendingThread.notify() )
  }

  // --------------------------------------------------------
  def close(){
    clientThread.close()
  }


  // --------------------------------------------------------------------------
  // --------------------------------------------------------------------------
  // --------------------------------------------------------------------------
  class ClientThread( url:URI, eventHandler:WebSocketEventHandler,
                      connectionOption:Client.ConnectionOption,
                      throwableLogger: Option[Throwable => Any] ) extends Thread {

    private var socket:Socket = _
    private var input:InputStream  = _
    val running = new AtomicBoolean( true )


    // --------------------------------------------------------
   object URIExtractor{
      def isSecure( url:URI ) =
        url.getScheme match {
          case "ws" => false
          case "wss" => true
        }

      def port( url:URI ) = {
        url.getPort match {
          case -1 => if( isSecure( url ) ) 443 else 80
          case p => p
        }
      }
      def unapply( url:URI ) = {
        Some( ( url.getScheme, url.getHost, port( url ), url.getPath) )
      }
    }

    // --------------------------------------------------------
    @inline private def catcher[T]( block: => T ):Option[T] = {
      try{
        Some( block )
      }
      catch{
        case t =>
          throwableLogger.foreach( _( t ) )
          None
      }
    }
    
    
    // --------------------------------------------------------
    private def connect() {
      val URIExtractor( protocol, host, port, path ) = url

      if( protocol == "ws" )
        socket = new Socket( host, port )
      else
        socket = connectionOption.sslSocketFactory().createSocket( host, port )


      socket.setKeepAlive( true )
      socket.setTcpNoDelay( connectionOption.tcpNoDelay )
      socket.setSoTimeout( connectionOption.soTimeout )

      input = socket.getInputStream()

      val handshake = "GET " + path + " HTTP/1.1\r\n" +
        "Upgrade: WebSocket\r\n" +
        "Connection: Upgrade\r\n" +
        "Host: " + host + "\r\n" +
        "Origin: http://" + host +
        "\r\n" +
        "\r\n"

      val os = socket.getOutputStream()
      os.write( handshake.getBytes() )
      os.flush()

      val reader = new BufferedReader( new InputStreamReader(input) )
      val line = reader.readLine()

      if ( !line.equals("HTTP/1.1 101 Web Socket Protocol Handshake") ){
        throw new IOException("unable to connect to server")
      }
    }

    // --------------------------------------------------------
    def socketSend( message:String ){
      val os = socket.getOutputStream()
      os.write( 0x00 )
      os.write( message.getBytes() )
      os.write( 0xFF )
      os.flush()
    }
    def close() {
      running.set(false)
      catcher {
        sendingThread.synchronized( sendingThread.notify() )
        while( sendingThread.getState != Thread.State.TERMINATED ) Thread.sleep(10)
        if( socket != null ) socket.close()
        if( input != null ) input.close()
        clientThread.interrupt()
      }
      socket = null
      input = null
    }

    // --------------------------------------------------------
    lazy val sendingThread = new Thread {
      override def run(){
        while( running.get ){
          catcher{  
            if( sendQueue.isEmpty ) synchronized( wait )
            while( !sendQueue.isEmpty && running.get ){
              socketSend( sendQueue.head )
              sendQueue.dequeue()
            }
          } // catcher
        } // while
      } // run
    }


    // --------------------------------------------------------
    override def run(){
      try {
        connect()
        eventHandler.onOpen( Client.this )

        sendingThread.start()

        while( running.get ){
          try {
            val b = input.read()

            if( b == -1 ) {
              running.set( false )
              eventHandler.onClose( Client.this )
            }
            else {
              if (b == 0x00) {
                val text = decodeTextFrame()
                try {
                  eventHandler.onMessage( Client.this, text )
                }
                catch {
                  case e => eventHandler.onError( Client.this, e )
                }
              }
              else if( b == 0x80 ){
                val bin = decodeBinaryFrame()
                try {
                  eventHandler.onMessage( Client.this, bin )
                }
                catch {
                  case e => eventHandler.onError( Client.this, e )
                }
              }
              else {
                throw new IOException( "Unexpected byte: " + Integer.toHexString(b) );
              }
            }

          }
          catch{
            // workaround for android issu => close on socket doesnt throw ex.
            case e:SocketTimeoutException =>

            // interrupt to send
            case e:InterruptedException =>

          }
        } // while

      }
      catch {
        case e =>
          catcher( eventHandler.onError( Client.this, e ) )
      }

      catcher( eventHandler.onStop( Client.this ) )


      running.set(false)
    }

    // --------------------------------------------------------
    def decodeBinaryFrame() = {
      var frameSize = 0L
      var lengthFieldSize = 0
      var b:Byte = 0
      do {
        b = input.read().toByte
        frameSize <<= 7
        frameSize |= b & 0x7f
        lengthFieldSize += 1
        if (lengthFieldSize > 8) {
          throw new IOException( "Unexpected lengthFieldSize");
        }
      }
      while( (b & 0x80) == 0x80 )

      val buffer = new Array[Byte](frameSize.toInt)
      input.read(buffer)
      buffer
    }



    // --------------------------------------------------------
    val boas = new ByteArrayOutputStream()
    def decodeTextFrame() = {
      boas.reset()
      var b = 0
      while( b != 0xFF ){
        b = input.read()
        if( b != 0xFF ) boas.write( b.toByte )
      }

      boas.toString()
    }
  }

}
