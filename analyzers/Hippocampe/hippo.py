#!/usr/bin/env python
# encoding: utf-8
import sys
import os
import json
import codecs
import urllib2

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
max_tlp     = get_param('config.max_tlp', 10)
tlp         = get_param('tlp', 2)  # amber by default
data_type   = get_param('dataType', None, 'Missing dataType field')
data        = get_param('data', None, 'Missing data field')
url         = get_param('config.url', None, 'Missing URL for Hippocampe API')
service     = get_param('config.service', None, 'Service parameter is missing')

# run  only if TLP condition is met
if tlp > max_tlp:
    error('Error with TLP value ; see max_tlp in config or tlp value in input data')

# setup proxy
if http_proxy != None:
    os.environ['http_proxy'] = http_proxy
if https_proxy != None:
    os.environ['https_proxy'] = https_proxy

value = {data: {"type": data_type}}
json_data = json.dumps(value)
post_data = json_data.encode('utf-8')
headers = {'Content-Type': 'application/json'}

response = {}
try:
    if (service == 'hipposcore') or (service == 'more'):
        request = urllib2.Request(url + service, post_data, headers)
    elif (service == 'shadowbook'):
        request = urllib2.Request(url + service)

    response = urllib2.urlopen(request)
    report = json.loads(response.read())

    json.dump(report, sys.stdout, ensure_ascii=False)
except urllib2.HTTPError:
    error("Hippocampe: " + str(sys.exc_info()[1]))
except urllib2.URLError:
    error("Hippocampe: service is not available")
except:
    error("An unexpected error occurred: " + str(sys.exc_info()[0]) + ":" + str(sys.exc_info()[1]))