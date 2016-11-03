#!/usr/bin/env python
# encoding: utf-8
import sys
import os
import json
import codecs
from domaintools.api.request import Request, Configuration

from domaintools.exceptions import NotFoundException
from domaintools.exceptions import NotAuthorizedException

if sys.stdout.encoding != 'UTF-8':
    if sys.version_info.major == 3:
        sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    else:
        sys.stdout = codecs.getwriter('utf-8')(sys.stdout, 'strict')
if sys.stderr.encoding != 'UTF-8':
    if sys.version_info.major == 3:
        sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')
    else:
        sys.stderr = codecs.getwriter('utf-8')(sys.stderr, 'strict')

# load artifact
artifact = json.load(sys.stdin)

def error(message):
    print('{{"errorMessage":"{}"}}'.format(message))
    sys.exit(1)

def get_param(name, default=None, message=None, current=artifact):
    if isinstance(name, str):
        name = name.split('.')
    if len(name) == 0:
        return current
    else:
        value = current.get(name[0])
        if value == None:
            if message != None:
                error(message)
            else:
                return default
        else:
            return get_param(name[1:], default, message, value)

http_proxy  = get_param('config.proxy.http')
https_proxy = get_param('config.proxy.https')
max_tlp     = get_param('config.max_tlp', 2)
tlp         = get_param('tlp', 2)  # amber by default
data_type   = get_param('dataType', None, 'Missing dataType field')
data        = get_param('data', None, 'Missing data field')
service     = get_param('config.service', None, 'Service parameter is missing')

# run  only if TLP condition is met
if tlp > max_tlp:
    error('Error with TLP value ; see max_tlp in config or tlp value in input data')

# setup proxy
if http_proxy != None:
    os.environ['http_proxy'] = http_proxy
if https_proxy != None:
    os.environ['https_proxy'] = https_proxy
if 'proxy' in artifact['config']:
    del artifact['config']['proxy']

if service == 'reverse-ip' and data_type == 'ip':
    service = 'host-domains'

if service == 'reverse-whois':
    query = {}
    query['terms'] = data
    query['mode'] = "purchase"
    data=''
else:
    query={}

if (service == 'reverse-ip' and data_type == 'domain') or \
        (service == 'host-domains' and data_type == 'ip') or \
        (service == 'name-server-domains' and data_type == 'domain') or \
        (service == 'whois/history' and data_type == 'domain') or \
        (service == 'whois/parsed' and data_type == 'domain') or \
        (service == 'reverse-whois') or\
        (service == 'whois' and data_type == 'ip'):

    response = {}
    try:
        response = Request(Configuration(get_param('config'))).service(service).domain(data).where(query).toJson().execute()
    except NotFoundException:
        error(data_type.capitalize() + " not found")
    except NotAuthorizedException:
        error("An authorization error occurred")
    except:
        error("An unexpected error occurred: " + str(sys.exc_info()[0]) + ":" + str(sys.exc_info()[1]))

    r = json.loads(response)
    if 'response' in r:
        json.dump(r['response'], sys.stdout, ensure_ascii=False)
    elif 'error' in r and 'message' in r['error']:
        error(r['error']['message'])
    else:
        json.dump(r, sys.stdout, ensure_ascii=False)

else:
    error('Unknown DomainTools service or invalid data type')
