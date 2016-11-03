"""
This file is part of the domaintoolsAPI_python_wrapper package.
For the full copyright and license information, please view the LICENSE
file that was distributed with this source code.
"""

class ServiceException(Exception):

    INVALID_CONFIG_PATH    = "Config file do not exist";
    UNKNOWN_SERVICE_NAME   = "Unknown service name";
    EMPTY_API_KEY          = "Empty API key";
    EMPTY_API_USERNAME     = "Empty API username";
    UNKNOWN_RETURN_TYPE    = "Unknown return type. (json or xml or html required)";

    INVALID_DOMAIN         = "Domain/Ip invalide";
    INVALID_OPTIONS        = "Invalid options; options must be an array";

    TRANSPORT_NOT_FOUND    = "Transport not found; it must refer to a class that extends RESTService";
    DOMAIN_CALL_REQUIRED   = "Domain is required for this service";
    IP_CALL_REQUIRED       = "Ip address is required for this service";
    EMPTY_CALL_REQUIRED    = "No domain or ip is required for this service";

    INVALID_REQUEST_OBJECT = "Invalid object; DomaintoolsAPI instance required";
    INVALID_JSON_STRING    = "Invalid json string; a valid one is required";


class BadRequestException(ServiceException):
    pass

class InternalServerErrorException(ServiceException):
    pass

class NotAuthorizedException(ServiceException):
    pass

class NotFoundException(ServiceException):
    pass

class ServiceUnavailableException(ServiceException):
    pass

