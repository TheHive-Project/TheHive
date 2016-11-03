#!/usr/bin/env python
# encoding: utf-8
import sys
import json
import geoip2.database
import codecs

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

def dumpCity(city):
	return {
		'confidence': city.confidence,
		'geoname_id': city.geoname_id,
		'name': city.name,
		'names': city.names
	}

def dumpContinent(continent):
	return {
		'code': continent.code,
		'geoname_id': continent.geoname_id,
		'name': continent.name,
		'names': continent.names,
	}

def dumpCountry(country):
	return {
		'confidence': country.confidence,
		'geoname_id': country.geoname_id,
		'iso_code': country.iso_code,
		'name': country.name,
		'names': country.names
	}

def dumpLocation(location):
	return {
		'accuracy_radius': location.accuracy_radius,
		'latitude': location.latitude,
		'longitude': location.longitude,
		'metro_code': location.metro_code,
		'time_zone': location.time_zone
	}

def dumpTraits(traits):
	return {
		'autonomous_system_number': traits.autonomous_system_number,
		'autonomous_system_organization': traits.autonomous_system_organization,
		'domain': traits.domain,
		'ip_address': traits.ip_address,
		'is_anonymous_proxy': traits.is_anonymous_proxy,
		'is_satellite_provider': traits.is_satellite_provider,
		'isp': traits.isp,
		'organization': traits.organization,
		'user_type': traits.user_type
	}

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
				print(message)
				sys.exit(1)
			else:
				return default
		else:
			return get_param(name[1:], default, message, value)

data_type   = get_param('dataType', None, 'Missing dataType field')
data        = get_param('data', None, 'Missing data field')
max_tlp     = get_param('config.max_tlp', 10)
tlp         = get_param('tlp', 2)  # amber by default

# run  only if TLP condition is met
if tlp > max_tlp:
	error('Error with TLP value ; see max_tlp in config or tlp value in input data')


if data_type == 'ip':
	city = geoip2.database.Reader('GeoLite2-City.mmdb').city(data)
	country = geoip2.database.Reader('GeoLite2-Country.mmdb').country(data)
	json.dump({
			'city': dumpCity(city.city),
			'continent': dumpContinent(city.continent),
			'country': dumpCountry(city.country),
			'location': dumpLocation(city.location),
			'registered_country': dumpCountry(city.registered_country),
			'represented_country': dumpCountry(city.represented_country), # TODO add 'type' field ?
			'subdivisions': dumpCountry(city.subdivisions.most_specific),
			'traits': dumpTraits(city.traits)
		}, sys.stdout, ensure_ascii=False)
