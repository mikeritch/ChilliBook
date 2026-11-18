"""Create the owner's private signing identity and recovery copy once on this Mac."""
import os
from pathlib import Path
import secrets
import shutil
import subprocess

os.umask(0o077)
primary = Path.home() / "Library/Application Support/ChilliBook/private"
recovery = Path.home() / ".local/share/chillibook-signing-recovery"
if primary.exists() or recovery.exists():
    raise SystemExit("Signing directory already exists. Use the existing key or recover it before building.")
primary.mkdir(mode=0o700, parents=True)
secret = secrets.token_urlsafe(48)
tool = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool")
subprocess.run([str(tool), "-genkeypair", "-keystore", str(primary / "diary.p12"),
                "-storetype", "PKCS12", "-alias", "diary", "-storepass:env", "DIARY_KEY_PASS",
                "-keypass:env", "DIARY_KEY_PASS", "-keyalg", "RSA", "-keysize", "3072",
                "-validity", "20000", "-dname", "CN=ChilliBook private release"],
               env={**os.environ, "DIARY_KEY_PASS": secret}, check=True)
(primary / "signing.properties").write_text(
    "storeFile=diary.p12\nkeyAlias=diary\nstorePassword=" + secret + "\n"
)
shutil.copytree(primary, recovery)
