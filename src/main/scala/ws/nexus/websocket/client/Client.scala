package ws.nexus.websocket.client

import javax.net.ssl.{SSLSocketFactory, X509TrustManager, SSLContext}
import java.security.cert.X509Certificate
import java.io._
import java.net.{URI, SocketTimeoutException, Socket}

abstract class  WebSocketEventHandler {
  def onOpen( client:Client ){}

  def onMessage( client:Client, message:String){}
  def onMessage(  client:Client, message:Array[Byte]){}

  def onError(  client:Client, e:Exception ){}

  def onClose(  client:Client ){}
  def onStop(  client:Client  ){}

}



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

    // soTimeout 500 => workaround for android issu => close on socket doesnt throw ex.
    val DEFAULT = ConnectionOption( true, 5000, TRUST_ALL_SSL_SOCKET_FACTORY )
  }
  case class ConnectionOption( tcpNoDelay:Boolean, soTimeout:Int, sslSocketFactory: () => SSLSocketFactory)

}
class Client( url:URI, connectionOption:Client.ConnectionOption = Client.ConnectionOption.DEFAULT, eventHandler:WebSocketEventHandler ) extends Thread {

  var socket:Socket = _
  var input:InputStream  = _
  var running = false


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

  def isClosed() =
    if( socket != null ) socket.isClosed()
    else true


  def canSendMessage() =
    !isClosed() && socket != null && !socket.isOutputShutdown()


  def close() {
    running = false
    socket.close()
    socket = null

  }
  def send( message:String  ){
    if( canSendMessage() ){
      val os = socket.getOutputStream()
      os.write( 0x00 )
      os.write( message.getBytes() )
      os.write( 0xFF )
      os.flush()
    }
  }


  private def connect() {
    val URIExtractor( protocol, host, port, path ) = url

    if( protocol == "ws" )
      socket = new Socket( host, port )
    else
      socket = connectionOption.sslSocketFactory().createSocket( host, port )


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



  override def run(){
    running = true
    try {
      connect()

      eventHandler.onOpen( this )

      while( running ){
        try{
          val b = input.read()
          if( running ){
            if( b == -1 ) {
              eventHandler.onClose( this )
              running = false
            }
            else {
              if (b == 0x00) {
                val text = decodeTextFrame()
                try {
                  eventHandler.onMessage( this, text )
                } 
                catch {
                  case e:Exception => eventHandler.onError( this, e )
                }
              }
              else if( b == 0x80 ){
                try {
                  eventHandler.onMessage( this, decodeBinaryFrame() )
                }
                catch {
                  case e:Exception => eventHandler.onError( this, e )
                }
              }
              else {
                throw new IOException( "Unexpected byte: " + Integer.toHexString(b) );
              }
            }
          }
        }
        // workaround for android issu => close on socket doesnt throw ex.
        catch{
          case e:SocketTimeoutException =>
        }
      } // while

    }
    catch {
      case e:Exception =>
        eventHandler.onError( this, e )
    }

    eventHandler.onStop( this )


  }


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



  start()

}
