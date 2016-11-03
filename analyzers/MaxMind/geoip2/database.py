"""
======================
GeoIP2 Database Reader
======================

"""
import inspect

import geoip2
import geoip2.models
import geoip2.errors
import maxminddb


class Reader(object):

    """Creates a new GeoIP2 database Reader object.

    Instances of this class provide a reader for the GeoIP2 database format.
    IP addresses can be looked up using the ``country`` and ``city`` methods.

     Usage
     -----

    The basic API for this class is the same for every database. First, you
    create a reader object, specifying a file name. You then call the method
    corresponding to the specific database, passing it the IP address you want
    to look up.

    If the request succeeds, the method call will return a model class for the
    method you called. This model in turn contains multiple record classes,
    each of which represents part of the data returned by the database. If the
    database does not contain the requested information, the attributes on the
    record class will have a ``None`` value.

    If the address is not in the database, an
    ``geoip2.errors.AddressNotFoundError`` exception will be thrown. If the
    database is corrupt or invalid, a ``maxminddb.InvalidDatabaseError`` will
    be thrown.

"""

    def __init__(self, filename, locales=None):
        if locales is None:
            locales = ['en']
        self._db_reader = maxminddb.Reader(filename)
        self._locales = locales

    def country(self, ip_address):
        """Get the Country object for the IP address

        :param ip_address: IPv4 or IPv6 address as a string.

        :returns: :py:class:`geoip2.models.Country` object

        """

        return self._model_for(geoip2.models.Country, 'Country', ip_address)

    def city(self, ip_address):
        """Get the City object for the IP address

        :param ip_address: IPv4 or IPv6 address as a string.

        :returns: :py:class:`geoip2.models.City` object

        """
        return self._model_for(geoip2.models.City, 'City', ip_address)

    def connection_type(self, ip_address):
        """Get the ConnectionType object for the IP address

        :param ip_address: IPv4 or IPv6 address as a string.

        :returns: :py:class:`geoip2.models.ConnectionType` object

        """
        return self._flat_model_for(geoip2.models.ConnectionType,
                                    'GeoIP2-Connection-Type',
                                    ip_address)

    def domain(self, ip_address):
        """Get the Domain object for the IP address

        :param ip_address: IPv4 or IPv6 address as a string.

        :returns: :py:class:`geoip2.models.Domain` object

        """
        return self._flat_model_for(geoip2.models.Domain,
                                    'GeoIP2-Domain',
                                    ip_address)

    def isp(self, ip_address):
        """Get the ISP object for the IP address

        :param ip_address: IPv4 or IPv6 address as a string.

        :returns: :py:class:`geoip2.models.ISP` object

        """
        return self._flat_model_for(geoip2.models.ISP,
                                    'GeoIP2-ISP',
                                    ip_address)

    def _get(self, database_type, ip_address):
        if not database_type in self.metadata().database_type:
            caller = inspect.stack()[2][3]
            raise TypeError("The %s method cannot be used with the "
                            "%s database" %
                            (caller, self.metadata().database_type))
        record = self._db_reader.get(ip_address)
        if record is None:
            raise geoip2.errors.AddressNotFoundError(
                "The address %s is not in the database." % ip_address)
        return record

    def _model_for(self, model_class, types, ip_address):
        record = self._get(types, ip_address)
        record.setdefault('traits', {})['ip_address'] = ip_address
        return model_class(record, locales=self._locales)

    def _flat_model_for(self, model_class, types, ip_address):
        record = self._get(types, ip_address)
        record['ip_address'] = ip_address
        return model_class(record)

    def metadata(self):
        """The metadata for the open database

        :returns: :py:class:`maxminddb.reader.Metadata` object
        """
        return self._db_reader.metadata()

    def close(self):
        """Closes the GeoIP2 database"""

        self._db_reader.close()
