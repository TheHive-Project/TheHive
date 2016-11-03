#!/usr/bin/env python
# encoding: utf-8

import sys
import json
import codecs
from lib.msgParser import Message

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


filename = get_param('attachmentName', 'noname.ext')
filepath = get_param('file', None, 'File is missing')
data_type = get_param('dataType', None, 'Missing dataType field')

if data_type == 'file':
    try:
        msg = Message(filepath)
        print msg.JsonDump()
        # print msg.dump()
    except:
        # print("Error with file '" + filepath + "': " + traceback.format_exc())
        error("An unexpected error occurred: " + str(sys.exc_info()[1]))
