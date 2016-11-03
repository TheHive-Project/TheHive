#!/usr/bin/env python
# encoding: utf-8

# --- LICENSE -----------------------------------------------------------------
#
#    Copyright 2013 Matthew Walker
#
#    This program is free software: you can redistribute it and/or modify
#    it under the terms of the GNU General Public License as published by
#    the Free Software Foundation, either version 3 of the License, or
#    (at your option) any later version.
#
#    This program is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU General Public License for more details.
#
#    You should have received a copy of the GNU General Public License
#    along with this program.  If not, see <http://www.gnu.org/licenses/>.

import json
import os
import sys
import glob
import traceback
from email.parser import Parser as EmailParser
import email.utils
import olefile as OleFile


class Attachment:

    def __init__(self, msg, dir_):

        # print dir_

        # Get long filename
        self.longFilename = msg._getStringStream([dir_, '__substg1.0_3707'])
        # print  self.longFilename

        # Get short filename
        self.shortFilename = msg._getStringStream([dir_, '__substg1.0_3704'])

        # Get attachment data
        self.data = msg._getStream([dir_, '__substg1.0_37010102'])

        # Get short mimeTag
        self.mimeTag = msg._getStringStream([dir_, '__substg1.0_370E'])

        # Get extension
        self.extension = msg._getStringStream([dir_, '__substg1.0_3703'])

    def save(self):
        # Use long filename as first preference
        filename = self.longFilename

        # Otherwise use the short filename
        if filename is None:
            filename = self.shortFilename
        # Otherwise just make something up!
        if filename is None:
            import random
            import string
            filename = 'UnknownFilename ' + \
                       ''.join(random.choice(string.ascii_uppercase + string.digits)
                               for _ in range(5)) + ".bin"
            #f = open("/tmp/" + filename, 'wb')
            # if self.data is None:
            #f.write(("Pas de PJ"))
            # f.close()
            # else:
            # f.write((self.data))
            # f.close()
            # return filename


def windowsUnicode(string):
    if string is None:
        return None
    if sys.version_info[0] >= 3:  # Python 3
        return str(string, 'utf_16_le')
    else:  # Python 2
        return unicode(string, 'utf_16_le')


class Message(OleFile.OleFileIO):

    def __init__(self, filename):
        OleFile.OleFileIO.__init__(self, filename)

    def _getStream(self, filename):
        if self.exists(filename):
            stream = self.openstream(filename)
            return stream.read()
        else:
            return None

    def _getStringStream(self, filename, prefer='unicode'):
        """Gets a string representation of the requested filename.
        Checks for both ASCII and Unicode representations and returns
        a value if possible.  If there are both ASCII and Unicode
        versions, then the parameter /prefer/ specifies which will be
        returned.
        """

        if isinstance(filename, list):
            # Join with slashes to make it easier to append the type
            filename = "/".join(filename)

        asciiVersion = self._getStream(filename + '001E')
        unicodeVersion = windowsUnicode(self._getStream(filename + '001F'))
        if asciiVersion is None:
            return unicodeVersion
        elif unicodeVersion is None:
            return asciiVersion.decode('ascii', 'ignore')
        else:
            if prefer == 'unicode':
                return unicodeVersion
            else:
                return asciiVersion.decode('ascii', 'ignore')

    @property
    def subject(self):
        return self._getStringStream('__substg1.0_0037')

    @property
    def header(self):
        try:
            return self._header
        except Exception:
            headerText = self._getStringStream('__substg1.0_007D')
            if headerText is not None:
                self._header = EmailParser().parsestr(headerText)
            else:
                self._header = None
            return self._header

    @property
    def date(self):
        # Get the message's header and extract the date
        if self.header is None:
            return None
        else:
            return self.header['date']

    @property
    def parsedDate(self):
        return email.utils.parsedate(self.date)

    @property
    def attachments(self):
        try:
            return self._attachments
        except Exception:
            # Get the attachments
            attachmentDirs = []

            for dir_ in self.listdir():
                if dir_[0].startswith('__attach') and dir_[0] not in attachmentDirs:
                    attachmentDirs.append(dir_[0])

            self._attachments = []

            for attachmentDir in attachmentDirs:
                self._attachments.append(Attachment(self, attachmentDir))

            return self._attachments

    @property
    def sender(self):
        try:
            return self._sender
        except Exception:
            # Check header first
            if self.header is not None:
                headerResult = self.header["from"]
                if headerResult is not None:
                    self._sender = headerResult
                    return headerResult

            # Extract from other fields
            text = self._getStringStream('__substg1.0_0C1A')
            email = self._getStringStream('__substg1.0_0C1F')
            result = None
            if text is None:
                result = email
            else:
                result = text
                if email is not None:
                    result = result + " <" + email + ">"

            self._sender = result
            return result

    @property
    def to(self):
        try:
            return self._to
        except Exception:
            # Check header first
            if self.header is not None:
                headerResult = self.header["to"]
                if headerResult is not None:
                    self._to = headerResult
                    return headerResult

            # Extract from other fields
            # TODO: This should really extract data from the recip folders,
            # but how do you know which is to/cc/bcc?
            display = self._getStringStream('__substg1.0_0E04')
            self._to = display
            return display

    @property
    def cc(self):
        try:
            return self._cc
        except Exception:
            # Check header first
            if self.header is not None:
                headerResult = self.header["cc"]
                if headerResult is not None:
                    self._cc = headerResult
                    return headerResult

            # Extract from other fields
            # TODO: This should really extract data from the recip folders,
            # but how do you know which is to/cc/bcc?
            display = self._getStringStream('__substg1.0_0E03')
            self._cc = display
            return display

    @property
    def body(self):
        return self._getStringStream('__substg1.0_1000')

    @property
    def sujet(self):
        return self._getStringStream('__substg1.0_0037')

    @property
    def recupar(self):
        return self._getStringStream('__substg1.0_0040')

    @property
    def nomaffichefrom(self):
        return self._getStringStream('__substg1.0_0042')

    @property
    def Recupar(self):
        return self._getStringStream('__substg1.0_0044')

    @property
    def Lesender(self):
        return self._getStringStream('__substg1.0_0065')

    @property
    def lobjet(self):
        return self._getStringStream('__substg1.0_0070')

    @property
    def lentete(self):
        return self._getStringStream('__substg1.0_007d')

    @property
    def bcc(self):
        return self._getStringStream('__substg1.0_0E02')

    @property
    def displayto(self):
        return self._getStringStream('__substg1.0_0E04')

    def dump(self):
        # Prints out a summary of the message
        print('Message')
        print('Subject:', self.subject)
        print('Date:', self.date)
        print('Body:')
        print(self.body)
        print('Recu par: ', self.recupar)
        print('Nom affiche dans le from: %s' % self.nomaffichefrom)
        print('Le sender: ', self.Lesender)
        print('lobjet: ', self.lobjet)
        print('lentete: ', self.lentete)
        print('bcc: ', self.bcc)
        print('display to: ', self.displayto)

    def JsonDump(self):
        result = {"subject": self.subject, "date": self.date, "receivers": self.recupar, "displayFrom": self.nomaffichefrom,
                  "sender": self.Lesender, "topic": self.lobjet, "bcc": self.bcc, "displayTo": self.displayto,
                  "headers": self.lentete, "body": self.body}

        attachments = []
        for attachment in self.attachments:
            attachments.append({"filename": attachment.longFilename,
                                "mime": attachment.mimeTag, "extension": attachment.extension})


        result["attachments"] = attachments

        return json.dumps(result, sort_keys=True, ensure_ascii=False)
