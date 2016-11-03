import os
from domaintools                import utils
from domaintools.exceptions     import ServiceException
from domaintools.transport.curl import CurlRestService

"""
This file is part of the domaintoolsAPI_python_wrapper package.
For the full copyright and license information, please view the LICENSE
file that was distributed with this source code.
"""

class Configuration(object):

    def __init__(self, ini_resource = None):
        """
        Construct the class
        Initiliaze it with default values (if no config given)
        """

        #use DomainTools free API
        self.use_free_api        = False
        
        #server host
        self.host                = None

        #server port
        self.port                = None

        #sub url (version)
        self.sub_url             = None

        #beginning url
        self.base_url            = None

        #domaintools API username
        self.username            = None

        #domaintools API password
        self.password            = None

        #secure authentication
        self.secure_path         = True

        #default return type
        self.return_type         = None

        #transport type (curl, etc.)
        self.transport_type      = None

        #transport object in charge of calling API
        self.transport           = None
        
        #proxy url
        self.proxy               = None

        #default configuration file path
        self.default_config_path = os.path.realpath(os.path.dirname(__file__)+'/../conf/api.ini')

        #default configuration
        self.default_config      = {

            'username'       : '',
            'key'            : '',
            'use_free_api'   : False,
            'host'           : 'api.domaintools.com',
            'version'        : 'v1',
            'port'           : '80',
            'secure_auth'    : True,
            'return_type'    : 'json',
            'transport_type' : 'curl',
            'content_type'   : 'application/json',
            'proxy'          : None
        }

        #dictionary to map the good transport class
        self.transport_map = {
            'curl'           : CurlRestService
        }

        if(ini_resource == None): ini_resource = self.default_config_path
        
        config = {}

        if(type(ini_resource) is dict):
            config = ini_resource
        else:
            config = utils.load_config_file(ini_resource)
        
        self.init(config)

    def validateParams(self, config):
        """
        Validate options from a given array
        Merge with the default configuration
        """
        config = dict(self.default_config,**config)

        if config['username'].strip() == '':
            raise ServiceException(ServiceException.EMPTY_API_USERNAME)

        if config['key'].strip() == '':
            raise ServiceException(ServiceException.EMPTY_API_KEY)

        try:
            transport = self.transport_map[config['transport_type']]()
        except Exception as e:
            config['transport_type'] = self.default_config['transport_type'];

        return config


    def init(self, config):
        """
        Initialize the configuration Object
        Returns the configuration dictionary
        """
        config              = self.validateParams(config);

        self.use_free_api   = True if config['use_free_api'] in ('True','true',1) else False
        self.host           = ('free' if self.use_free_api==True else '')+config['host'];
        self.port           = config['port'];
        self.sub_url        = config['version'];
        self.username       = config['username'];
        self.password       = config['key'];
        self.secure_auth    = True if config['secure_auth'] in ('True','true','1') else False;
        self.return_type    = config['return_type'];
        self.content_type   = config['content_type'];
        self.transport_type = config['transport_type'];
        self.proxy          = config['proxy'];

        self.base_url       = 'http://' + self.host +':' + self.port + '/' + self.sub_url;

        self.transport      = self.transport_map[config['transport_type']](self.content_type)
