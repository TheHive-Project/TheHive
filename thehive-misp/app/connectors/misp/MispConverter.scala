package connectors.misp

trait MispConverter {

  def convertAttribute(mispAttribute: MispAttribute): Seq[MispArtifact] = {
    val tags = Seq(s"MISP:type=${mispAttribute.tpe}", s"MISP:category=${mispAttribute.category}")
    if (mispAttribute.tpe == "attachment" || mispAttribute.tpe == "malware-sample") {
      Seq(
        MispArtifact(
          value = RemoteAttachmentArtifact(mispAttribute.value.split("\\|").head, mispAttribute.id, mispAttribute.tpe),
          dataType = "file",
          message = mispAttribute.comment,
          tlp = 0,
          tags = tags ++ mispAttribute.tags,
          startDate = mispAttribute.date,
          ioc = mispAttribute.toIds
        )
      )
    } else {
      val dataType = toArtifact(mispAttribute.tpe)
      val artifact =
        MispArtifact(
          value = SimpleArtifactData(mispAttribute.value),
          dataType = dataType,
          message = mispAttribute.comment,
          tlp = 0,
          tags = tags ++ mispAttribute.tags,
          startDate = mispAttribute.date,
          ioc = mispAttribute.toIds
        )

      val types = mispAttribute.tpe.split('|').toSeq
      if (types.length > 1) {
        val values      = mispAttribute.value.split('|').toSeq
        val typesValues = types.zipAll(values, "noType", "noValue")
        val additionnalMessage = typesValues
          .map {
            case (t, v) => s"$t: $v"
          }
          .mkString("\n")
        typesValues.map {
          case (tpe, value) =>
            artifact.copy(dataType = toArtifact(tpe), value = SimpleArtifactData(value), message = mispAttribute.comment + "\n" + additionnalMessage)
        }
      } else {
        Seq(artifact)
      }
    }
  }

  def fromArtifact(dataType: String, data: Option[String]): (String, String) =
    dataType match {
      case "filename"     => "Payload delivery" -> "filename"
      case "fqdn"         => "Network activity" -> "hostname"
      case "url"          => "Network activity" -> "url"
      case "user-agent"   => "Network activity" -> "user-agent"
      case "domain"       => "Network activity" -> "domain"
      case "ip"           => "Network activity" -> "ip-src"
      case "mail_subject" => "Payload delivery" -> "email-subject"
      case "hash" =>
        data.fold(0)(_.length) match {
          case 32  => "Payload delivery" -> "md5"
          case 40  => "Payload delivery" -> "sha1"
          case 64  => "Payload delivery" -> "sha256"
          case 56  => "Payload delivery" -> "sha224"
          case 71  => "Payload delivery" -> "sha384"
          case 128 => "Payload delivery" -> "sha512"
          case _   => "Payload delivery" -> "other"
        }
      case "mail"     => "Payload delivery"      -> "email-src"
      case "registry" => "Persistence mechanism" -> "regkey"
      case "uri_path" => "Network activity"      -> "uri"
      case "regexp"   => "Other"                 -> "other"
      case "other"    => "Other"                 -> "other"
      case "file"     => "Payload delivery"      -> "malware-sample"
      case _          => "Other"                 -> "other"
    }

  def toArtifact(tpe: String): String = attribute2artifactLookup.getOrElse(tpe, "other")

  private lazy val attribute2artifactLookup = Map(
    "md5"                                      -> "hash",
    "sha1"                                     -> "hash",
    "sha256"                                   -> "hash",
    "filename"                                 -> "filename",
    "pdb"                                      -> "other",
    "filename|md5"                             -> "other",
    "filename|sha1"                            -> "other",
    "filename|sha256"                          -> "other",
    "ip-src"                                   -> "ip",
    "ip-dst"                                   -> "ip",
    "hostname"                                 -> "fqdn",
    "domain"                                   -> "domain",
    "domain|ip"                                -> "other",
    "email-src"                                -> "mail",
    "email-dst"                                -> "mail",
    "email-subject"                            -> "mail_subject",
    "email-attachment"                         -> "other",
    "float"                                    -> "other",
    "url"                                      -> "url",
    "http-method"                              -> "other",
    "user-agent"                               -> "user-agent",
    "regkey"                                   -> "registry",
    "regkey|value"                             -> "registry",
    "AS"                                       -> "other",
    "snort"                                    -> "other",
    "pattern-in-file"                          -> "other",
    "pattern-in-traffic"                       -> "other",
    "pattern-in-memory"                        -> "other",
    "yara"                                     -> "other",
    "sigma"                                    -> "other",
    "vulnerability"                            -> "other",
    "attachment"                               -> "file",
    "malware-sample"                           -> "file",
    "link"                                     -> "other",
    "comment"                                  -> "other",
    "text"                                     -> "other",
    "hex"                                      -> "other",
    "other"                                    -> "other",
    "named"                                    -> "other",
    "mutex"                                    -> "other",
    "target-user"                              -> "other",
    "target-email"                             -> "mail",
    "target-machine"                           -> "fqdn",
    "target-org"                               -> "other",
    "target-location"                          -> "other",
    "target-external"                          -> "other",
    "btc"                                      -> "other",
    "iban"                                     -> "other",
    "bic"                                      -> "other",
    "bank-account-nr"                          -> "other",
    "aba-rtn"                                  -> "other",
    "bin"                                      -> "other",
    "cc-number"                                -> "other",
    "prtn"                                     -> "other",
    "threat-actor"                             -> "other",
    "campaign-name"                            -> "other",
    "campaign-id"                              -> "other",
    "malware-type"                             -> "other",
    "uri"                                      -> "uri_path",
    "authentihash"                             -> "other",
    "ssdeep"                                   -> "hash",
    "imphash"                                  -> "hash",
    "pehash"                                   -> "hash",
    "impfuzzy"                                 -> "hash",
    "sha224"                                   -> "hash",
    "sha384"                                   -> "hash",
    "sha512"                                   -> "hash",
    "sha512/224"                               -> "hash",
    "sha512/256"                               -> "hash",
    "tlsh"                                     -> "other",
    "filename|authentihash"                    -> "other",
    "filename|ssdeep"                          -> "other",
    "filename|imphash"                         -> "other",
    "filename|impfuzzy"                        -> "other",
    "filename|pehash"                          -> "other",
    "filename|sha224"                          -> "other",
    "filename|sha384"                          -> "other",
    "filename|sha512"                          -> "other",
    "filename|sha512/224"                      -> "other",
    "filename|sha512/256"                      -> "other",
    "filename|tlsh"                            -> "other",
    "windows-scheduled-task"                   -> "other",
    "windows-service-name"                     -> "other",
    "windows-service-displayname"              -> "other",
    "whois-registrant-email"                   -> "mail",
    "whois-registrant-phone"                   -> "other",
    "whois-registrant-name"                    -> "other",
    "whois-registrar"                          -> "other",
    "whois-creation-date"                      -> "other",
    "x509-fingerprint-sha1"                    -> "other",
    "dns-soa-email"                            -> "other",
    "size-in-bytes"                            -> "other",
    "counter"                                  -> "other",
    "datetime"                                 -> "other",
    "cpe"                                      -> "other",
    "port"                                     -> "other",
    "ip-dst|port"                              -> "other",
    "ip-src|port"                              -> "other",
    "hostname|port"                            -> "other",
    "email-dst-display-name"                   -> "other",
    "email-src-display-name"                   -> "other",
    "email-header"                             -> "other",
    "email-reply-to"                           -> "other",
    "email-x-mailer"                           -> "other",
    "email-mime-boundary"                      -> "other",
    "email-thread-index"                       -> "other",
    "email-message-id"                         -> "other",
    "github-username"                          -> "other",
    "github-repository"                        -> "other",
    "github-organisation"                      -> "other",
    "jabber-id"                                -> "other",
    "twitter-id"                               -> "other",
    "first-name"                               -> "other",
    "middle-name"                              -> "other",
    "last-name"                                -> "other",
    "date-of-birth"                            -> "other",
    "place-of-birth"                           -> "other",
    "gender"                                   -> "other",
    "passport-number"                          -> "other",
    "passport-country"                         -> "other",
    "passport-expiration"                      -> "other",
    "redress-number"                           -> "other",
    "nationality"                              -> "other",
    "visa-number"                              -> "other",
    "issue-date-of-the-visa"                   -> "other",
    "primary-residence"                        -> "other",
    "country-of-residence"                     -> "other",
    "special-service-request"                  -> "other",
    "frequent-flyer-number"                    -> "other",
    "travel-details"                           -> "other",
    "payment-details"                          -> "other",
    "place-port-of-original-embarkation"       -> "other",
    "place-port-of-clearance"                  -> "other",
    "place-port-of-onward-foreign-destination" -> "other",
    "passenger-name-record-locator-number"     -> "other",
    "mobile-application-id"                    -> "other"
  )
}
