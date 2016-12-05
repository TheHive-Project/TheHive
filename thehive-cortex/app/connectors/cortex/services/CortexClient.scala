package connectors.cortex.services

import scala.concurrent.{ ExecutionContext, Future }

import akka.stream.scaladsl.{ FileIO, Source }

import play.api.libs.json.{ JsValue, Json }
import play.api.libs.ws.{ WSClient, WSRequest, WSResponse }
import play.api.mvc.MultipartFormData.{ DataPart, FilePart }

import org.elastic4play.models.JsonFormat._
import connectors.cortex.models.JsonFormat._
import connectors.cortex.models.Analyzer
import connectors.cortex.models.FileArtifact
import connectors.cortex.models.DataArtifact
import connectors.cortex.models.Job
import connectors.cortex.models.CortexArtifact
import play.api.libs.json.JsObject
import org.elastic4play.NotFoundError

class CortexClient(name: String, baseUrl: String, key: String) {
  val fakeAnalyzers = Seq(
    Analyzer("DNSDB_DomainName", "1.1", "DNSDB Passive DNS query for Domain Names : Provides history records for a domain", Seq("domain"), List(name)),
    Analyzer("DNSDB_IPHistory", "1.0", "DNSDB Passive DNS query for IP history : Provides history records for an IP", Seq("ip"), List(name)),
    Analyzer("DNSDB_NameHistory", "1.0", "DNSDB Passive DNS query for domain/host name history : Provides history records for an domain/host", Seq("fqdn"), List(name)),
    Analyzer("DomainTools_ReverseIP", "1.0", "DomainTools Reverse IP: provides a list of domain names that share the same Internet host", Seq("ip"), List(name)),
    Analyzer("DomainTools_ReverseNameServer", "1.0", "DomainTools Reverse Name server: provides a list of domain names that share the same primary or secondary name server", Seq("domain"), List(name)),
    Analyzer("DomainTools_ReverseWhois", "1.0", "Domaintools Reverse Whois lookup : provides a list of domain names that share the same Registrant Information.", Seq("mail", "ip", "domain", "other"), List(name)),
    Analyzer("DomainTools_WhoisHistory", "1.0", "DomainTools Whois History: provides a list of historic Whois records for a domain name", Seq("domain"), List(name)),
    Analyzer("DomainTools_WhoisLookup", "1.0", "DomainTools Whois Lookup: provides the ownership record for a domain name with basic registration details", Seq("domain"), List(name)),
    Analyzer("DomainTools_WhoisLookup_IP", "1.0", "DomainTools Whois Lookup IP: provides the ownership record for a IP address with basic registration details", Seq("ip"), List(name)),
    Analyzer("Hipposcore", "1.0", "Hippocampe Score report: provides the last report for an IP, domain or a URL", Seq("ip", "domain", "fqdn", "url"), List(name)),
    Analyzer("HippoMore", "1.0", "Hippocampe detailed report: provides the last detailed report for an IP, domain or a URL", Seq("ip", "domain", "fqdn", "url"), List(name)),
    Analyzer("MaxMind_GeoIP", "2.0", "MaxMind: Geolocation", Seq("ip"), List(name)),
    Analyzer("Msg_Parser", "1.0", "Outlook .msg file parser", Seq("file"), List(name)),
    Analyzer("Olevba_Report", "1.0", "Olevba analysis report. Submit a Microsoft Office File.", Seq("file"), List(name)),
    Analyzer("URLCategory", "1.0", "URL Category query: checks the category of a specific URL or domain", Seq("url", "domain"), List(name)),
    Analyzer("VirusTotal_GetReport", "1.0", "VirusTotal get file report: provides the last report of a file. Submit a hash(md5/sha1/sha256)", Nil, List(name)),
    Analyzer("VirusTotal_GetReport", "2.0", "VirusTotal get report: provides the last report of a file, hash, domain or ip", Seq("file", "hash", "domain", "ip"), List(name)),
    Analyzer("VirusTotal_Scan", "2.0", "VirusTotal scan file or url", Seq("file", "url"), List(name)),
    Analyzer("VirusTotal_UrlReport", "1.0", "VirusTotal get url report: provides the last report of a url or site. Submit a url", Nil, List(name)))

  def request[A](uri: String, f: WSRequest ⇒ Future[WSResponse], t: WSResponse ⇒ A)(implicit ws: WSClient, ec: ExecutionContext) = {
    f(ws.url(baseUrl + "/" + uri).withHeaders("auth" → key)).map {
      case response if response.status / 100 == 2 ⇒ t(response)
      case error                                  ⇒ ???
    }
  }

  def getAnalyzer(analyzerId: String)(implicit ws: WSClient, ec: ExecutionContext): Future[Analyzer] = {
    //request(s"/api/analyzer/$analyzerId", _.get, _.json.as[Analyzer])
    fakeAnalyzers.find(_.id == analyzerId).fold[Future[Analyzer]](Future.failed(NotFoundError("")))(a ⇒
      Future.successful(a))
  }

  def listAnalyzer(implicit ws: WSClient, ec: ExecutionContext): Future[Seq[Analyzer]] = {
    //request(s"/api/analyzer", _.get, _.json.as[Seq[Analyzer]])
    Future.successful(fakeAnalyzers)
  }

  def analyze(analyzerId: String, artifact: CortexArtifact)(implicit ws: WSClient, ec: ExecutionContext) = {
    artifact match {
      case FileArtifact(file, attributes) ⇒
        val body = Source(FilePart("data", file.getName, None, FileIO.fromPath(file.toPath)) :: DataPart("_json", attributes.toString) :: Nil)
        request(s"/api/analyzer/$analyzerId", _.post(body), _.json)
      case a: DataArtifact ⇒
        request(s"/api/analyzer/$analyzerId", _.post(Json.toJson(a)), _.json.as[JsObject])
    }
  }

  def listAnalyzerForType(dataType: String)(implicit ws: WSClient, ec: ExecutionContext): Future[Seq[Analyzer]] = {
    //request(s"/api/analyzer/type/$dataType", _.get, _.json.as[Seq[Analyzer]])
    Future.successful(fakeAnalyzers.filter(_.dataTypeList.contains(dataType)))
  }

  def listJob(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job", _.get, _.json.as[Seq[JsObject]])
  }

  def getJob(jobId: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job/$jobId", _.get, _.json.as[JsObject])
  }

  def removeJob(jobId: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job/$jobId", _.delete, _ ⇒ ())
  }

  def report(jobId: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job/$jobId/report", _.get, r ⇒ r.json.as[JsObject])
  }

  def waitReport(jobId: String, atMost: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job/$jobId/waitreport", _.get, r ⇒ r.json.as[JsObject])
  }
}