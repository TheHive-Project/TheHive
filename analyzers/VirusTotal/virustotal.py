#!/usr/bin/env python
# encoding: utf-8
import sys
import os
import json
import codecs
#from threading import Timer
import time
from virustotal_api import PublicApi as VirusTotalPublicApi

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

def wait_file_report(id):
	debug('>> wait_file_report ' + str(id))
	results = check_response(vt.get_file_report(id))
	code = results.get('response_code', None)
	if code == 1:
		json.dump(results, sys.stdout, ensure_ascii=False)
	else:
		#Timer(10, wait_file_report, id)
		time.sleep(10)
		wait_file_report(id)

def wait_url_report(id):
	debug('>> wait_url_report ' + str(id))
	results = check_response(vt.get_url_report(id))
	code = results.get('response_code', None)
	if code == 1:
		json.dump(results, sys.stdout, ensure_ascii=False)
	else:
		#Timer(10, wait_file_report, id)
		time.sleep(60)
		wait_url_report(id)

def check_response(response):
	debug('>> check_response ' + str(response))
	if type(response) is not dict:
		error('bad response : ' + str(response))
	status = response.get('response_code', -1)
	if status != 200:
		error('bad status : ' + str(status))
	results = response.get('results', {})
	if 'verbose_msg' in results:
		print >> sys.stderr, str(results.get('verbose_msg'))
	return results

	# 0 => not found
	# -2 => in queue
	# 1 => ready

def read_scan_response(response, func):
	debug('>> read_scan_response ' + str(response))
	results = check_response(response)
	code = results.get('response_code', None)
	scan_id = results.get('scan_id', None)
	if code == 1 and scan_id != None:
		func(scan_id)
	else:
		error('Scan not found')

http_proxy     = get_param('config.proxy.http')
https_proxy    = get_param('config.proxy.https')
max_tlp        = get_param('config.max_tlp', 1)
tlp            = get_param('tlp', 2) # amber by default
data_type      = get_param('dataType', None, 'Missing dataType field')
service        = get_param('config.service', None, 'Service parameter is missing')
virustotal_key = get_param('config.key', None, 'Missing VirusTotal API key')

# run  only if TLP condition is met
if tlp > max_tlp:
	error('Error with TLP value ; see max_tlp in config or tlp value in input data')
	
# setup proxy
if http_proxy != None:
	os.environ['http_proxy'] = http_proxy
if https_proxy != None:
	os.environ['https_proxy'] = https_proxy

vt = VirusTotalPublicApi(virustotal_key)

if service == 'scan':
	if data_type == 'file':
		filename = get_param('attachment.name', 'noname.ext')
		filepath = get_param('file', None, 'File is missing')
		read_scan_response(vt.scan_file((filename, open(filepath, 'rb'))), wait_file_report)
	elif data_type == 'url':
		data = get_param('data', None, 'Data is missing')
		read_scan_response(vt.scan_url(data), wait_url_report)
	else:
		error('Invalid data type')
elif service == 'get':
	if data_type == 'domain':
		data = get_param('data', None, 'Data is missing')
		json.dump(check_response(vt.get_domain_report(data)), sys.stdout, ensure_ascii=False)
	elif data_type == 'ip':
		data = get_param('data', None, 'Data is missing')
		json.dump(check_response(vt.get_ip_report(data)), sys.stdout, ensure_ascii=False)
	elif data_type == 'file':
		hashes = get_param('attachment.hashes', None, 'Hash is missing')
		# find SHA256 hash
		hash = next(h for h in hashes if len(h) == 64)
		json.dump(check_response(vt.get_file_report(hash)), sys.stdout, ensure_ascii=False)
	elif data_type == 'hash':
		data = get_param('data', None, 'Data is missing')
		json.dump(check_response(vt.get_file_report(data)), sys.stdout, ensure_ascii=False)
	else:
		error('Invalid data type')
else:
	error('Invalid service')
