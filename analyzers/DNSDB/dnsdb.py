#!/usr/bin/env python
# encoding: utf-8
import sys
import os
import json
import codecs
import datetime
from urllib2 import HTTPError
from dnsdb_query import DnsdbClient, QueryError

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

def execute_dnsdb_service(client, service_name, data_type, data):
	if service_name == 'domain_name' and data_type == 'domain':
		return client.query_rrset(data)
	elif service_name == 'ip_history' and data_type == 'ip':
		return client.query_rdata_ip(data)
	elif service_name == 'name_history' and data_type == 'fqdn':
		return client.query_rdata_name(data)
	else:
		error('Unknown DNSDB service or invalid data type')

def update_date(field, row):
	if field in row:
		row[field] = datetime.datetime.utcfromtimestamp(row[field]).strftime('%Y%m%dT%H%M%S')+'+0000'
	return row

http_proxy     = get_param('config.proxy.http')
https_proxy    = get_param('config.proxy.https')
max_tlp        = get_param('config.max_tlp', 2)
tlp            = get_param('tlp', 2) # amber by default
data_type      = get_param('dataType', None, 'Missing dataType field')
data           = get_param('data', None, 'Missing data field')
service        = get_param('config.service', None, 'Service parameter is missing')
dnsdb_server   = get_param('config.server', None, 'Missing DNSDB server name')
dnsdb_key      = get_param('config.key', None, 'Missing DNSDB API key')

# run  only if TLP condition is met
if tlp > max_tlp:
	error('Error with TLP value ; see max_tlp in config or tlp value in input data')
	
# setup proxy
if http_proxy != None:
	os.environ['http_proxy'] = http_proxy
if https_proxy != None:
	os.environ['https_proxy'] = https_proxy

try:
	client    = DnsdbClient(dnsdb_server, dnsdb_key)
	response  = { "records": map(lambda r: update_date('time_first', update_date('time_last', r)), execute_dnsdb_service(client, service, data_type, data)) }

except HTTPError, e:
	if e.code != 404:
		raise e
	response = {} # if no result return empty response.

json.dump(response, sys.stdout, ensure_ascii=False)