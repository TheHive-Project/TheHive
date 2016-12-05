#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
peHashNG, Portable Executable hash of structural properties

@author: AnyMaster
https://github.com/AnyMaster/pehashng
"""

from __future__ import print_function
import logging
from bz2 import compress
from hashlib import sha256
from struct import pack

from pefile import PE, PEFormatError

__version__ = '1.0.1'
__author__ = 'AnyMaster'


def pehashng(pe_file):
    """ Return pehashng for PE file, sha256 of PE structural properties.

    :param pe_file: file name or instance of pefile.PE() class
    :return: SHA256 in hexdigest format, None in case of pefile.PE() error
    :rtype: str
    """

    if isinstance(pe_file, PE):
        exe = pe_file
    else:
        try:
            exe = PE(pe_file, fast_load=True)
        except PEFormatError as exc:
            logging.error("Exception in pefile.PE('%s') - %s", pe_file, exc)
            return

    def align_down_p2(number):
        return 1 << (number.bit_length() - 1) if number else 0

    def align_up(number, boundary_p2):
        assert not boundary_p2 & (boundary_p2 - 1), \
            "Boundary '%d' is not a power of 2" % boundary_p2
        boundary_p2 -= 1
        return (number + boundary_p2) & ~ boundary_p2

    def get_dirs_status():
        dirs_status = 0
        for idx in range(min(exe.OPTIONAL_HEADER.NumberOfRvaAndSizes, 16)):
            if exe.OPTIONAL_HEADER.DATA_DIRECTORY[idx].VirtualAddress:
                dirs_status |= (1 << idx)
        return dirs_status

    def get_complexity():
        complexity = 0
        if section.SizeOfRawData:
            complexity = (len(compress(section.get_data())) *
                          7.0 /
                          section.SizeOfRawData)
            complexity = 8 if complexity > 7 else int(round(complexity))
        return complexity

    characteristics_mask = 0b0111111100100011
    data_directory_mask = 0b0111111001111111

    data = [
        pack('> H', exe.FILE_HEADER.Characteristics & characteristics_mask),
        pack('> H', exe.OPTIONAL_HEADER.Subsystem),
        pack("> I", align_down_p2(exe.OPTIONAL_HEADER.SectionAlignment)),
        pack("> I", align_down_p2(exe.OPTIONAL_HEADER.FileAlignment)),
        pack("> Q", align_up(exe.OPTIONAL_HEADER.SizeOfStackCommit, 4096)),
        pack("> Q", align_up(exe.OPTIONAL_HEADER.SizeOfHeapCommit, 4096)),
        pack('> H', get_dirs_status() & data_directory_mask)]

    for section in exe.sections:
        data += [
            pack('> I', align_up(section.VirtualAddress, 512)),
            pack('> I', align_up(section.SizeOfRawData, 512)),
            pack('> B', section.Characteristics >> 24),
            pack("> B", get_complexity())]

    if not isinstance(pe_file, PE):
        exe.close()
    data_sha256 = sha256(b"".join(data)).hexdigest()

    return data_sha256


if __name__ == '__main__':
    import sys
    if len(sys.argv) < 2:
        print("Usage: pehashng.py path_to_file")
        sys.exit(0)
    print(pehashng(sys.argv[1]), sys.argv[1])
