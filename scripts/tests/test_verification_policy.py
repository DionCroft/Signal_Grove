import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('verification_policy',Path(__file__).resolve().parents[1]/'check_verification_policy.py')
policy=importlib.util.module_from_spec(spec);spec.loader.exec_module(policy)

FIXTURE='<verification-metadata xmlns="https://schema.gradle.org/dependency-verification"><configuration><verify-metadata>true</verify-metadata></configuration><components><component group="fixture" name="library" version="1"><artifact name="library.jar"><sha256 value="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"/></artifact></component></components></verification-metadata>'

class VerificationPolicyTest(unittest.TestCase):
    def test_explicit_metadata_and_binary_hashes_are_required(self):
        self.assertEqual((1,1),policy.check(FIXTURE))
        with self.assertRaises(ValueError):policy.check(FIXTURE.replace('<verify-metadata>true','<verify-metadata>false'))
    def test_blanket_trust_cannot_silently_bypass_artifact_checks(self):
        with self.assertRaises(ValueError):policy.check(FIXTURE.replace('</configuration>','<trusted-artifacts><trust group="fixture"/></trusted-artifacts></configuration>'))
    def test_missing_or_placeholder_checksums_cannot_pass(self):
        for changed in (FIXTURE.replace('sha256','sha1'),FIXTURE.replace('a'*64,'0'*64),FIXTURE.replace('a'*64,'invalid')):
            with self.assertRaises(ValueError):policy.check(changed)
    def test_empty_or_duplicate_artifact_identity_is_rejected(self):
        artifact='<artifact name="library.jar"><sha256 value="'+'a'*64+'"/></artifact>'
        with self.assertRaises(ValueError):policy.check(FIXTURE.replace(artifact,artifact*2))
        with self.assertRaises(ValueError):policy.check(FIXTURE.replace('name="library.jar"','name=""'))
    def test_empty_or_unexpected_manifest_is_rejected(self):
        with self.assertRaises(ValueError):policy.check('<verification-metadata/>')
        with self.assertRaises(ValueError):policy.check(FIXTURE.replace('<components>','<unused>').replace('</components>','</unused>'))

if __name__=='__main__':unittest.main()
