# Real-device test checklist

Nobody has run OpenPack on a real phone or Windows PC yet. This takes about 20 minutes per
device. Write PASS / FAIL next to each line and send photos of anything odd.

Device: ____________  Android/Windows version: ______  System font: Zawgyi / Unicode / don't know

## A. Open from the folder (no server)
1. Copy the `OpenPack-...` folder to the phone or PC.
2. Open `pack/index.html` with Chrome.
   - [ ] Page opens. Burmese text is readable (not boxes, not scrambled).
   - [ ] Tapping a tile shows a list. The coloured button opens the file.
   - [ ] Phone Back button returns to the home page.
   - [ ] Settings: largest text size works; dark colour works; still there after closing Chrome.
   - [ ] Search: type a Burmese word with your normal keyboard. Results appear.

## B. Android as the sharing phone (Termux)
1. Install Termux from F-Droid. Run `termux-setup-storage`.
2. Follow section B of `START-HERE.txt` to run `START-ANDROID.sh`.
   - [ ] It prints a line starting `OK:` and an address.
3. Turn on the phone's hotspot. Connect a second phone to it.
   - [ ] Second phone opens the address in Chrome.
   - [ ] Share page shows a QR code; scanning it on a third phone opens the library.
   - [ ] A file opens/downloads on the second phone.

## C. Windows laptop
1. Double-click `START-WINDOWS.bat`.
   - [ ] No antivirus block. (If SmartScreen appears: note the exact message.)
   - [ ] Window shows a line starting `OK:` and an address.
   - [ ] Windows Firewall question appears: choose Allow on private networks.
   - [ ] A phone on the same Wi-Fi can open the address.
   - [ ] `START-HERE.txt` opens in Notepad with readable Burmese.

## D. Zawgyi phone (most important)
On a phone whose system font is Zawgyi:
   - [ ] Portal text is correct Unicode Burmese.
   - [ ] Typing in the search box with a Zawgyi keyboard finds items.
   - [ ] Note how the typed text looks inside the search box while typing.

## E. Tamper check
Edit any file in `pack/content/` and start again.
   - [ ] The server refuses to start and says which file changed.
