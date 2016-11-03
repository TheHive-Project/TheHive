import urllib2
from urllib2 import Request, urlopen, URLError, HTTPError

"""
This file is part of the domaintoolsAPI_python_wrapper package.
For the full copyright and license information, please view the LICENSE
file that was distributed with this source code.
"""

class RestService(object):

    def __init__(self, content_type='json', options={}):

        self.options      = options
        self.content_type = content_type
        self.status_code  = 200

    def get(self, url, proxy=None):
        if proxy:
            proxy = urllib2.ProxyHandler({'http': proxy})
            opener = urllib2.build_opener(proxy)
            urllib2.install_opener(opener)

        try:
            response = urllib2.urlopen(url)
        except HTTPError, e:
            resp = e.read()
            self.status_code = e.code
        except URLError, e:
            resp = e.read()
            self.status_code = e.code
        else:
            self.status_code = response.code
            resp = response.read()
          
        return resp
        

    def get_status(self):
        return self.status_code

