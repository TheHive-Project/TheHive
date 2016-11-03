#!/usr/bin/python
# encoding: utf-8

# using https://bitbucket.org/decalage/oletools/wiki/olevba 


import sys
import re
import os
import json
import codecs
from StringIO import StringIO


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

# load observable
artifact = json.load(sys.stdin)

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

filename    = get_param('attachmentName', 'noname.ext')
filepath    = get_param('file', None, 'File is missing')
max_tlp     = get_param('config.max_tlp', 10)
tlp         = get_param('tlp', 2)  # amber by default

# run  only if TLP condition is met
if tlp > max_tlp:
    error('Error with TLP value ; see max_tlp in config or tlp value in input data')

try:
    __import__('imp').find_module('oletools')
    from oletools.olevba import VBA_Parser, VBA_Scanner
    from oletools.olevba import __version__ as olevbaVersion
except ImportError:
    result = {'Error': 'Import Error: Module oletools not found'}
    json_data = json.dumps(result)
    print json_data
    sys.exit()

# Redirect stderr to devnull in case input file is not a valid office document. When parsing a non valid 
# document VBA Parser raises an error to stderr. 
redir_err = sys.stderr = StringIO()

try:
    vba = VBA_Parser(filepath)
    result = {'Suspicious': False, 'Base64 Strings': False, 'Hex Strings': False, 'Version': olevbaVersion}
except TypeError:
    result = {'Error': redir_err.getvalue() }
    json_data = json.dumps(result, indent=4)
    print json_data
    sys.exit()

# set stderr back to original __stderr__ 
sys.stderr = sys.__stderr__

if vba.detect_vba_macros():
    result['vba'] = 'VBA Macros found'
    streams = []
    for (filename, stream_path, vba_filename, vba_code) in vba.extract_macros():
        vba_scanner = VBA_Scanner(vba_code)
        scan_results = vba_scanner.scan(include_decoded_strings=False)
        vba_scan_results = []
        for kw_type, keyword, description in scan_results:
            vba_scan_results.append({'type': kw_type, 'keyword': keyword, 'description': description})
            if (kw_type == 'Suspicious'):
                result['Suspicious'] = True
            if (keyword == 'Base64 Strings'):
                result['Base64 Strings'] = True
            if (keyword == 'Hex Strings'):
                result['Hex Strings'] = True

        streams.append({'Filename': filename, 'OLE stream': stream_path, 'VBA filename': vba_filename, 
            'VBA code': vba_code, 'scan_result': vba_scan_results })
    result['streams'] = streams
else:
    result['vba'] =  'No VBA Macros found'

json.dump(result, sys.stdout, sort_keys=True, ensure_ascii=False, indent=4)
