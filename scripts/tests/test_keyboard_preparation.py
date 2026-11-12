"""A keyboard setup receipt cannot waive a suite failure or replay an unknown UI action."""
import copy,importlib.util,json,subprocess,tempfile,unittest,uuid
from pathlib import Path
from unittest.mock import Mock
spec=importlib.util.spec_from_file_location('device',Path(__file__).resolve().parents[1]/'ci_android_device.py');device=importlib.util.module_from_spec(spec);spec.loader.exec_module(device)

class KeyboardPreparationTest(unittest.TestCase):
    nonce='11111111-2222-4333-8444-555555555555'
    def receipt(self,nonce=None):
        ready=dict(top=1053,bottom=1794,keyTop=1053,keys=37,hints=0,closes=0,exactHintText=False,ready=True)
        return dict(schema=1,nonce=nonce or self.nonce,api=26,hardware='ranchu',mode='prepare',imeVersion='6.2.34.154174348-preload-x86_64',selectedImeBefore='com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME',selectedImeAfter='com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME',before={**ready,'top':827,'hints':1,'closes':1,'exactHintText':True,'ready':False},afterDismiss={**ready,'top':827,'ready':False},reopened=copy.deepcopy(ready),hintSeen=True,dismissAttempts=1,actions=['open_settings_search','dismiss_hint','hide_keyboard','reopen_editor','return_home'],state='READY_FRESH_KEYBOARD',cleanupKeyboardHidden=True,actionAcknowledgements={name:True for name in ['open_settings_search','dismiss_hint','hide_keyboard','reopen_editor','return_home']})
    def output(self,report=None):return 'INSTRUMENTATION_RESULT: keyboardFixtureReport='+json.dumps(report or self.receipt())+'\nINSTRUMENTATION_CODE: -1\n'
    def test_one_hint_removal_and_fully_unobscured_reopened_keyboard_are_accepted(self):
        value=self.receipt();self.assertEqual(value,device.keyboard_preparation_record(self.output(value),0,self.nonce))
    def test_failed_cancelled_partial_duplicate_and_oversized_command_results_are_refused(self):
        for code,text in [(1,self.output()),(0,''),(0,self.output().replace('CODE: -1','CODE: 0')),(0,self.output()+self.output()),(0,'x'*32769),(0,self.output().replace('"schema": 1','"schema": 1, "schema": 1'))]:
            with self.subTest(code=code,text=text[:40]),self.assertRaises(ValueError):device.keyboard_preparation_record(text,code,self.nonce)
    def test_physical_unknown_version_stale_nonce_and_repeated_dismissal_are_refused(self):
        for key,value in [('nonce',str(uuid.uuid4())),('hardware','qcom'),('api',37),('imeVersion','unknown'),('mode','verify'),('hintSeen',False),('cleanupKeyboardHidden',False),('dismissAttempts',2),('dismissAttempts',True),('selectedImeAfter','foreign/Keyboard'),('state','FAILED')]:
            changed=self.receipt();changed[key]=value
            with self.subTest(key=key,value=value),self.assertRaises(ValueError):device.keyboard_preparation_record(self.output(changed),0,self.nonce)
    def test_present_overlay_hidden_keyboard_or_coerced_geometry_cannot_pass(self):
        for phase,key,value in [('before','hints',0),('before','exactHintText',False),('afterDismiss','ready',True),('afterDismiss','hints',1),('reopened','top',827),('reopened','keys',0),('afterDismiss','keys',True),('afterDismiss','bottom','1794'),('afterDismiss','ready',1)]:
            changed=self.receipt();changed[phase][key]=value
            with self.subTest(phase=phase,key=key),self.assertRaises(ValueError):device.keyboard_preparation_record(self.output(changed),0,self.nonce)
    def test_voice_contacts_keyboard_replacement_or_duplicate_steps_are_not_setup(self):
        for actions in [['dismiss_hint','dismiss_hint','return_home'],['grant_contacts','return_home'],['replace_ime','return_home'],['voice_input']]:
            changed=self.receipt();changed['actions']=actions
            with self.subTest(actions=actions),self.assertRaises(ValueError):device.keyboard_preparation_record(self.output(changed),0,self.nonce)
    def test_missing_false_coerced_or_foreign_acknowledgements_are_refused(self):
        for ack in [{},{'dismiss_hint':True},{**self.receipt()['actionAcknowledgements'],'dismiss_hint':False},{**self.receipt()['actionAcknowledgements'],'dismiss_hint':1},{**self.receipt()['actionAcknowledgements'],'voice_input':True}]:
            changed=self.receipt();changed['actionAcknowledgements']=ack
            with self.subTest(ack=ack),self.assertRaises(ValueError):device.keyboard_preparation_record(self.output(changed),0,self.nonce)
    def test_command_runs_once_and_its_receipt_cannot_be_reused(self):
        with tempfile.TemporaryDirectory() as path:
            def result(*args,**kwargs):return Mock(stdout=self.output(self.receipt(args[args.index('ownedKeyboardFixture')+1])),returncode=0)
            native=Mock(side_effect=result);report=device.prepare_owned_keyboard(native,Path(path),26);self.assertEqual(1,report['dismissAttempts']);native.assert_called_once();self.assertEqual(45,native.call_args.kwargs['timeout']);self.assertFalse(native.call_args.kwargs['check'])
            self.assertEqual('OWNED_DEFAULT_KEYBOARD_PREPARED',json.loads((Path(path)/'keyboard-preparation.json').read_text())['state'])
            with self.assertRaises(ValueError):device.prepare_owned_keyboard(native,Path(path),26)
            native.assert_called_once()
    def test_timeout_retains_partial_output_and_never_invokes_the_native_action_again(self):
        with tempfile.TemporaryDirectory() as path:
            native=Mock(side_effect=subprocess.TimeoutExpired('owned-setup',45,output=b'partial fixture\n'))
            with self.assertRaises(subprocess.TimeoutExpired):device.prepare_owned_keyboard(native,Path(path),26)
            self.assertEqual('partial fixture\n',(Path(path)/'keyboard-preparation.log').read_text());self.assertEqual('KEYBOARD_PREPARATION_FAILED_OR_UNKNOWN',json.loads((Path(path)/'keyboard-preparation.json').read_text())['state'])
            with self.assertRaises(ValueError):device.prepare_owned_keyboard(native,Path(path),26)
            native.assert_called_once()
    def test_other_api_is_unchanged_and_standard_suite_does_not_gain_exclusions(self):
        with tempfile.TemporaryDirectory() as path:
            native=Mock();self.assertIsNone(device.prepare_owned_keyboard(native,Path(path),37));native.assert_not_called();self.assertFalse(list(Path(path).iterdir()))
        self.assertEqual(5,len(device.EXTERNAL_MODEL_GATES));self.assertEqual(11,sum(len(x['methods']) for x in device.EXTERNAL_MODEL_GATES.values()))
    def ready_receipt(self,nonce=None):
        value=self.receipt(nonce);value['before']=copy.deepcopy(value['reopened']);value['afterDismiss']=copy.deepcopy(value['reopened'])
        value.update(hintSeen=False,dismissAttempts=0,state='READY_EXISTING_KEYBOARD')
        value['actions'].remove('dismiss_hint');value['actionAcknowledgements'].pop('dismiss_hint')
        return value
    def test_already_unobscured_keyboard_without_a_tip_is_accepted_after_reopen(self):
        value=self.ready_receipt();self.assertEqual(value,device.keyboard_preparation_record(self.output(value),0,self.nonce))
    def test_already_ready_focused_editor_needs_no_search_open_or_dismissal(self):
        value=self.ready_receipt();value['actions'].remove('open_settings_search');value['actionAcknowledgements'].pop('open_settings_search')
        self.assertEqual(value,device.keyboard_preparation_record(self.output(value),0,self.nonce))
    def test_no_tip_branch_refuses_gaps_overlays_coercions_or_unreviewed_actions(self):
        changes=[('state','READY_FRESH_KEYBOARD'),('hintSeen',0),('dismissAttempts',False),('dismissAttempts',1),('actions',['dismiss_hint','hide_keyboard','reopen_editor','return_home'])]
        for key,value in changes:
            changed=self.ready_receipt();changed[key]=value
            with self.subTest(key=key,value=value),self.assertRaises(ValueError):device.keyboard_preparation_record(self.output(changed),0,self.nonce)
        for phase,key,value in [('before','top',827),('before','ready',False),('before','hints',1),('before','closes',1),('before','exactHintText',True),('before','ready',1),('afterDismiss','top',827),('afterDismiss','ready',False),('reopened','top',827),('reopened','hints',1)]:
            changed=self.ready_receipt();changed[phase][key]=value
            with self.subTest(phase=phase,key=key,value=value),self.assertRaises(ValueError):device.keyboard_preparation_record(self.output(changed),0,self.nonce)
        for phase in ['before','afterDismiss','reopened']:
            changed=self.ready_receipt();changed[phase].update(top=827,ready=False)
            with self.subTest(coherentGap=phase),self.assertRaises(ValueError):device.keyboard_preparation_record(self.output(changed),0,self.nonce)
    def test_failed_native_setup_cannot_be_relabelled_from_a_ready_latest_sample(self):
        value=self.ready_receipt();failed={k:value[k] for k in ['schema','nonce','api','hardware','mode','imeVersion','selectedImeBefore']}
        failed.update(state='FAILED',errorType='IllegalStateException',failureStage='reviewed first-use hint or prepared keyboard',latestKeyboard=value['before'])
        with self.assertRaises(ValueError):device.keyboard_preparation_record(self.output(failed),0,self.nonce)
    def test_already_ready_native_setup_is_once_only_and_its_receipt_cannot_be_reused(self):
        with tempfile.TemporaryDirectory() as path:
            def result(*args,**kwargs):return Mock(stdout=self.output(self.ready_receipt(args[args.index('ownedKeyboardFixture')+1])),returncode=0)
            native=Mock(side_effect=result);report=device.prepare_owned_keyboard(native,Path(path),26);self.assertEqual(0,report['dismissAttempts']);native.assert_called_once()
            with self.assertRaises(ValueError):device.prepare_owned_keyboard(native,Path(path),26)
            native.assert_called_once()
if __name__=='__main__':unittest.main()
