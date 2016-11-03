import sys

# pylint: skip-file

is_py2 = sys.version_info[0] == 2

is_py3_3_or_better = (
    sys.version_info[0] >= 3 and sys.version_info[1] >= 3)

if is_py2 and not is_py3_3_or_better:
    import ipaddr as ipaddress  # pylint:disable=F0401
    ipaddress.ip_address = ipaddress.IPAddress
else:
    import ipaddress  # pylint:disable=F0401


if is_py2:
    int_from_byte = ord

    FileNotFoundError = IOError

    def int_from_bytes(b):
        if b:
            return int(b.encode("hex"), 16)
        return 0

    byte_from_int = chr

else:
    int_from_byte = lambda x: x

    FileNotFoundError = FileNotFoundError

    int_from_bytes = lambda x: int.from_bytes(x, 'big')

    byte_from_int = lambda x: bytes([x])
