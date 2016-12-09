#!/usr/bin/env python
# encoding: utf-8
import sys
import os
import json
import codecs
#from threading import Timer
import time
import requests
import urllib

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

http_proxy     = get_param('config.proxy.http')
https_proxy    = get_param('config.proxy.https')
max_tlp        = get_param('config.max_tlp', 1)
tlp            = get_param('tlp', 2) # amber by default
data_type      = get_param('dataType', None, 'Missing dataType field')
service        = get_param('config.service', None, 'Service parameter is missing')
otx_key        = get_param('config.key', None, 'Missing OTX API key')

def OTX_Query_IP(data):
	debug('>> OTX_Query_IP ' + str(data))
	baseurl = "https://otx.alienvault.com:443/api/v1/indicators/IPv4/%s/" % data
	headers = {'X-OTX-API-KEY': otx_key, 'Accept':'application/json'}
	sections = ['general','reputation','geo','malware','url_list','passive_dns']
	IP_={}
	try:
		for section in sections:
			queryurl = baseurl + section
			IP_[section] = json.loads(requests.get(queryurl, headers=headers).content)
	except:
		error('API Error! Please verify data type is correct.')

	json.dump({
		'pulse_count': IP_['general']['pulse_info']['count'],
		'pulses': IP_['general']['pulse_info']['pulses'],
		'whois': IP_['general']['whois'],
		'continent_code': IP_['geo']['continent_code'],
		'country_code': IP_['geo']['country_code'],
		'country_name': IP_['geo']['country_name'],
		'city': IP_['geo']['city'],
		'longitude': IP_['general']['longitude'],
		'latitude': IP_['general']['latitude'],
		'asn': IP_['geo']['asn'],
		'malware_samples': IP_['malware']['result'],
		'url_list': IP_['url_list']['url_list'],
		'passive_dns': IP_['passive_dns']['passive_dns']
	}, sys.stdout, ensure_ascii=False)


def OTX_Query_Domain(data):
	debug('>> OTX_Query_Domain ' + str(data))
	baseurl = "https://otx.alienvault.com:443/api/v1/indicators/domain/%s/" % data
	headers = {'X-OTX-API-KEY': otx_key, 'Accept':'application/json'}
	sections = ['general','geo','malware','url_list','passive_dns']
	IP_={}
	try:
		for section in sections:
			queryurl = baseurl + section
			IP_[section] = json.loads(requests.get(queryurl, headers=headers).content)
	except:
		error('API Error! Please verify data type is correct.')

	json.dump({
		'pulse_count': IP_['general']['pulse_info']['count'],
		'pulses': IP_['general']['pulse_info']['pulses'],
		'whois': IP_['general']['whois'],
		'continent_code': IP_['geo']['continent_code'],
		'country_code': IP_['geo']['country_code'],
		'country_name': IP_['geo']['country_name'],
		'city': IP_['geo']['city'],
		'asn': IP_['geo']['asn'],
		'malware_samples': IP_['malware']['result'],
		'url_list': IP_['url_list']['url_list'],
		'passive_dns': IP_['passive_dns']['passive_dns']
	}, sys.stdout, ensure_ascii=False)


def OTX_Query_File(data):
	debug('>> OTX_Query_File ' + str(data))
	baseurl = "https://otx.alienvault.com:443/api/v1/indicators/file/%s/" % data
	headers = {'X-OTX-API-KEY': otx_key, 'Accept':'application/json'}
	sections = ['general','analysis']
	IP_={}
	try:
		for section in sections:
			queryurl = baseurl + section
			IP_[section] = json.loads(requests.get(queryurl, headers=headers).content)
	except:
		error('API Error! Please verify data type is correct.')

	
	if IP_['analysis']['analysis']: # file has been analyzed before
		json.dump({
			'pulse_count': IP_['general']['pulse_info']['count'],
			'pulses': IP_['general']['pulse_info']['pulses'],
			'malware': IP_['analysis']['malware'],
			'page_type': IP_['analysis']['page_type'],
			'sha1': IP_['analysis']['analysis']['info']['results']['sha1'],
			'sha256': IP_['analysis']['analysis']['info']['results']['sha256'],
			'md5': IP_['analysis']['analysis']['info']['results']['md5'],
			'file_class': IP_['analysis']['analysis']['info']['results']['file_class'],
			'file_type': IP_['analysis']['analysis']['info']['results']['file_type'],
			'filesize': IP_['analysis']['analysis']['info']['results']['filesize'],
			'ssdeep': IP_['analysis']['analysis']['info']['results']['ssdeep']
		}, sys.stdout, ensure_ascii=False)
	else: # file has not been analyzed before
		json.dump({
			'errortext': 'File has not previously been analyzed by OTX!',
			'pulse_count': IP_['general']['pulse_info']['count'],
			'pulses': IP_['general']['pulse_info']['pulses']
		}, sys.stdout, ensure_ascii=False)


def OTX_Query_URL(data):
	debug('>> OTX_Query_URL ' + str(data))
	data = urllib.quote_plus(data) # urlencode the URL that we are searching for
	baseurl = "https://otx.alienvault.com:443/api/v1/indicators/url/%s/" % data
	headers = {'X-OTX-API-KEY': otx_key, 'Accept':'application/json'}
	sections = ['general','url_list']
	IP_={}
	try:
		for section in sections:
			queryurl = baseurl + section
			IP_[section] = json.loads(requests.get(queryurl, headers=headers).content)
	except:
		error('API Error! Please verify data type is correct.')

	json.dump({
		'pulse_count': IP_['general']['pulse_info']['count'],
		'pulses': IP_['general']['pulse_info']['pulses'],
		'alexa': IP_['general']['alexa'],
		'whois': IP_['general']['whois'],
		'url_list': IP_['url_list']['url_list']
	}, sys.stdout, ensure_ascii=False)


# run  only if TLP condition is met
if tlp > max_tlp:
	error('Error with TLP value ; see max_tlp in config or tlp value in input data')
	
# setup proxy
if http_proxy != None:
	os.environ['http_proxy'] = http_proxy
if https_proxy != None:
	os.environ['https_proxy'] = https_proxy


if service == 'query':
	if data_type == 'file':
		hashes = get_param('attachment.hashes', None, 'Hash is missing')
		# find SHA256 hash
		hash = next(h for h in hashes if len(h) == 64)
		OTX_Query_File(hash)
	elif data_type == 'url':
		data = get_param('data', None, 'Data is missing')
		OTX_Query_URL(data)
	elif data_type == 'domain':
		data = get_param('data', None, 'Data is missing')
		OTX_Query_Domain(data)
	elif data_type == 'ip':
		data = get_param('data', None, 'Data is missing')
		OTX_Query_IP(data)
	elif data_type == 'hash':
		data = get_param('data', None, 'Data is missing')
		OTX_Query_File(data)
	else:
		error('Invalid data type')
else:
	error('Invalid service')
