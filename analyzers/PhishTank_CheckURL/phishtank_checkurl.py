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
	print >> sys.stderr, msg
	pass

http_proxy     = get_param('config.proxy.http')
https_proxy    = get_param('config.proxy.https')
max_tlp        = get_param('config.max_tlp', 1)
tlp            = get_param('tlp', 2) # amber by default
data_type      = get_param('dataType', None, 'Missing dataType field')
service        = get_param('config.service', None, 'Service parameter is missing')
phishtank_key  = get_param('config.key', None, 'Missing PhishTank API key')

def phishtank_checkurl(data):
	debug('>> phishtank_checkurl ' + str(data))
	url = 'http://checkurl.phishtank.com/checkurl/'
	postdata = {'url': data, 'format':'json','app_key': phishtank_key}
	r = requests.post(url, data=postdata)
	return json.loads(r.content)

# run  only if TLP condition is met
if tlp > max_tlp:
	error('Error with TLP value ; see max_tlp in config or tlp value in input data')

# setup proxy
if http_proxy != None:
	os.environ['http_proxy'] = http_proxy
if https_proxy != None:
	os.environ['https_proxy'] = https_proxy

if service == 'query':
	if data_type == 'url':
		data = get_param('data', None, 'Data is missing')
		r = phishtank_checkurl(data)
		if "success" in r['meta']['status']:
			if r['results']['in_database']:
				if "verified" in r['results']:
					json.dump({
						'in_database': r['results']['in_database'],
						'phish_detail_page': r['results']['phish_detail_page'],
						'verified': r['results']['verified'],
						'verified_at': r['results']['verified_at']
					}, sys.stdout, ensure_ascii=False)
				else:
					json.dump({
							'in_database': r['results']['in_database'],
							'phish_detail_page': r['results']['phish_detail_page']
					}, sys.stdout, ensure_ascii=False)
			else:
				json.dump({
				 'in_database': 'False'
				}, sys.stdout, ensure_ascii=False)
		else:
			json.dump({
					'errortext': r['errortext']
			}, sys.stdout, ensure_ascii=False)
	else:
			error('Invalid data type')
else:
		error('Invalid service')