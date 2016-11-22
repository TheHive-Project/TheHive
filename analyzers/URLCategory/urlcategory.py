#!/usr/bin/env python
# encoding: utf-8
import sys
import os
import json
import codecs
import time
import re
import requests

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

def debug(msg):
	#print >> sys.stderr, msg
	pass

def fortinet_category(data):
	debug('>> fortinet_category ' + str(data))
	pattern = re.compile("(?:Category: )([\w\s]+)")
	baseurl = 'http://www.fortiguard.com/iprep?data='
	tailurl = '&lookup=Lookup'
	url = baseurl + data + tailurl
	r = requests.get(url)
	category_match = re.search(pattern, r.content, flags=0)
	return category_match.group(1)

http_proxy     = get_param('config.proxy.http')
https_proxy    = get_param('config.proxy.https')
max_tlp        = get_param('config.max_tlp', 1)
tlp            = get_param('tlp', 2) # amber by default
data_type      = get_param('dataType', None, 'Missing dataType field')
service        = get_param('config.service', None, 'Service parameter is missing')

# run  only if TLP condition is met
if tlp > max_tlp:
	error('Error with TLP value ; see max_tlp in config or tlp value in input data')

# setup proxy
if http_proxy != None:
	os.environ['http_proxy'] = http_proxy
if https_proxy != None:
	os.environ['https_proxy'] = https_proxy

if service == 'query':
	if data_type == 'url' or data_type == 'domain':
		data = get_param('data', None, 'Data is missing')
	        json.dump({
                        'fortinet_category': fortinet_category(data)
                	}, sys.stdout, ensure_ascii=False)
	else:
		error('Invalid data type')
else:
	error('Invalid service')

