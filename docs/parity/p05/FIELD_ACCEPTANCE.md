# P05 field acceptance

On stock SageTV with an unchanged STV and real recordings:
1. Verify STV Off -> CC1 -> CC2 -> Off with a CEA recording.
2. Verify the same cycle with an observed DVB Teletext recording; confirm only one caption surface is visible.
3. Verify a peer that sends `VIDEO_CC_STATE` and, if available, a stock peer/STV path that omits it.
4. Seek forward/backward and FLUSH; confirm old rows do not remain or splice into new captions.
5. Switch STV -> local CC1 -> DVB -> Off and confirm no ghost renderer survives.
6. Verify CEA-608 CC1-CC4 and at least one CEA-708 service where source material exists.
7. Capture diagnostics including callback event/byte counts and the effective-source label.

Only after these pass should the P05 exit gate be marked complete.
