"""Portable parser/refusal checks; real procfs acceptance is a separate native gate."""
import importlib.util
from pathlib import Path
import socket
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
spec = importlib.util.spec_from_file_location('native_retirement_owner', Path(__file__).resolve().parents[1] / 'native_retirement_owner.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
BOOT = '00000000-0000-4000-8000-000000000001'
SERVER = ('127.0.0.1', 12345)
CLIENT = ('127.0.0.1', 54321)


def process(*, pid=4321, name='normal', state='S', ticks='123456'):
    fields = [state] + ['0'] * 18 + [ticks] + ['0'] * 4
    return f'{pid} ({name}) ' + ' '.join(fields)


def tcp(*, local=SERVER, remote=CLIENT, state='01', uid='1000', inode='987654'):
    return '  0: ' + m._endpoint(local) + ' ' + m._endpoint(remote) + ' ' + state + ' 00000000:00000000 00:00000000 00000000 ' + uid + ' 0 ' + inode + ' 1'


class NativeRetirementOwnerTests(unittest.TestCase):
    def test_process_start_ticks_and_boot_identity_are_preserved(self):
        self.assertEqual(m._incarnation(process(), BOOT + '\n', 4321), m.Owner(4321, 123456, BOOT))

    def test_process_names_with_spaces_and_closing_parentheses_do_not_shift_ticks(self):
        self.assertEqual(m._incarnation(process(name='odd ) ( name'), BOOT, 4321).start_ticks, 123456)

    def test_wrong_pid_boolean_zero_and_hidden_start_ticks_refuse(self):
        for text, pid in [(process(pid=4322), 4321), (process(), True), (process(ticks='0'), 4321),
                          (process(ticks='-1'), 4321), (process(ticks='True'), 4321)]:
            with self.subTest(pid=pid), self.assertRaises(ValueError):
                m._incarnation(text, BOOT, pid)

    def test_dead_truncated_oversized_and_invalid_boot_records_refuse(self):
        for text, boot in [(process(state='Z'), BOOT), (process(state='X'), BOOT), ('4321 (short) S', BOOT),
                           ('x' * 8193, BOOT), (process(), 'not-a-boot-id'), (process(), 'x' * 65)]:
            with self.subTest(boot=boot), self.assertRaises(ValueError):
                m._incarnation(text, boot, 4321)

    def test_exact_established_reverse_tuple_and_user_resolves_one_inode(self):
        self.assertEqual(m._reverse_inode('header\n' + tcp(), SERVER, CLIENT, 1000), 987654)

    def test_same_listener_port_wrong_peer_wrong_direction_and_user_do_not_prove_owner(self):
        for row in [tcp(remote=('127.0.0.1', 54322)), tcp(local=CLIENT, remote=SERVER), tcp(uid='1001')]:
            with self.subTest(row=row):
                self.assertIsNone(m._reverse_inode('header\n' + row, SERVER, CLIENT, 1000))

    def test_listening_time_wait_and_close_wait_are_not_established_connections(self):
        for state in ['0A', '06', '08', '02']:
            with self.subTest(state=state):
                self.assertIsNone(m._reverse_inode('header\n' + tcp(state=state), SERVER, CLIENT, 1000))

    def test_duplicate_matching_records_are_indeterminate_even_if_inode_is_equal(self):
        self.assertIsNone(m._reverse_inode('header\n' + tcp() + '\n' + tcp(), SERVER, CLIENT, 1000))

    def test_unaccepted_zero_inode_and_malformed_matching_fields_refuse(self):
        for row in [tcp(inode='0'), tcp(inode='-1'), tcp(uid='invalid')]:
            with self.subTest(row=row), self.assertRaises(ValueError):
                m._reverse_inode('header\n' + row, SERVER, CLIENT, 1000)

    def test_nonloopback_and_invalid_endpoint_ports_refuse(self):
        for endpoint in [('192.0.2.1', 123), ('127.0.0.2', 123), ('::1', 123), ('127.0.0.1', True), ('127.0.0.1', 0), ('127.0.0.1', 65536)]:
            with self.subTest(endpoint=endpoint), self.assertRaises(ValueError):
                m._endpoint(endpoint)

    def test_table_size_and_user_type_are_bounded(self):
        for table, uid in [('x' * (4 * 1024 * 1024 + 1), 1000), ('header\n' + tcp(), True), ('header', -1)]:
            with self.subTest(uid=uid), self.assertRaises(ValueError):
                m._reverse_inode(table, SERVER, CLIENT, uid)

    def test_native_entrypoints_do_not_accept_invalid_or_portable_identity(self):
        for value in [True, 0, -1, '4321', None]:
            self.assertIsNone(m.read_process_owner(value))
        with self.assertRaises(m.Refused):
            m.LinuxSocketOwner(None)
        if sys.platform != 'linux':
            self.assertIsNone(m.read_process_owner(4321))
            with self.assertRaises(m.Refused):
                m.LinuxSocketOwner(m.Owner(4321, 123456, BOOT))


if __name__ == '__main__':
    unittest.main()
