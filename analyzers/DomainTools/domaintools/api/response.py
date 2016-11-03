import json
from   domaintools.utils        import obj

"""
This file is part of the domaintoolsAPI_python_wrapper package.
For the full copyright and license information, please view the LICENSE
file that was distributed with this source code.
"""

class Response(object):

    def __init__(self, request=None):
        """
        Constructs the Response object
        """

        #json string respresenting the response
        self.json       = None

        #json object representation
        self.jsonObject = None

        #request object for API
        self.request    = request

        self.mergeJson(request.raw_response)

    def mergeJson(self, raw=None):
        """
        define self.json and self.jsonObject only if conversion worked
        otherwise we keep the old values
        """

        jsonObject = obj(json.loads(raw))
        if(jsonObject == None): raise ServiceException()

        self.json       = raw
        self.jsonObject = jsonObject

    def __getattr__(self, name):
        """
       Magic get method to create an alias :
       self.history <=> self.jsonObject.response.history

       if      self.history already exists               => return value
       elseif  self.jsonObject.response.history exists   => return value
       else                                              => return None
        """

        try:
            return object.__getattribute__(self, name)
        except AttributeError:
            try:
                return object.__getattribute__(self.jsonObject.response, name)
            except AttributeError:
                return None

