#!/usr/bin/env python

# Copyright (c) 2013 by Farsight Security, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import calendar
import errno
import locale
import optparse
import os
import re
import sys
import time
import urllib
import urllib2
from cStringIO import StringIO

try:
    import json
except ImportError:
    import simplejson as json

DEFAULT_CONFIG_FILE = '/etc/dnsdb-query.conf'
DEFAULT_DNSDB_SERVER = 'https://api.dnsdb.info'

cfg = None
options = None

locale.setlocale(locale.LC_ALL, '')

class QueryError(Exception):
    pass

class DnsdbClient(object):
    def __init__(self, server, apikey, limit=None):
        self.server = server
        self.apikey = apikey
        self.limit = limit

    def query_rrset(self, oname, rrtype=None, bailiwick=None, before=None, after=None):
        if bailiwick:
            if not rrtype:
                rrtype = 'ANY'
            path = 'rrset/name/%s/%s/%s' % (quote(oname), rrtype, quote(bailiwick))
        elif rrtype:
            path = 'rrset/name/%s/%s' % (quote(oname), rrtype)
        else:
            path = 'rrset/name/%s' % quote(oname)
        return self._query(path, before, after)

    def query_rdata_name(self, rdata_name, rrtype=None, before=None, after=None):
        if rrtype:
            path = 'rdata/name/%s/%s' % (quote(rdata_name), rrtype)
        else:
            path = 'rdata/name/%s' % quote(rdata_name)
        return self._query(path, before, after)

    def query_rdata_ip(self, rdata_ip, before=None, after=None):
        path = 'rdata/ip/%s' % rdata_ip.replace('/', ',')
        return self._query(path, before, after)

    def _query(self, path, before=None, after=None):
        res = []
        url = '%s/lookup/%s' % (self.server, path)

        params = {}
        if self.limit:
            params['limit'] = self.limit
        if before and after:
            params['time_first_after'] = after
            params['time_last_before'] = before
        else:
            if before:
                params['time_first_before'] = before
            if after:
                params['time_last_after'] = after
        if params:
            url += '?{0}'.format(urllib.urlencode(params))

        req = urllib2.Request(url)
        req.add_header('Accept', 'application/json')
        req.add_header('X-Api-Key', self.apikey)
        http = urllib2.urlopen(req)
        while True:
            line = http.readline()
            if not line:
                break
            yield json.loads(line)

def quote(path):
    return urllib.quote(path, safe='')

def sec_to_text(ts):
    return time.strftime('%Y-%m-%d %H:%M:%S -0000', time.gmtime(ts))

def rrset_to_text(m):
    s = StringIO()

    if 'bailiwick' in m:
        s.write(';;  bailiwick: %s\n' % m['bailiwick'])

    if 'count' in m:
        s.write(';;      count: %s\n' % locale.format('%d', m['count'], True))

    if 'time_first' in m:
        s.write(';; first seen: %s\n' % sec_to_text(m['time_first']))
    if 'time_last' in m:
        s.write(';;  last seen: %s\n' % sec_to_text(m['time_last']))

    if 'zone_time_first' in m:
        s.write(';; first seen in zone file: %s\n' % sec_to_text(m['zone_time_first']))
    if 'zone_time_last' in m:
        s.write(';;  last seen in zone file: %s\n' % sec_to_text(m['zone_time_last']))

    if 'rdata' in m:
        for rdata in m['rdata']:
            s.write('%s IN %s %s\n' % (m['rrname'], m['rrtype'], rdata))

    s.seek(0)
    return s.read()

def rdata_to_text(m):
    return '%s IN %s %s' % (m['rrname'], m['rrtype'], m['rdata'])

def parse_config(cfg_fname):
    config = {}
    cfg_files = filter(os.path.isfile,
            (cfg_fname, os.path.expanduser('~/.dnsdb-query.conf')))

    if not cfg_files:
        raise IOError(errno.ENOENT, 'dnsdb_query: No config files found')

    for fname in cfg_files:
        for line in open(fname):
            key, eq, val = line.strip().partition('=')
            val = val.strip('"')
            config[key] = val

    return config

def time_parse(s):
    try:
        epoch = int(s)
        return epoch
    except ValueError:
        pass

    try:
        epoch = int(calendar.timegm(time.strptime(s, '%Y-%m-%d')))
        return epoch
    except ValueError:
        pass

    try:
        epoch = int(calendar.timegm(time.strptime(s, '%Y-%m-%d %H:%M:%S')))
        return epoch
    except ValueError:
        pass

    m = re.match(r'^(?=\d)(?:(\d+)w)?(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s?)?$', s, re.I)
    if m:
        return -1*(int(m.group(1) or 0)*604800 +  \
                int(m.group(2) or 0)*86400+  \
                int(m.group(3) or 0)*3600+  \
                int(m.group(4) or 0)*60+  \
                int(m.group(5) or 0))

    raise ValueError('Invalid time: "%s"' % s)

def main():
    global cfg
    global options

    parser = optparse.OptionParser(epilog='Time formats are: "%Y-%m-%d", "%Y-%m-%d %H:%M:%S", "%d" (UNIX timestamp), "-%d" (Relative time in seconds), BIND format (e.g. 1w1h, (w)eek, (d)ay, (h)our, (m)inute, (s)econd)')
    parser.add_option('-c', '--config', dest='config', type='string',
        help='config file', default=DEFAULT_CONFIG_FILE)
    parser.add_option('-r', '--rrset', dest='rrset', type='string',
        help='rrset <ONAME>[/<RRTYPE>[/BAILIWICK]]')
    parser.add_option('-n', '--rdataname', dest='rdata_name', type='string',
        help='rdata name <NAME>[/<RRTYPE>]')
    parser.add_option('-i', '--rdataip', dest='rdata_ip', type='string',
        help='rdata ip <IPADDRESS|IPRANGE|IPNETWORK>')
    parser.add_option('-t', '--rrtype', dest='rrtype', type='string',
        help='rrset or rdata rrtype')
    parser.add_option('-b', '--bailiwick', dest='bailiwick', type='string',
        help='rrset bailiwick')
    parser.add_option('-s', '--sort', dest='sort', type='string', help='sort key')
    parser.add_option('-R', '--reverse', dest='reverse', action='store_true', default=False,
        help='reverse sort')
    parser.add_option('-j', '--json', dest='json', action='store_true', default=False,
        help='output in JSON format')
    parser.add_option('-l', '--limit', dest='limit', type='int', default=0,
        help='limit number of results')

    parser.add_option('', '--before', dest='before', type='string', help='only output results seen before this time')
    parser.add_option('', '--after', dest='after', type='string', help='only output results seen after this time')

    options, args = parser.parse_args()
    if args:
        parser.print_help()
        sys.exit(1)

    try:
        if options.before:
            options.before = time_parse(options.before)
    except ValueError, e:
        print 'Could not parse before: {}'.format(options.before)

    try:
        if options.after:
            options.after = time_parse(options.after)
    except ValueError, e:
        print 'Could not parse after: {}'.format(options.after)

    try:
        cfg = parse_config(options.config)
    except IOError, e:
        sys.stderr.write(e.message)
        sys.exit(1)


    if not 'DNSDB_SERVER' in cfg:
        cfg['DNSDB_SERVER'] = DEFAULT_DNSDB_SERVER
    if not 'APIKEY' in cfg:
        sys.stderr.write('dnsdb_query: APIKEY not defined in config file\n')
        sys.exit(1)

    client = DnsdbClient(cfg['DNSDB_SERVER'], cfg['APIKEY'], options.limit)
    if options.rrset:
        if options.rrtype or options.bailiwick:
            qargs = (options.rrset, options.rrtype, options.bailiwick)
        else:
            qargs = (options.rrset.split('/', 2))

        results = client.query_rrset(*qargs, before=options.before, after=options.after)
        fmt_func = rrset_to_text
    elif options.rdata_name:
        if options.rrtype:
            qargs = (options.rdata_name, options.rrtype, options.bailiwick)
        else:
            qargs = (options.rdata_name.split('/', 1))

        results = client.query_rdata_name(*qargs, before=options.before, after=options.after)
        fmt_func = rdata_to_text
    elif options.rdata_ip:
        results = client.query_rdata_ip(options.rdata_ip, before=options.before, after=options.after)
        fmt_func = rdata_to_text
    else:
        parser.print_help()
        sys.exit(1)

    if options.json:
        fmt_func = json.dumps

    try:
        if options.sort:
            results = list(results)
            if len(results) > 0:
                if not options.sort in results[0]:
                    sort_keys = results[0].keys()
                    sort_keys.sort()
                    sys.stderr.write('dnsdb_query: invalid sort key "%s". valid sort keys are %s\n' % (options.sort, ', '.join(sort_keys)))
                    sys.exit(1)
                results.sort(key=lambda r: r[options.sort], reverse=options.reverse)
        for res in results:
            sys.stdout.write('%s\n' % fmt_func(res))
    except (urllib2.HTTPError, urllib2.URLError), e:
        print >>sys.stderr, str(e)
        sys.exit(1)

if __name__ == '__main__':
    main()
