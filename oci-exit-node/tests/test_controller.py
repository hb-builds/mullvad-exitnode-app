import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module
control = load('control_test', SCRIPTS / 'control.py')
original_spec = importlib.util.spec_from_file_location
with patch('importlib.util.spec_from_file_location', side_effect=lambda name,path: original_spec(name, SCRIPTS/'control.py' if name=='control' else path)):
    broker = load('broker_test', SCRIPTS / 'broker.py')

class ControllerTests(unittest.TestCase):
    def test_filter_masks_reject_bool_float_and_extra_bits(self):
        for value in (True, False, 1.5, '7', -1, 8, 63, None):
            with self.assertRaises(ValueError): control.validate_mask(value)
        self.assertEqual([control.validate_mask(i) for i in range(8)], list(range(8)))

    def test_no_arbitrary_commands_or_extra_fields(self):
        for command in ({'operation':'shell','command':'id'}, {'operation':'state','extra':1}, [], None):
            with self.assertRaises(ValueError): broker.dispatch(command)

    def test_change_validation(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); (root/'profiles').mkdir(); (root/'profiles/us-nyc-wg-001.conf').touch()
            with patch.object(broker.control,'ROOT',root):
                base={'request_id':'a'*36,'kind':'location','value':'us-nyc'}
                broker.validate_change(base)
                for value in ('../../etc/passwd','us; id','zz','us\n',None,7):
                    with self.assertRaises(ValueError): broker.validate_change({**base,'value':value})
                with self.assertRaises(ValueError): broker.validate_change({**base,'extra':True})

    def test_duplicate_request_is_not_applied_twice(self):
        broker.history.clear(); broker.job=None
        change={'request_id':'b'*36,'kind':'dns','value':7}
        with patch.object(broker.threading,'Thread') as thread:
            first=broker.dispatch({'operation':'change','change':change})
            second=broker.dispatch({'operation':'change','change':change})
            self.assertEqual(first,second); self.assertEqual(thread.call_count,1)
            busy=broker.dispatch({'operation':'change','change':{**change,'request_id':'c'*36}})
            self.assertTrue(busy['busy'])
            with self.assertRaises(ValueError): broker.dispatch({'operation':'change','change':{**change,'value':0}})
        broker.job=None

    def test_failed_dns_does_not_persist_new_mask(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); (root/'dns-mask').write_text('7\n')
            # Replace the lockfile opening only; keep the real state reads/writes.
            real_open=open
            def lock_open(path,*args,**kwargs):
                return real_open(root/'lock' if path=='/run/lock/mullvad-exit-switch.lock' else path,*args,**kwargs)
            with patch.object(control,'ROOT',root), patch('builtins.open',side_effect=lock_open), patch.object(control,'apply_dns') as apply, patch.object(control,'run',return_value='status: SERVFAIL'):
                with self.assertRaises(RuntimeError): control.set_dns(0)
                self.assertEqual([call.args[0] for call in apply.call_args_list],[0,7])
                self.assertEqual((root/'dns-mask').read_text(),'7\n')

    def test_successful_dns_persists_checked_state(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); (root/'dns-mask').write_text('7\n'); real_open=open
            def lock_open(path,*args,**kwargs):
                return real_open(root/'lock' if path=='/run/lock/mullvad-exit-switch.lock' else path,*args,**kwargs)
            with patch.object(control,'ROOT',root), patch('builtins.open',side_effect=lock_open), patch.object(control,'apply_dns'), patch.object(control,'run',return_value='status: NOERROR, ANSWER: 1'):
                control.set_dns(3)
                self.assertEqual((root/'dns-mask').read_text(),'3\n')

if __name__=='__main__': unittest.main()
