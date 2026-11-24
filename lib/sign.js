'use strict';
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

function keygen(dir) {
  fs.mkdirSync(dir, { recursive: true });
  const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');
  const priv = path.join(dir, 'openpack.key');
  const pub = path.join(dir, 'openpack.pub');
  fs.writeFileSync(priv, privateKey.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600 });
  fs.writeFileSync(pub, publicKey.export({ type: 'spki', format: 'pem' }));
  return { priv, pub };
}

function sign(bytes, privateKeyPath) {
  const key = crypto.createPrivateKey(fs.readFileSync(privateKeyPath));
  return crypto.sign(null, bytes, key).toString('base64');
}

function verify(bytes, signatureB64, publicKeyPath) {
  const key = crypto.createPublicKey(fs.readFileSync(publicKeyPath));
  return crypto.verify(null, bytes, key, Buffer.from(signatureB64, 'base64'));
}

module.exports = { keygen, sign, verify };
