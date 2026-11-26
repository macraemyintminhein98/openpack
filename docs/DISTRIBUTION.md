# Distribution plan (draft)

How packs get from the maintainer to people, and how problems come back.

## Roles

| Role | Who | What they do |
|---|---|---|
| Maintainer | Holds the signing key | Chooses content, builds and signs each pack, publishes the checksum |
| Hub | A shop, school, monastery, clinic or library with a laptop or phone | Gets each new pack, checks it, copies it to people's devices |
| Carrier | Anyone travelling between places | Takes a copy on a phone, SD card or USB stick to the next hub |
| Reader | Everyone else | Opens the pack, reads, shares it onward |

Nobody needs an account, and no list of readers is kept anywhere.

## The cycle (weekly or monthly)

1. **Build.** Maintainer runs `scripts/weekly-release.sh packs/<name>`. This builds, signs,
   verifies and zips the pack, and writes the change note and checksum.
2. **Publish.** Maintainer sends the zip and its checksum to the hubs by whatever works:
   a download link when there is internet, otherwise a carrier.
3. **Check.** Each hub starts the pack with the START file. The program refuses to run if any
   file was changed or the signature is not the maintainer's. A hub never hands on a pack
   that fails this check.
4. **Copy.** Hubs copy the whole folder to people's phones (USB cable, SD card, or the
   built-in Wi-Fi sharing with the QR code).
5. **Onward.** Readers can copy the folder to others exactly as they received it.

## Rules that keep people safe

- **One key, kept offline.** The signing key lives on one machine and one backup. If it is
  lost or stolen, publish a new public key to every hub by hand and tell them to delete the
  old one.
- **Hubs get the public key in person** the first time, not from a download.
- **Apolitical, licensed content only** (see `CONTENT-POLICY.md`). A carrier must never be put
  at risk by what is in the pack.
- **No tracking.** Do not add analytics, sign-ups or reader lists, even "to measure impact".
  Count packs handed out at hubs instead.
- **Charging.** The content is free. If a shop charges for the copying service or the storage
  card, that is their business, but get legal advice first (non-commercial licences).

## Hub notice board, and why there is no chat

- **Notice board:** safe to use. Only the hub owner writes it (`notices.txt`); visitors read.
- **No chat or messaging.** OpenPack is a library. A messaging feature would make every hub
  owner answerable for what strangers write on their Wi-Fi, and would change what a carrier
  is carrying. If that is ever reconsidered, get the legal questions answered first and have
  the encryption reviewed by someone independent.

## When something is wrong in a pack

1. Anyone reports it to a hub or the maintainer (see contact in the README).
2. Maintainer removes or fixes the item and releases a new pack. The change note lists it.
3. Hubs replace the old pack on their own devices and tell regular visitors.
4. Old copies cannot be recalled. This is why review happens **before** release.

## Sizes

| Pack | Size | Fits on |
|---|---|---|
| Lessons and portal only | under 1 MB (plus about 16 MB of server programs) | Anything |
| With Burmese Wikipedia (no pictures) | a few hundred MB, check the real file | Any phone with free space |
| With video | several GB | SD cards, USB sticks |

Keep a small "lite" pack alongside any big one, for old phones.

## Measuring without tracking

- Hubs keep a paper tally: copies made per week.
- Every few months, ask a handful of readers the five questions in `PILOT.md`.
