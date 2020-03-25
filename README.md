About NFCCopyPaste

Android 10 removed a feature lots of apps used to exchange little bits of information via NFC. For example, 
my wife would have a shopping list in ColorNote and, when it was decided I'd do the shopping, we'd touch phones and
magically I'd have the list. NFCCopyPaste works around the problem by passing, via NFC, whatever text is in the 
system clipboard to a copy of itself on another phone. It's not as simple as when apps like ColorNote could do 
it themselves, but it's easier than e.g. copy-and-pasting into email, and it works without an internet connection
(e.g. on an airplane.)

Use is simple (once the app's on two phones that have NFC enabled)
* In any app, select some text and "Copy" it
* Open NFCCopyPaste, hit the "Start Sending" button, and touch the two phones together (before the 10-second timer expires)
* On the second phone, "Paste" the received text into any app

NFCCopyPaste is something I've tossed together for my own use. I'm making it available in the hopes others are 
also missing the feature Android 10 removed. It's a very simple app: doesn't even have a custom icon, though I'd certainly
take one as a push request. :-)

Note that the copy of NFCCopyPaste doing the receiving does not have to be running, but it must have been 
launched at least once since being installed. That's just the way background-launch on Android works.
