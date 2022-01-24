package org.thp.thehive.migration.th3

import akka.NotUsed
import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.sslconfig.ssl.{KeyManagerConfig, KeyStoreConfig, SSLConfigSettings, TrustManagerConfig, TrustStoreConfig}
import org.thp.client.{Authentication, NoAuthentication, PasswordAuthentication}
import org.thp.scalligraph.utils.Retry
import org.thp.scalligraph.{InternalError, NotFoundError}
import play.api.http.HeaderNames
import play.api.libs.json.{JsNumber, JsObject, JsValue, Json}
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfig}
import play.api.libs.ws.{WSClient, WSClientConfig, WSResponse}
import play.api.{Configuration, Logger}

import java.net.{URI, URLEncoder}
import scala.concurrent.duration.{Duration, DurationInt, DurationLong, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

object ElasticClientProvider {
  def apply(config: Configuration, actorSystem: ActorSystem): ElasticClient = {
    implicit val as: ActorSystem = actorSystem
    lazy val logger              = Logger(getClass)
    val ws: WSClient = {
      val trustManager = config.getOptional[String]("search.trustStore.path").map { trustStore =>
        val trustStoreConfig = TrustStoreConfig(None, Some(trustStore))
        config.getOptional[String]("search.trustStore.type").foreach(trustStoreConfig.withStoreType)
        trustStoreConfig.withPassword(config.getOptional[String]("search.trustStore.password"))
        val trustManager = TrustManagerConfig()
        trustManager.withTrustStoreConfigs(List(trustStoreConfig))
        trustManager
      }
      val keyManager = config.getOptional[String]("search.keyStore.path").map { keyStore =>
        val keyStoreConfig = KeyStoreConfig(None, Some(keyStore))
        config.getOptional[String]("search.keyStore.type").foreach(keyStoreConfig.withStoreType)
        keyStoreConfig.withPassword(config.getOptional[String]("search.keyStore.password"))
        val keyManager = KeyManagerConfig()
        keyManager.withKeyStoreConfigs(List(keyStoreConfig))
        keyManager
      }
      val sslConfig = SSLConfigSettings()
      trustManager.foreach(sslConfig.withTrustManagerConfig)
      keyManager.foreach(sslConfig.withKeyManagerConfig)

      val wsConfig = AhcWSClientConfig(
        wsClientConfig = WSClientConfig(
          connectionTimeout = config.getOptional[Int]("search.connectTimeout").fold(2.minutes)(_.millis),
          idleTimeout = config.getOptional[Int]("search.socketTimeout").fold(2.minutes)(_.millis),
          requestTimeout = config.getOptional[Int]("search.connectionRequestTimeout").fold(2.minutes)(_.millis),
          followRedirects = config.getOptional[Boolean]("search.redirectsEnabled").getOrElse(false),
          useProxyProperties = true,
          userAgent = None,
          compressionEnabled = false,
          ssl = sslConfig
        ),
        maxConnectionsPerHost = -1,
        maxConnectionsTotal = -1,
        maxConnectionLifetime = Duration.Inf,
        idleConnectionInPoolTimeout = 1.minute,
        maxNumberOfRedirects = config.getOptional[Int]("search.maxRedirects").getOrElse(5),
        maxRequestRetry = 5,
        disableUrlEncoding = false,
        keepAlive = true,
        useLaxCookieEncoder = false,
        useCookieStore = false
      )
      AhcWSClient(wsConfig)
    }

    val authentication: Authentication =
      (for {
        user     <- config.getOptional[String]("search.user")
        password <- config.getOptional[String]("search.password")
      } yield PasswordAuthentication(user, password))
        .getOrElse(NoAuthentication)

    val esUri        = config.get[String]("search.uri")
    val pageSize     = config.get[Int]("search.pagesize")
    val keepAlive    = config.getMillis("search.keepalive").millis
    val maxAttempts  = config.get[Int]("search.maxAttempts")
    val minBackoff   = config.get[FiniteDuration]("search.minBackoff")
    val maxBackoff   = config.get[FiniteDuration]("search.maxBackoff")
    val randomFactor = config.get[Double]("search.randomFactor")

    val elasticConfig = new ElasticConfig(
      ws,
      authentication,
      esUri,
      pageSize,
      s"${keepAlive.toMillis}ms",
      maxAttempts,
      minBackoff,
      maxBackoff,
      randomFactor,
      actorSystem.scheduler
    )
    val elasticVersion = elasticConfig.version
    logger.info(s"Found ElasticSearch $elasticVersion")
    lazy val indexName: String = {
      val indexVersion  = config.getOptional[Int]("search.indexVersion")
      val indexBaseName = config.get[String]("search.index")
      indexVersion.fold {
        (17 to 10 by -1)
          .view
          .map(v => s"${indexBaseName}_$v")
          .find(elasticConfig.exists)
          .getOrElse(sys.error(s"TheHive 3.x index $indexBaseName not found"))
      } { v =>
        val indexName = s"${indexBaseName}_$v"
        if (elasticConfig.exists(indexName)) indexName
        else sys.error(s"TheHive 3.x index $indexName not found")
      }
    }
    logger.info(s"Found Index $indexName")

    val isSingleType = config.getOptional[Boolean]("search.singleType").getOrElse(elasticConfig.isSingleType(indexName))
    logger.info(s"Found index with ${if (isSingleType) "single type" else "multiple types"}")
    if (isSingleType) new ElasticSingleTypeClient(elasticConfig, indexName)
    else new ElasticMultiTypeClient(elasticConfig, indexName)
  }
}

class ElasticConfig(
    ws: WSClient,
    authentication: Authentication,
    esUri: String,
    val pageSize: Int,
    val keepAlive: String,
    maxAttempts: Int,
    minBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
    randomFactor: Double,
    scheduler: Scheduler
) {
  lazy val logger: Logger           = Logger(getClass)
  def stripUrl(url: String): String = new URI(url).normalize().toASCIIString.replaceAll("/+$", "")

  def post(url: String, body: JsValue, params: (String, String)*)(implicit ec: ExecutionContext): Future[WSResponse] = {
    val encodedParams = params
      .map(p => s"${URLEncoder.encode(p._1, "UTF-8")}=${URLEncoder.encode(p._2, "UTF-8")}")
      .mkString("&")
    logger.debug(s"POST ${stripUrl(s"$esUri/$url?$encodedParams")}\n$body")
    Retry(maxAttempts).withBackoff(minBackoff, maxBackoff, randomFactor)(scheduler, ec) {
      authentication(
        ws.url(stripUrl(s"$esUri/$url?$encodedParams"))
          .withHttpHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
      )
        .post(body)
    }
  }

  def postJson(url: String, body: JsValue, params: (String, String)*)(implicit ec: ExecutionContext): Future[JsValue] =
    post(url, body, params: _*)
      .map {
        case response if response.status == 200 => response.json
        case response                           => throw InternalError(s"Unexpected response from Elasticsearch: ${response.status} ${response.statusText}\n${response.body}")
      }

  def postRaw(url: String, body: JsValue, params: (String, String)*)(implicit ec: ExecutionContext): Future[ByteString] =
    post(url, body, params: _*)
      .map {
        case response if response.status == 200 => response.bodyAsBytes
        case response                           => throw InternalError(s"Unexpected response from Elasticsearch: ${response.status} ${response.statusText}\n${response.body}")
      }

  def delete(url: String, body: JsValue, params: (String, String)*)(implicit ec: ExecutionContext): Future[JsValue] = {
    val encodedParams = params
      .map(p => s"${URLEncoder.encode(p._1, "UTF-8")}=${URLEncoder.encode(p._2, "UTF-8")}")
      .mkString("&")
    authentication(
      ws
        .url(stripUrl(s"$esUri/$url?$encodedParams"))
        .withHttpHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
    )
      .withBody(body)
      .execute("DELETE")
      .map {
        case response if response.status == 200 => response.body[JsValue]
        case response                           => throw InternalError(s"Unexpected response from Elasticsearch: ${response.status} ${response.statusText}\n${response.body}")
      }
  }

  def exists(indexName: String): Boolean =
    Await
      .result(
        authentication(ws.url(stripUrl(s"$esUri/$indexName")))
          .head(),
        10.seconds
      )
      .status == 200

  def isSingleType(indexName: String): Boolean = {
    val response = Await
      .result(
        authentication(ws.url(stripUrl(s"$esUri/$indexName")))
          .get(),
        10.seconds
      )
    if (response.status != 200)
      throw InternalError(s"Unexpected response from Elasticsearch: ${response.status} ${response.statusText}\n${response.body}")
    (response.json \ indexName \ "settings" \ "index" \ "mapping" \ "single_type").asOpt[String].fold(version.head > '6')(_.toBoolean)
  }

  def version: String = {
    val response = Await.result(authentication(ws.url(stripUrl(esUri))).get(), 10.seconds)
    if (response.status == 200) (response.json \ "version" \ "number").as[String]
    else throw InternalError(s"Unexpected response from Elasticsearch: ${response.status} ${response.statusText}\n${response.body}")
  }
}

trait ElasticClient {
  val pageSize: Int
  val keepAlive: String
  def search(docType: String, request: JsObject, params: (String, String)*)(implicit ec: ExecutionContext): Future[JsValue]
  def searchRaw(docType: String, request: JsObject, params: (String, String)*)(implicit ec: ExecutionContext): Future[ByteString]
  def scroll(scrollId: String, keepAlive: String)(implicit ec: ExecutionContext): Future[JsValue]
  def clearScroll(scrollId: String)(implicit ec: ExecutionContext): Future[JsValue]

  def apply(docType: String, query: JsObject)(implicit ec: ExecutionContext): Source[JsValue, NotUsed] = {
    val searchWithScroll = new SearchWithScroll(this, docType, query + ("size" -> JsNumber(pageSize)), keepAlive)
    Source.fromGraph(searchWithScroll)
  }

  def count(docType: String, query: JsObject)(implicit ec: ExecutionContext): Future[Long] =
    search(docType, query + ("size" -> JsNumber(0)))
      .map { j =>
        (j \ "hits" \ "total")
          .asOpt[Long]
          .orElse((j \ "hits" \ "total" \ "value").asOpt[Long])
          .getOrElse(-1)
      }

  def get(docType: String, id: String)(implicit ec: ExecutionContext, mat: Materializer): Future[JsValue] = {
    import ElasticDsl._
    apply(docType, searchQuery(idsQuery(id))).runWith(Sink.headOption).map(_.getOrElse(throw NotFoundError(s"Document $id not found")))
  }
}

class ElasticMultiTypeClient(elasticConfig: ElasticConfig, indexName: String) extends ElasticClient {
  override val pageSize: Int     = elasticConfig.pageSize
  override val keepAlive: String = elasticConfig.keepAlive
  override def search(docType: String, request: JsObject, params: (String, String)*)(implicit ec: ExecutionContext): Future[JsValue] =
    elasticConfig.postJson(s"/$indexName/$docType/_search", request, params: _*)
  override def searchRaw(docType: String, request: JsObject, params: (String, String)*)(implicit ec: ExecutionContext): Future[ByteString] =
    elasticConfig.postRaw(s"/$indexName/$docType/_search", request, params: _*)
  override def scroll(scrollId: String, keepAlive: String)(implicit ec: ExecutionContext): Future[JsValue] =
    elasticConfig.postJson("/_search/scroll", Json.obj("scroll_id" -> scrollId, "scroll" -> keepAlive))
  override def clearScroll(scrollId: String)(implicit ec: ExecutionContext): Future[JsValue] =
    elasticConfig.delete("/_search/scroll", Json.obj("scroll_id" -> scrollId))
}

class ElasticSingleTypeClient(elasticConfig: ElasticConfig, indexName: String) extends ElasticClient {
  override val pageSize: Int     = elasticConfig.pageSize
  override val keepAlive: String = elasticConfig.keepAlive
  override def search(docType: String, request: JsObject, params: (String, String)*)(implicit ec: ExecutionContext): Future[JsValue] = {
    import ElasticDsl._
    val query         = (request \ "query").as[JsObject]
    val queryWithType = request + ("query" -> and(termQuery("relations", docType), query))
    elasticConfig.postJson(s"/$indexName/_search", queryWithType, params: _*)
  }
  override def searchRaw(docType: String, request: JsObject, params: (String, String)*)(implicit ec: ExecutionContext): Future[ByteString] = {
    import ElasticDsl._
    val query         = (request \ "query").as[JsObject]
    val queryWithType = request + ("query" -> and(termQuery("relations", docType), query))
    elasticConfig.postRaw(s"/$indexName/_search", queryWithType, params: _*)
  }
  override def scroll(scrollId: String, keepAlive: String)(implicit ec: ExecutionContext): Future[JsValue] =
    elasticConfig.postJson("/_search/scroll", Json.obj("scroll_id" -> scrollId, "scroll" -> keepAlive))
  override def clearScroll(scrollId: String)(implicit ec: ExecutionContext): Future[JsValue] =
    elasticConfig.delete("/_search/scroll", Json.obj("scroll_id" -> scrollId))
}
