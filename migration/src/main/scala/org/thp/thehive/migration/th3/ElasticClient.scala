package org.thp.thehive.migration.th3

import akka.NotUsed
import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.sslconfig.ssl.{KeyManagerConfig, KeyStoreConfig, TrustManagerConfig, TrustStoreConfig}
import org.thp.client._
import org.thp.scalligraph.utils.FunctionalCondition.When
import org.thp.scalligraph.utils.Retry
import org.thp.scalligraph.{InternalError, NotFoundError}
import play.api.http.HeaderNames
import play.api.libs.json.{JsNumber, JsObject, JsValue, Json}
import play.api.libs.ws.ahc.AhcWSClientConfig
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.{Configuration, Logger}

import java.net.{URI, URLEncoder}
import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

@Singleton
class ElasticClientProvider @Inject() (
    config: Configuration,
    mat: Materializer,
    implicit val actorSystem: ActorSystem
) extends Provider[ElasticClient] {

  override def get(): ElasticClient = {
    lazy val logger = Logger(getClass)
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
      val connectionTimeout    = config.getOptional[Int]("search.connectTimeout").map(_.millis)
      val idleTimeout          = config.getOptional[Int]("search.socketTimeout").map(_.millis)
      val requestTimeout       = config.getOptional[Int]("search.connectionRequestTimeout").map(_.millis)
      val followRedirects      = config.getOptional[Boolean]("search.redirectsEnabled")
      val maxNumberOfRedirects = config.getOptional[Int]("search.maxRedirects")

      val wsConfig = Try(Json.parse(config.underlying.getValue("search.wsConfig").render(ConfigRenderOptions.concise())).as[ProxyWSConfig])
        .getOrElse(ProxyWSConfig(AhcWSClientConfig(), None))
        .merge(trustManager) { (cfg, tm) =>
          cfg.copy(wsConfig =
            cfg.wsConfig.copy(wsClientConfig = cfg.wsConfig.wsClientConfig.copy(ssl = cfg.wsConfig.wsClientConfig.ssl.withTrustManagerConfig(tm)))
          )
        }
        .merge(keyManager) { (cfg, km) =>
          cfg.copy(wsConfig =
            cfg.wsConfig.copy(wsClientConfig = cfg.wsConfig.wsClientConfig.copy(ssl = cfg.wsConfig.wsClientConfig.ssl.withKeyManagerConfig(km)))
          )
        }
        .merge(connectionTimeout) { (cfg, ct) =>
          cfg.copy(wsConfig = cfg.wsConfig.copy(wsClientConfig = cfg.wsConfig.wsClientConfig.copy(connectionTimeout = ct)))
        }
        .merge(idleTimeout) { (cfg, it) =>
          cfg.copy(wsConfig = cfg.wsConfig.copy(wsClientConfig = cfg.wsConfig.wsClientConfig.copy(idleTimeout = it)))
        }
        .merge(requestTimeout) { (cfg, rt) =>
          cfg.copy(wsConfig = cfg.wsConfig.copy(wsClientConfig = cfg.wsConfig.wsClientConfig.copy(requestTimeout = rt)))
        }
        .merge(followRedirects) { (cfg, fr) =>
          cfg.copy(wsConfig = cfg.wsConfig.copy(wsClientConfig = cfg.wsConfig.wsClientConfig.copy(followRedirects = fr)))
        }
        .merge(maxNumberOfRedirects) { (cfg, mr) =>
          cfg.copy(wsConfig = cfg.wsConfig.copy(maxNumberOfRedirects = mr))
        }
      new ProxyWS(wsConfig, mat)
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
      keepAlive.toMillis + "ms",
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

  def isSingleType(indexName: String): Boolean =
    indexName
      .split('_')
      .lastOption
      .flatMap(version => Try(version.toInt).toOption)
      .fold {
        val response = Await
          .result(
            authentication(ws.url(stripUrl(s"$esUri/$indexName")))
              .get(),
            10.seconds
          )
        if (response.status != 200)
          throw InternalError(s"Unexpected response from Elasticsearch: ${response.status} ${response.statusText}\n${response.body}")
        (response.json \ indexName \ "settings" \ "index" \ "mapping" \ "single_type").asOpt[String].fold(version.head > '6')(_.toBoolean)
      }(version => version >= 15)

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
