import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import publish_play_internal as play
import release_version as version


class PlayInternalTest(unittest.TestCase):
    def service(self, code=100):
        service = MagicMock()
        edits = service.edits.return_value
        edits.insert.return_value.execute.return_value = {"id": "edit"}
        edits.bundles.return_value.list.return_value.execute.return_value = {"bundles": []}
        edits.tracks.return_value.list.return_value.execute.return_value = {"tracks": []}
        edits.bundles.return_value.upload.return_value.next_chunk.return_value = (None, {"versionCode": code})
        return service, edits

    def test_completed_upload_only_updates_internal(self):
        service, edits = self.service()
        play.publish(service, Path("test.aab"), 100, "0.1.0", "completed", MagicMock())
        call = edits.tracks.return_value.update.call_args.kwargs
        self.assertEqual("internal", call["track"])
        self.assertEqual(play.PACKAGE, call["packageName"])
        self.assertEqual([{"name": "0.1.0 (100)", "versionCodes": ["100"], "status": "completed"}], call["body"]["releases"])
        edits.validate.return_value.execute.assert_called_once()
        edits.commit.return_value.execute.assert_called_once()
        edits.delete.assert_not_called()

    def test_draft_is_explicit_and_invalid_status_never_creates_edit(self):
        self.assertEqual("draft", play.release_body(100, "0.1.0", "draft")["releases"][0]["status"])
        service, _ = self.service()
        with self.assertRaises(ValueError):
            play.publish(service, Path("test.aab"), 100, "0.1.0", "production", MagicMock())
        service.edits.assert_not_called()

    def test_older_or_duplicate_code_never_uploads(self):
        service, edits = self.service()
        edits.tracks.return_value.list.return_value.execute.return_value = {"tracks": [{"track": "production", "releases": [{"versionCodes": ["100"]}]}]}
        with self.assertRaises(ValueError):
            play.publish(service, Path("test.aab"), 100, "0.1.0", "draft", MagicMock())
        edits.bundles.return_value.upload.assert_not_called()
        edits.commit.assert_not_called()
        edits.delete.assert_called_once()

    def test_uploaded_binary_version_must_match(self):
        service, edits = self.service(code=99)
        with self.assertRaises(ValueError):
            play.publish(service, Path("test.aab"), 100, "0.1.0", "draft", MagicMock())
        edits.tracks.return_value.update.assert_not_called()
        edits.commit.assert_not_called()
        edits.delete.assert_called_once()

    def test_draft_preserves_the_release_current_testers_use(self):
        service, edits = self.service()
        current = {"name": "previous", "status": "completed", "versionCodes": ["99"]}
        edits.tracks.return_value.list.return_value.execute.return_value = {"tracks": [{"track": "internal", "releases": [current]}]}
        play.publish(service, Path("test.aab"), 100, "0.1.0", "draft", MagicMock())
        releases = edits.tracks.return_value.update.call_args.kwargs["body"]["releases"]
        self.assertEqual(current, releases[0])
        self.assertEqual("draft", releases[1]["status"])

    def test_existing_draft_is_not_silently_overwritten(self):
        service, edits = self.service()
        edits.tracks.return_value.list.return_value.execute.return_value = {"tracks": [{"track": "internal", "releases": [{"status": "draft", "versionCodes": ["99"]}]}]}
        with self.assertRaises(ValueError):
            play.publish(service, Path("test.aab"), 100, "0.1.0", "completed", MagicMock())
        edits.bundles.return_value.upload.assert_not_called()
        edits.tracks.return_value.update.assert_not_called()
        edits.delete.assert_called_once()

    def test_commit_failure_is_not_retried_and_cleanup_is_attempted(self):
        service, edits = self.service()
        edits.commit.return_value.execute.side_effect = TimeoutError()
        with self.assertRaises(TimeoutError):
            play.publish(service, Path("test.aab"), 100, "0.1.0", "completed", MagicMock())
        edits.commit.return_value.execute.assert_called_once_with()
        edits.delete.assert_called_once()

    def test_signer_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "release.aab"
            path.write_bytes(b"fixture")
            with patch.object(play.subprocess, "run", side_effect=[SimpleNamespace(stdout="jar verified."), SimpleNamespace(stdout="SHA256: " + "aa" * 32)]):
                with self.assertRaises(ValueError):
                    play.verify_signature(path, "bb" * 32)

    def test_unsigned_bundle_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "release.aab"
            path.write_bytes(b"fixture")
            with patch.object(play.subprocess, "run", return_value=SimpleNamespace(stdout="jar is unsigned.")):
                with self.assertRaises(ValueError):
                    play.verify_signature(path, "aa" * 32)

    def test_canonical_semantic_version_and_independent_build_codes(self):
        self.assertEqual(100, version.version_code(version.EPOCH + 100))
        self.assertEqual(101, version.version_code(version.EPOCH + 101))
        with self.assertRaises(ValueError):
            version.version_code(version.EPOCH)
        with self.assertRaises(ValueError):
            version.version_code(version.EPOCH + version.MAX_CODE + 1)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            path.write_text("major=0\nminor=1\npatch=0\n")
            self.assertEqual("0.1.0", version.version_name(path))
            path.write_text("major=0\nminor=01\npatch=0\n")
            with self.assertRaises(ValueError):
                version.version_name(path)


if __name__ == "__main__":
    unittest.main()
