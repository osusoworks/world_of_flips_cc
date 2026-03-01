import os
import subprocess

cmd = [
    r"C:\Program Files\Java\jdk-17.0.14+7\bin\keytool.exe",
    "-genkey", "-v",
    "-keystore", "release-key.jks",
    "-alias", "key0",
    "-keyalg", "RSA",
    "-keysize", "2048",
    "-validity", "10000",
    "-storepass", "password",
    "-keypass", "password",
    "-dname", "CN=User, O=WorldOfFlips, C=JP"
]

try:
    subprocess.check_call(cmd)
    print("Key generated successfully.")
except Exception as e:
    print(f"Error: {e}")
