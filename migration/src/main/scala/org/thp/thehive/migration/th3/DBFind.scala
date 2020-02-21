package org.thp.thehive.migration.th3

import scala.collection.mutable
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import play.api.libs.json._
import play.api.{Configuration, Logger}

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.stream.stage.{AsyncCallback, GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.search.{SearchHit, SearchResponse}
import com.sksamuel.elastic4s.searches.SearchRequest
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.{InternalError, SearchError}

/**
  * Service class responsible for entity search
  */
@Singleton
class DBFind(pageSize: Int, keepAlive: FiniteDuration, db: DBConfiguration, implicit val ec: ExecutionContext, implicit val mat: Materializer) {

  @Inject def this(configuration: Configuration, db: DBConfiguration, ec: ExecutionContext, mat: Materializer) =
    this(configuration.get[Int]("search.pagesize"), configuration.getMillis("search.keepalive").millis, db, ec, mat)

  val keepAliveStr: String        = keepAlive.toMillis + "ms"
  private[DBFind] lazy val logger = Logger(getClass)

  /**
    * return a new instance of DBFind but using another DBConfiguration
    */
  def switchTo(otherDB: DBConfiguration) = new DBFind(pageSize, keepAlive, otherDB, ec, mat)

  /**
    * Extract offset and limit from optional range
    * Range has the following format : "start-end"
    * If format is invalid of range is None, this function returns (0, 10)
    */
  def getOffsetAndLimitFromRange(range: Option[String]): (Int, Int) =
    range match {
      case None        => (0, 10)
      case Some("all") => (0, Int.MaxValue)
      case Some(r) =>
        val Array(_offset, _end, _*) = (r + "-0").split("-", 3)
        val offset                   = Try(Math.max(0, _offset.toInt)).getOrElse(0)
        val end                      = Try(_end.toInt).getOrElse(offset + 10)
        if (end <= offset)
          (offset, 10)
        else
          (offset, end - offset)
    }

  /**
    * Execute the search definition using scroll
    */
  def searchWithScroll(searchRequest: SearchRequest, offset: Int, limit: Int): (Source[SearchHit, NotUsed], Future[Long]) = {
    val searchWithScroll = new SearchWithScroll(db, searchRequest, keepAliveStr, offset, limit)
    (Source.fromGraph(searchWithScroll), searchWithScroll.totalHits)
  }

  /**
    * Execute the search definition
    */
  def searchWithoutScroll(searchRequest: SearchRequest, offset: Int, limit: Int): (Source[SearchHit, NotUsed], Future[Long]) = {
    val resp  = db.execute(searchRequest.start(offset).limit(limit))
    val total = resp.map(_.totalHits)
    val src = Source
      .future(resp)
      .mapConcat { resp =>
        resp.hits.hits.toList
      }
    (src, total)
  }

  /**
    * Search entities in ElasticSearch
    *
    * @param range  first and last entities to retrieve, for example "23-42" (default value is "0-10")
    * @param sortBy define order of the entities by specifying field names used in sort. Fields can be prefixed by
    *               "-" for descendant or "+" for ascendant sort (ascendant by default).
    * @param query  a function that build a SearchRequest using the index name
    * @return Source (akka stream) of JsObject. The source is materialized as future of long that contains the total number of entities.
    */
  def apply(range: Option[String], sortBy: Seq[String])(query: String => SearchRequest): (Source[JsObject, NotUsed], Future[Long]) = {
    val (offset, limit) = getOffsetAndLimitFromRange(range)
    val sortDef         = DBUtils.sortDefinition(sortBy)
    val searchRequest   = query(db.indexName).start(offset).sortBy(sortDef).version(true)

    logger.debug(
      s"search in ${searchRequest.indexesTypes.indexes.mkString(",")} / ${searchRequest.indexesTypes.types.mkString(",")} ${db.client.show(searchRequest)}"
    )
    val (src, total) = if (limit > 2 * pageSize) {
      searchWithScroll(searchRequest, offset, limit)
    } else {
      searchWithoutScroll(searchRequest, offset, limit)
    }

    (src.map(DBUtils.hit2json), total)
  }

  /**
    * Execute the search definition
    * This function is used to run aggregations
    */
  def apply(query: String => SearchRequest): Future[SearchResponse] = {
    val searchRequest = query(db.indexName)
    logger.debug(
      s"search in ${searchRequest.indexesTypes.indexes.mkString(",")} / ${searchRequest.indexesTypes.types.mkString(",")} ${db.client.show(searchRequest)}"
    )

    db.execute(searchRequest)
      .recoverWith {
        case t: InternalError => Future.failed(t)
        case t                => Future.failed(SearchError("Invalid search query"))
      }
  }
}

class SearchWithScroll(db: DBConfiguration, SearchRequest: SearchRequest, keepAliveStr: String, offset: Int, max: Int)(
    implicit
    ec: ExecutionContext
) extends GraphStage[SourceShape[SearchHit]] {

  private[SearchWithScroll] lazy val logger = Logger(getClass)
  val out: Outlet[SearchHit]                = Outlet[SearchHit]("searchHits")
  val shape: SourceShape[SearchHit]         = SourceShape.of(out)
  val firstResults: Future[SearchResponse]  = db.execute(SearchRequest.scroll(keepAliveStr))
  val totalHits: Future[Long]               = firstResults.map(_.totalHits)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    var processed: Long                 = 0
    var skip: Long                      = offset.toLong
    val queue: mutable.Queue[SearchHit] = mutable.Queue.empty
    var scrollId: Future[String]        = firstResults.map(_.scrollId.get)
    var firstResultProcessed            = false

    setHandler(
      out,
      new OutHandler {

        def pushNextHit(): Unit = {
          push(out, queue.dequeue())
          processed += 1
          if (processed >= max) {
            completeStage()
          }
        }

        val firstCallback: AsyncCallback[Try[SearchResponse]] = getAsyncCallback[Try[SearchResponse]] {
          case Success(searchResponse) if skip > 0 =>
            if (searchResponse.hits.size <= skip)
              skip -= searchResponse.hits.size
            else {
              queue ++= searchResponse.hits.hits.drop(skip.toInt)
              skip = 0
            }
            firstResultProcessed = true
            onPull()
          case Success(searchResponse) =>
            queue ++= searchResponse.hits.hits
            firstResultProcessed = true
            onPull()
          case Failure(error) =>
            logger.warn("Search error", error)
            failStage(error)
        }

        override def onPull(): Unit =
          if (firstResultProcessed) {
            if (processed >= max) completeStage()

            if (queue.isEmpty) {
              val callback = getAsyncCallback[Try[SearchResponse]] {
                case Success(searchResponse) if searchResponse.isTimedOut =>
                  logger.warn("Search timeout")
                  failStage(SearchError("Request terminated early or timed out"))
                case Success(searchResponse) if searchResponse.isEmpty =>
                  completeStage()
                case Success(searchResponse) if skip > 0 =>
                  if (searchResponse.hits.size <= skip) {
                    skip -= searchResponse.hits.size
                    onPull()
                  } else {
                    queue ++= searchResponse.hits.hits.drop(skip.toInt)
                    skip = 0
                    pushNextHit()
                  }
                case Success(searchResponse) =>
                  queue ++= searchResponse.hits.hits
                  pushNextHit()
                case Failure(error) =>
                  logger.warn("Search error", error)
                  failStage(SearchError("Request terminated early or timed out"))
              }
              val futureSearchResponse = scrollId.flatMap(s => db.execute(searchScroll(s).keepAlive(keepAliveStr)))
              scrollId = futureSearchResponse.map(_.scrollId.get)
              futureSearchResponse.onComplete(callback.invoke)
            } else {
              pushNextHit()
            }
          } else firstResults.onComplete(firstCallback.invoke)
      }
    )
    override def postStop(): Unit =
      scrollId.foreach { s =>
        db.execute(clearScroll(s))
      }
  }
}
