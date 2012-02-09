package ws.nexus.websocket.server

import java.util.concurrent.Executors


import java.security.KeyStore
import java.io._
import java.net._

import javax.net.ssl._

import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.codec.http._

import org.jboss.netty.handler.codec.http.HttpMethod._

import websocketx._


object WebSocketServer {
  def bootstrap() = new ServerBootstrap(
    new NioServerSocketChannelFactory(
      Executors.newCachedThreadPool(),
      Executors.newCachedThreadPool()))

  var plainServer:ServerBootstrap = _
  var secureServer:ServerBootstrap = _

  def start(){
    plainServer = bootstrap()
    plainServer.setPipelineFactory( new PlainWebSocketServerPipelineFactory() )
    plainServer.bind(new InetSocketAddress( "0.0.0.0", 8080 ))

    secureServer = bootstrap()
    secureServer.setPipelineFactory( new SecureWebSocketServerPipelineFactory() )
    secureServer.bind(new InetSocketAddress( "0.0.0.0", 8443 ))
  }

  def stop(){
    plainServer.releaseExternalResources()
    plainServer = null

    secureServer.releaseExternalResources()
    secureServer = null
  }

}

object SSLContextBuilder {
  lazy val context = {
    val keypass = "changeme".toCharArray

    val keyStore = KeyStore.getInstance("JKS")
    val ksFile = new File("./keystore/keystore.jks")
    keyStore.load(new FileInputStream(ksFile), keypass);

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


class PlainWebSocketServerPipelineFactory extends ChannelPipelineFactory {
  def getPipeline() = {

    val pipeline = Channels.pipeline()

    pipeline.addLast("decoder", new HttpRequestDecoder());
    pipeline.addLast("aggregator", new HttpChunkAggregator(1024 * 4))
    pipeline.addLast("encoder", new HttpResponseEncoder())

    // and then business logic.
    pipeline.addLast("handler",  new WebSocketServerHandler() )
    pipeline
  }
}

class SecureWebSocketServerPipelineFactory extends ChannelPipelineFactory  {
  def getPipeline() = {

    val pipeline = Channels.pipeline()

    val engine = SSLContextBuilder.context.createSSLEngine
    engine.setUseClientMode(false);

    pipeline.addLast("ssl", new SslHandler(engine))

    pipeline.addLast("decoder", new HttpRequestDecoder());
    pipeline.addLast("aggregator", new HttpChunkAggregator(1024 * 4))
    pipeline.addLast("encoder", new HttpResponseEncoder())

    // and then business logic.
    pipeline.addLast("handler",  new WebSocketServerHandler() )
    pipeline
  }
}

@ChannelHandler.Sharable
class WebSocketServerHandler extends SimpleChannelUpstreamHandler {
  var handshaker:WebSocketServerHandshaker = _

  private def getWebSocketLocation(req: HttpRequest) = "wss://" + req.getHeader(HttpHeaders.Names.HOST) + req.getUri
  //private def getWebSocketLocation(req: HttpRequest) = "ws://" + req.getHeader(HttpHeaders.Names.HOST) + req.getUri

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
        // TODO:Implemt text websocket

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



