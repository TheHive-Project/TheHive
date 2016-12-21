#!/usr/local/bin/python
# encoding: utf-8

# using https://github.com/erocarrera/pefile


import sys
import json
import codecs
from  lib.File_analysis import file


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

result={}

def error(message):
    res={}
    res['errorMessage']="{}".format(message)
    json_data = json.dumps(res,indent=4)
    print json_data
    sys.exit(1)

try:
    __import__('imp').find_module('magic')
except ImportError:
    error('Import Error: Module magic not found')

try:
    __import__('imp').find_module('pefile')
except ImportError:
    error('Import Error: Module pefile not found')

try:
    __import__('imp').find_module('hashlib')
except ImportError:
    error('Import Error: Module hashlib not found')

try:
    __import__('imp').find_module('pydeep')
except ImportError:
    error('Import Error: Module pydeep not found')

try:
    __import__('imp').find_module('pyexifinfo')
except ImportError:
    error('Import Error: Module pyexifinfo not found')

# set stderr back to original __stderr__
sys.stderr = sys.__stderr__


# Associate Mimetype and analyzer (every result has a result['Mimetype']
def FileInfo():
    f = file(filepath)
    try:
        result['Mimetype'] = f.mimetype()
    except Exception as excp:
        error(str(excp))
    result['Exif'] = f.exif()
    result['Magic']= f.magic()
    result['Identification']= {'MD5': f.md5(),
                            'SHA1':f.sha1(),
                            'SHA256':f.sha256(),
                            'ssdeep':f.ssdeep()}


# PE_Info analyzer
def PE_info():
    f = file(filepath)
    result['Identification'].update({'impash':f.imphash(),
                            'ssdeep':f.ssdeep(),
                            'pehash':f.pehash(),
                            'OperatingSystem':f.OperatingSystem(),
                            'Type':f.PEtype()})

    result['BasicInformation'] = {'FileInfo':f.info(),
                              'FileSize': f.filesize(),
                              'TargetMachine' : f.Machine(),
                              'CompilationTimestamp' : f.CompilationTimestamp(),
                              'EntryPoint':f.EntryPoint()}

    result['Sections'] = f.sections()
    result['ImportAdressTable'] = f.iat()



def SpecificInfo():
    if result['Mimetype'] in ['application/x-dosexec']:
        PE_info()



def dump_result(res):
    if type(res['Mimetype']):
        # if res['Mimetype'] == 'application/x-dosexec':
        #     json.dump(res, sys.stdout, sort_keys=True,ensure_ascii=False, indent=4)
        json.dump(res, sys.stdout, sort_keys=True,ensure_ascii=False, indent=4)
        
    else:
        error(res)





def main():
    FileInfo()
    SpecificInfo()
    dump_result(result)

if __name__ == '__main__':
    main()
