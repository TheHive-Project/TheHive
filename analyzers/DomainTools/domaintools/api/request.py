from   domaintools.api.configuration import Configuration
from   domaintools.api.response      import Response
import hmac
import hashlib
import urllib
from   datetime                      import datetime
from   domaintools.exceptions        import ServiceException
from   domaintools.exceptions        import ServiceUnavailableException
from   domaintools.exceptions        import NotAuthorizedException
from   domaintools.exceptions        import InternalServerErrorException
from   domaintools.exceptions        import NotFoundException
from   domaintools.exceptions        import ServiceUnavailableException
from   domaintools.exceptions        import BadRequestException

"""
This file is part of the domaintoolsAPI_python_wrapper package.
For the full copyright and license information, please view the LICENSE
file that was distributed with this source code.
"""

class Request(object):

    def __init__(self, configuration=None):
        """
        Construction of the class with an optional given configuration object
        (If no configuration given, the default one is taken)
        """
        "Configuration (credentials, host,...)"
        self.configuration           = None

        #Name of the service to call
        self.service_name            = ''

        #Type of the return
        self.return_type             = None

        #Authorized return types
        self.authorized_return_types = ('json', 'xml', 'html')

        #Url of the resource to call
        self.url                     = None

        #Dictionary of options
        self.options                 = {}

        #Name of the domain to use
        self.domain_name             = ''

        #The raw response sent by domaintoolsAPI
        self.raw_response            = None
        
        #The proxy url
        self.proxy                   = None

        self.configuration           = Configuration() if(configuration == None) else configuration
        

    def service(self, service_name=''):
        """Specifies the name of the service to call"""
        self.service_name = service_name
        return self

    def withType(self, return_type):
        """This function allows you to specify the return type of the service"""

        self.return_type = return_type
        return self

    def domain(self, domain_name=''):
        """Set the domain name to use for the API request"""
        
        self.domain_name = domain_name
        return self

    def where(self, options):
        """
        This function allows you to specify an options dictionary
        The current options dictionary is merged with a new one
        """

        if type(options) is not dict: raise ServiceException(ServiceException.INVALID_OPTIONS)
        self.options.update(options)
        return self


    def query(self, query):
        """Alias for self.where({'query'=>query})"""

        return self.where({'query':query})


    def toJson(self):
        """Alias for self.withType('json')"""

        return self.withType('json')

    def toXml(self):
        """Alias for self.withType('xml')"""

        return self.withType('xml')

    def toHtml(self):
        """Alias for self.withType('html')"""

        return self.withType('html')

    def add_credentials_options(self):
        """Add credentials to the Options dictionary (if necessary)."""

        api_username = self.configuration.username
        api_key      = self.configuration.password

        self.options['api_username'] = api_username

        if self.configuration.secure_auth == True:
            timestamp = datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%SZ')

            uri       = '/' + self.configuration.sub_url + ('/' if self.domain_name.strip()=='' else '/' + self.domain_name + '/') + self.service_name

            self.options['timestamp'] = timestamp
            params                    = ''.join([api_username, timestamp, uri])
            self.options['signature'] = hmac.new(api_key, params, digestmod=hashlib.sha1).hexdigest()
        else:
            self.options['api_key']   = api_key

    def get_service_name(self):
        """getter of service_name"""

        return self.service_name

    def get_options(self):
        """getter of options"""

        return self.options

    def set_transport(self, transport):
        """setter of transport"""

        self.configuration.transport = transport

    def get_return_type(self):
        """getter of return_type"""

        if self.return_type != None:
            return_type = self.return_type
        else:
            return_type = self.configuration.return_type

        return return_type

    def build_options(self):
        """build all options in self.options"""

        self.options['format'] = self.get_return_type()
        self.add_credentials_options()


    def build_url(self):
        """Depending on the service name, and the options we built the good url to request"""

        query_string = urllib.urlencode(self.options)

        self.url     = self.configuration.base_url + ('/' if self.domain_name.strip()=='' else '/' + self.domain_name + '/') + self.service_name + '?' + query_string


    def execute(self, debug=False):
        """Make the request on the service, and return the response"""

        raw_response = ''
        self.build_options()

        if self.return_type == None: self.options['format'] = 'json'

        self.build_url()

        if debug==True: return self.url

        self.raw_response = self.request()

        if self.return_type == None: return Response(self)

        return self.raw_response

    def debug(self):
        return self.execute(True)

    def request(self):
        """
        Makes a request on the service (curl by default), and returns the response if the http status code is 200,
        else returns an exception.
        """
        transport = self.configuration.transport
        response  = ''
        
        try:
            response = transport.get(self.url, self.configuration.proxy)
        except Exception as e:
            raise ServiceUnavailableException()
            
        status = transport.get_status()
        
        if status==200:
            return response
        elif status==400:
            raise BadRequestException()
        elif status==403:
            raise NotAuthorizedException()
        elif status==404:
            raise NotFoundException()
        elif status==500:
            raise InternalServerErrorException()
        elif status==503:
            raise ServiceUnavailableException()
        else:
            raise ServiceException('Empty response')

