"""
Models
======

These classes provide models for the data returned by the GeoIP2
web service and databases.

The only difference between the City and Insights model classes is which
fields in each record may be populated. See
http://dev.maxmind.com/geoip/geoip2/web-services for more details.

"""
# pylint:disable=R0903
import geoip2.records


class Country(object):

    """Model for the GeoIP2 Precision: Country and the GeoIP2 Country database

    This class provides the following attributes:

    .. attribute:: continent

      Continent object for the requested IP address.

      :type: :py:class:`geoip2.records.Continent`

    .. attribute:: country

      Country object for the requested IP address. This record represents the
      country where MaxMind believes the IP is located.

      :type: :py:class:`geoip2.records.Country`

    .. attribute:: maxmind

      Information related to your MaxMind account.

      :type: :py:class:`geoip2.records.MaxMind`

    .. attribute:: registered_country

      The registered country object for the requested IP address. This record
      represents the country where the ISP has registered a given IP block in
      and may differ from the user's country.

      :type: :py:class:`geoip2.records.Country`

    .. attribute:: represented_country

      Object for the country represented by the users of the IP address
      when that country is different than the country in ``country``. For
      instance, the country represented by an overseas military base.

      :type: :py:class:`geoip2.records.RepresentedCountry`

    .. attribute:: traits

      Object with the traits of the requested IP address.

      :type: :py:class:`geoip2.records.Traits`

    """

    def __init__(self, raw_response, locales=None):
        if locales is None:
            locales = ['en']
        self.continent = \
            geoip2.records.Continent(locales,
                                     **raw_response.get('continent', {}))
        self.country = \
            geoip2.records.Country(locales,
                                   **raw_response.get('country', {}))
        self.registered_country = \
            geoip2.records.Country(locales,
                                   **raw_response.get('registered_country',
                                                      {}))
        # pylint:disable=bad-continuation
        self.represented_country \
            = geoip2.records.RepresentedCountry(locales,
                                                **raw_response.get(
                                                'represented_country', {}))

        self.maxmind = \
            geoip2.records.MaxMind(**raw_response.get('maxmind', {}))

        self.traits = geoip2.records.Traits(**raw_response.get('traits', {}))
        self.raw = raw_response


class City(Country):

    """Model for the GeoIP2 Precision: City and the GeoIP2 City database
    .. attribute:: city

      City object for the requested IP address.

      :type: :py:class:`geoip2.records.City`

    .. attribute:: continent

      Continent object for the requested IP address.

      :type: :py:class:`geoip2.records.Continent`

    .. attribute:: country

      Country object for the requested IP address. This record represents the
      country where MaxMind believes the IP is located.

      :type: :py:class:`geoip2.records.Country`

    .. attribute:: location

      Location object for the requested IP address.

    .. attribute:: maxmind

      Information related to your MaxMind account.

      :type: :py:class:`geoip2.records.MaxMind`

    .. attribute:: registered_country

      The registered country object for the requested IP address. This record
      represents the country where the ISP has registered a given IP block in
      and may differ from the user's country.

      :type: :py:class:`geoip2.records.Country`

    .. attribute:: represented_country

      Object for the country represented by the users of the IP address
      when that country is different than the country in ``country``. For
      instance, the country represented by an overseas military base.

      :type: :py:class:`geoip2.records.RepresentedCountry`

    .. attribute:: subdivisions

      Object (tuple) representing the subdivisions of the country to which
      the location of the requested IP address belongs.

      :type: :py:class:`geoip2.records.Subdivisions`

    .. attribute:: traits

      Object with the traits of the requested IP address.

      :type: :py:class:`geoip2.records.Traits`

    """

    def __init__(self, raw_response, locales=None):
        super(City, self).__init__(raw_response, locales)
        self.city = \
            geoip2.records.City(locales, **raw_response.get('city', {}))
        self.location = \
            geoip2.records.Location(**raw_response.get('location', {}))
        self.postal = \
            geoip2.records.Postal(**raw_response.get('postal', {}))
        self.subdivisions = \
            geoip2.records.Subdivisions(locales,
                                        *raw_response.get('subdivisions', []))


class Insights(City):

    """Model for the GeoIP2 Precision: Insights web service endpoint

    .. attribute:: city

      City object for the requested IP address.

      :type: :py:class:`geoip2.records.City`

    .. attribute:: continent

      Continent object for the requested IP address.

      :type: :py:class:`geoip2.records.Continent`

    .. attribute:: country

      Country object for the requested IP address. This record represents the
      country where MaxMind believes the IP is located.

      :type: :py:class:`geoip2.records.Country`

    .. attribute:: location

      Location object for the requested IP address.

    .. attribute:: maxmind

      Information related to your MaxMind account.

      :type: :py:class:`geoip2.records.MaxMind`

    .. attribute:: registered_country

      The registered country object for the requested IP address. This record
      represents the country where the ISP has registered a given IP block in
      and may differ from the user's country.

      :type: :py:class:`geoip2.records.Country`

    .. attribute:: represented_country

      Object for the country represented by the users of the IP address
      when that country is different than the country in ``country``. For
      instance, the country represented by an overseas military base.

      :type: :py:class:`geoip2.records.RepresentedCountry`

    .. attribute:: subdivisions

      Object (tuple) representing the subdivisions of the country to which
      the location of the requested IP address belongs.

      :type: :py:class:`geoip2.records.Subdivisions`

    .. attribute:: traits

      Object with the traits of the requested IP address.

      :type: :py:class:`geoip2.records.Traits`

    """


class ConnectionType(object):

    """Model class for the GeoIP2 Connection-Type

    This class provides the following attribute:

    .. attribute:: connection_type

      The connection type may take the following values:

      - Dialup
      - Cable/DSL
      - Corporate
      - Cellular

      Additional values may be added in the future.

      :type: unicode

    .. attribute:: ip_address

      The IP address used in the lookup.

      :type: unicode
    """

    def __init__(self, raw):
        self.connection_type = raw.get('connection_type')
        self.ip_address = raw.get('ip_address')
        self.raw = raw


class Domain(object):

    """Model class for the GeoIP2 Domain

    This class provides the following attribute:

    .. attribute:: domain

      The domain associated with the IP address.

      :type: unicode

    .. attribute:: ip_address

      The IP address used in the lookup.

      :type: unicode

    """

    def __init__(self, raw):
        self.domain = raw.get('domain')
        self.ip_address = raw.get('ip_address')
        self.raw = raw


class ISP(object):

    """Model class for the GeoIP2 ISP

    This class provides the following attribute:

    .. attribute:: autonomous_system_number

      The autonomous system number associated with the IP address.

      :type: int

    .. attribute:: autonomous_system_organization

      The organization associated with the registered autonomous system number
      for the IP address.

      :type: unicode

    .. attribute:: isp

      The name of the ISP associated with the IP address.

      :type: unicode

    .. attribute:: organization

      The name of the organization associated with the IP address.

      :type: unicode

    .. attribute:: ip_address

      The IP address used in the lookup.

      :type: unicode
    """

    # pylint:disable=too-many-arguments
    def __init__(self, raw):
        self.autonomous_system_number = raw.get('autonomous_system_number')
        self.autonomous_system_organization = raw.get(
            'autonomous_system_organization')
        self.isp = raw.get('isp')
        self.organization = raw.get('organization')
        self.ip_address = raw.get('ip_address')
        self.raw = raw
