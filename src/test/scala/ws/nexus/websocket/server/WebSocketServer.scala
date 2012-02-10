package ws.nexus.websocket.server

import java.util.concurrent.Executors


import java.security.KeyStore
import java.net._

import javax.net.ssl._

import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.codec.http._

import org.jboss.netty.handler.codec.http.HttpMethod._

import websocketx._
import collection.mutable.{SynchronizedSet, HashSet}


object WebSocketServer {
  val CLOSE_STRING_MESSAGE = "CLOSE"
  
  def bootstrap() = new ServerBootstrap(
    new NioServerSocketChannelFactory(
      Executors.newCachedThreadPool(),
      Executors.newCachedThreadPool()))

  var plainServer:ServerBootstrap = _
  var secureServer:ServerBootstrap = _

  def start(){
    plainServer = bootstrap()
    plainServer.setPipelineFactory( new WebSocketServerPipelineFactory( new WebSocketServerHandler( false ) ) )
    plainServer.bind(new InetSocketAddress( "0.0.0.0", 8080 ))

    secureServer = bootstrap()
    secureServer.setPipelineFactory( new WebSocketServerPipelineFactory( new WebSocketServerHandler( true ) ) )
    secureServer.bind(new InetSocketAddress( "0.0.0.0", 8443 ))
  }

  def stop(){
    plainServer.releaseExternalResources()
    secureServer.releaseExternalResources()
    plainServer = null
    secureServer = null
  }


}

object SSLContextBuilder {
  lazy val context = {
    val keypass = "changeme".toCharArray

    val keyStore = KeyStore.getInstance("JKS")

    keyStore.load( getClass.getResourceAsStream( "/keystore.jks" ), keypass)

    val trustFactory = TrustManagerFactory.getInstance("SunX509")
    trustFactory.init(keyStore)
    val trustManagers = trustFactory.getTrustManagers

    val keyFactory = KeyManagerFactory.getInstance("SunX509")
    keyFactory.init(keyStore, keypass)
    val keyManagers = keyFactory.getKeyManagers

    val tlsContext = SSLContext.getInstance("TLS")
    tlsContext.init(keyManagers, trustManagers, null)
    tlsContext
  }

}


class WebSocketServerPipelineFactory( handler:WebSocketServerHandler ) extends ChannelPipelineFactory {
  def getPipeline() = {

    val pipeline = Channels.pipeline()

    if( handler.secure ){
      val engine = SSLContextBuilder.context.createSSLEngine
      engine.setUseClientMode(false);
      pipeline.addLast("ssl", new SslHandler(engine))
    }

    pipeline.addLast("decoder", new HttpRequestDecoder());
    pipeline.addLast("aggregator", new HttpChunkAggregator(1024 * 4))
    pipeline.addLast("encoder", new HttpResponseEncoder())

    pipeline.addLast("handler", handler )
    pipeline
  }
}



@ChannelHandler.Sharable
class WebSocketServerHandler( val secure:Boolean ) extends SimpleChannelUpstreamHandler {
  var handshaker:WebSocketServerHandshaker = _


  private def getWebSocketLocation(req: HttpRequest) = {
    val protocol = if( secure ) "wss" else "ws"
    protocol + "://"+ req.getHeader(HttpHeaders.Names.HOST) + req.getUri
  }


  override def channelDisconnected( ctx:ChannelHandlerContext , e:ChannelStateEvent ){
    ctx.getChannel.close()
  }

  override def messageReceived( ctx:ChannelHandlerContext , e:MessageEvent ){
    e.getMessage match {
      case frame:WebSocketFrame => handleWebSocketFrame( ctx, frame )
      case msg:HttpRequest => handleHttpRequest( ctx, msg )
    }

  }
  private def handleWebSocketFrame( ctx:ChannelHandlerContext, frame:WebSocketFrame ) {
    frame match {
      case frame:CloseWebSocketFrame =>
        handshaker.close(ctx.getChannel(), frame )

      case frame:PingWebSocketFrame =>
        ctx.getChannel().write( new PongWebSocketFrame( frame.getBinaryData() ) )

      case frame:TextWebSocketFrame =>
        frame.getText() match {
          case WebSocketServer.CLOSE_STRING_MESSAGE => ctx.getChannel().close()
          case text => ctx.getChannel().write( new TextWebSocketFrame( text.toLowerCase() ) )
        }


      case _ =>
        throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()))
    }

  }


  private def handleHttpRequest(ctx: ChannelHandlerContext, req: HttpRequest) {

    if( req.getMethod() == GET  ) {
      // Handshake
      val wsFactory = new WebSocketServerHandshakerFactory( getWebSocketLocation(req), null, false )
      handshaker = wsFactory.newHandshaker( req )

      if( handshaker == null )
        wsFactory.sendUnsupportedWebSocketVersionResponse( ctx.getChannel )
      else {
        handshaker.handshake( ctx.getChannel, req )
      }
    }
  }



  override def exceptionCaught( ctx: ChannelHandlerContext, e: ExceptionEvent) {
    e.getChannel.close();
  }

}



